package com.dodosupermarket.show.core;

import com.dodosupermarket.support.BusinessSqlFixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dodosupermarket.show.dao.*;
import com.dodosupermarket.show.service.logic.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.io.InputStream;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ShowActivityServiceTest {
    private JdbcTemplate db;
    private ShowActivityService service;
    private SqlSessionTemplate sessions;
    private DataSourceTransactionManager manager;
    private final List<ShowReviewEvent> reviewEvents = new ArrayList<>();
    private final AtomicBoolean failCoupon = new AtomicBoolean(false);

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:show_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE dodo_users(user_id BIGINT PRIMARY KEY,nick_name VARCHAR(100),user_name VARCHAR(100))");
        db.execute(
                "CREATE TABLE dodo_admin_user(user_id BIGINT PRIMARY KEY,name VARCHAR(100),nick_name VARCHAR(100),user_name VARCHAR(100))");
        db.update("INSERT INTO dodo_admin_user VALUES(99,'测试审核员','员工昵称','admin')");
        db.update("INSERT INTO dodo_users VALUES(10,'当前用户昵称','customer'),(99,'同编号客户','other')");
        String schema = BusinessSqlFixture.createTables("dodo_show_")
                .replaceAll("(?m)^--.*$", "").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : schema.split(";")) if (!sql.trim().isEmpty()) db.execute(sql);
        db.execute(
                "CREATE TABLE dodo_order_info(order_id BIGINT PRIMARY KEY,order_sn VARCHAR(100),user_id BIGINT,store_id BIGINT,status INT,order_status INT,pay_status INT,order_amount DECIMAL(10,2),money_paid DECIMAL(10,2),create_time TIMESTAMP)");
        db.execute(
                "CREATE TABLE dodo_order_goods(rec_id BIGINT PRIMARY KEY,order_id BIGINT,status INT,goods_name VARCHAR(100),goods_img VARCHAR(200),goods_number INT,goods_price DECIMAL(10,2),volume_msg VARCHAR(100))");
        db.execute("CREATE TABLE dodo_coupon(id BIGINT AUTO_INCREMENT PRIMARY KEY,status INT,price DECIMAL(10,2))");
        db.execute(
                "CREATE TABLE dodo_dict(id BIGINT,dict_type VARCHAR(100),dict_value VARCHAR(100),dict_name VARCHAR(100),status INT,store_id BIGINT)");
        for (int i = 1; i <= 4; i++)
            db.update("INSERT INTO dodo_order_info VALUES(?,?,?,?,1,?,2,50,50,CURRENT_TIMESTAMP)", i, "ORDER" + i, i == 4 ? 20 : 10, 1,
                    i == 3 ? 2 : 3);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("issuer", (ShowRewardIssuer) (a, s) -> {
            db.update("INSERT INTO dodo_coupon(status,price) VALUES(1,?)", a.rewardPrice);
            if (failCoupon.get()) throw new IllegalStateException("模拟发券失败");
            return db.queryForObject("SELECT MAX(id) FROM dodo_coupon", Long.class);
        });
        manager = new DataSourceTransactionManager(source);
        Configuration configuration = new Configuration(new Environment("show", new SpringManagedTransactionFactory(), source));
        for (String name : List.of("ShowActivityRecord", "ShowSubmissionRecord", "ShowActivityLog", "ShowOrder")) {
            String mapper = "com/dodosupermarket/show/dao/" + name + "DAO.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(mapper)) {
                new XMLMapperBuilder(Objects.requireNonNull(input), configuration, mapper, configuration.getSqlFragments()).parse();
            }
        }
        sessions = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        service = newService(beans);
    }

    private ShowActivityService newService(DefaultListableBeanFactory beans) {
        return new ShowActivityService(new ShowActivityRecordServiceImpl(sessions.getMapper(ShowActivityRecordDAO.class)),
                new ShowSubmissionRecordServiceImpl(sessions.getMapper(ShowSubmissionRecordDAO.class)),
                new ShowActivityLogServiceImpl(sessions.getMapper(ShowActivityLogDAO.class)), sessions.getMapper(ShowOrderDAO.class),
                manager, new ObjectMapper(), beans.getBeanProvider(ShowRewardIssuer.class), beans.getBeanProvider(ShowMediaStore.class),
                event -> reviewEvents.add((ShowReviewEvent) event));
    }

    private ShowActivity activity(String policy) {
        ShowActivity a = new ShowActivity();
        a.name = "日常晒单";
        a.longTerm = true;
        a.coverImage = "show/cover.jpg";
        a.rules = new ArrayList<>(List.of("上传社媒截图"));
        a.rewardPrice = new BigDecimal("5.00");
        a.participation = policy;
        return service.save(a, 1, 99);
    }

    private Map<String, Object> submit(ShowActivity a, String order) {
        return service.submit(null, a.id, order, List.of("show/screenshot.jpg"), UUID.randomUUID().toString(), 10, "测试用户", "", 1);
    }

    private long id(Map<String, Object> s) {
        return ShowActivityService.number(s.get("id"));
    }

    private int version(Map<String, Object> s) {
        return ((Number) s.get("version")).intValue();
    }

    @Test
    void defaultStartAndScheduledActivities() {
        long before = System.currentTimeMillis();
        ShowActivity a = activity("ONCE");
        assertTrue(a.startTime >= before);
        assertTrue(a.active(System.currentTimeMillis()));
        a.startTime = System.currentTimeMillis() + 60000;
        service.save(a, 1, 99);
        assertEquals(0L, service.catalog(1, true, 1, 20).get("total"));
        assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
    }

    @Test
    void onlyCompletedOwnedOrders() {
        ShowActivity a = activity("UNLIMITED");
        assertThrows(ShowException.class, () -> submit(a, "ORDER3"));
        assertThrows(ShowException.class, () -> submit(a, "ORDER4"));
        assertThrows(ShowException.class, () -> service.orders(a.id, 10, 2, 1, 20));
        assertEquals(2L, service.orders(a.id, 10, 1, 1, 20).get("total"));
    }

    @Test
    void pendingConsumesOnceQuota() {
        ShowActivity a = activity("ONCE");
        submit(a, "ORDER1");
        assertThrows(ShowException.class, () -> submit(a, "ORDER2"));
    }

    @Test
    void optionalOrderAcceptsMissingOrderAndStillConsumesOnceQuota() {
        ShowActivity a = activity("ONCE");
        a.orderRequired = false;
        service.save(a, 1, 99);
        Map<String, Object> first = submit(a, null);
        assertEquals("", first.get("orderSn"));
        assertTrue(((Map<?, ?>) first.get("order")).isEmpty());
        assertThrows(ShowException.class, () -> submit(a, ""));
        assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
        Map<String, Object> approved = service.review(id(first), version(first), true, "", 1, 99);
        assertEquals("APPROVED", approved.get("auditStatus"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_coupon", Integer.class));
        assertEquals(1, reviewEvents.size());
    }

    @Test
    void unlimitedOptionalOrderAllowsRepeatedUnlinkedSubmissions() {
        ShowActivity a = activity("UNLIMITED");
        a.orderRequired = false;
        service.save(a, 1, 99);
        Map<String, Object> first = submit(a, "  "), second = submit(a, null);
        assertNotEquals(id(first), id(second));
        assertEquals("", first.get("orderSn"));
        assertEquals(2L, service.summary(10, 1).get("submissionCount"));
    }

    @Test
    void requiredAndPerOrderPoliciesCannotOmitOrder() {
        ShowActivity required = activity("ONCE");
        assertThrows(ShowException.class, () -> submit(required, null));
        ShowActivity perOrder = activity("PER_ORDER");
        perOrder.orderRequired = false;
        assertTrue(service.save(perOrder, 1, 99).orderRequired);
        assertThrows(ShowException.class, () -> submit(perOrder, ""));
        // 即使历史配置被写成矛盾状态，提交校验仍要求订单。
        String payload = db.queryForObject("SELECT payload FROM dodo_show_activity WHERE id=?", String.class, perOrder.id);
        db.update("UPDATE dodo_show_activity SET payload=? WHERE id=?",
                payload.replace("\"orderRequired\":true", "\"orderRequired\":false"),
                perOrder.id);
        assertThrows(ShowException.class, () -> submit(perOrder, null));
    }

    @Test
    void oldPayloadWithoutOrderSettingKeepsRequiredBehavior() throws Exception {
        ShowActivity a = activity("ONCE");
        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> payload = mapper.readValue(db.queryForObject("SELECT payload FROM dodo_show_activity WHERE id=?", String.class, a.id),
                Map.class);
        payload.remove("orderRequired");
        db.update("UPDATE dodo_show_activity SET payload=? WHERE id=?", mapper.writeValueAsString(payload), a.id);
        assertTrue(service.activity(a.id, 1).orderRequired);
        assertThrows(ShowException.class, () -> submit(a, null));
    }

    @Test
    void optionalOrderStillValidatesAnyProvidedOrder() {
        ShowActivity a = activity("UNLIMITED");
        a.orderRequired = false;
        service.save(a, 1, 99);
        assertThrows(ShowException.class, () -> submit(a, "ORDER3"));
        assertThrows(ShowException.class, () -> submit(a, "ORDER4"));
        assertThrows(ShowException.class, () -> submit(a, "UNKNOWN"));
        db.update("UPDATE dodo_order_info SET pay_status=6 WHERE order_id=2");
        assertThrows(ShowException.class, () -> submit(a, "ORDER2"));
        Map<String, Object> first = submit(a, " ORDER1 ");
        assertEquals("ORDER1", first.get("orderSn"));
        db.update("UPDATE dodo_order_info SET pay_status=5 WHERE order_id=1");
        assertThrows(ShowException.class, () -> service.review(id(first), version(first), true, "", 1, 99));
    }

    @Test
    void existingUnlinkedRecordKeepsEligibilityAfterOrderRequirementChanges() {
        ShowActivity a = activity("ONCE");
        a.orderRequired = false;
        service.save(a, 1, 99);
        Map<String, Object> first = submit(a, null);
        service.review(id(first), version(first), false, "补充截图", 1, 99);
        assertThrows(ShowException.class, () -> submit(a, ""));
        a.orderRequired = true;
        a.rewardPrice = new BigDecimal("8.00");
        service.save(a, 1, 99);
        assertThrows(ShowException.class, () -> service.submit(id(first), a.id, "ORDER1", List.of("show/new.jpg"),
                UUID.randomUUID().toString(), 10, "用户", "", 1));
        Map<String, Object> next = service.submit(id(first), a.id, "", List.of("show/new.jpg"), UUID.randomUUID().toString(),
                10, "用户", "", 1);
        assertEquals(id(first), id(next));
        assertEquals(2, next.get("attempts"));
        Map<String, Object> approved = service.review(id(next), version(next), true, "", 1, 99);
        assertEquals(new BigDecimal("8.00"), approved.get("rewardPrice"));
        assertEquals("", approved.get("orderSn"));
    }

    @Test
    void optionalOrderRetryNormalizesMissingValueAndHonorsDeadline() {
        ShowActivity a = activity("UNLIMITED");
        a.orderRequired = false;
        service.save(a, 1, 99);
        String key = UUID.randomUUID().toString();
        Map<String, Object> first = service.submit(null, a.id, null, List.of("show/a.jpg"), key, 10, "用户", "", 1);
        Map<String, Object> rejected = submit(a, "");
        service.review(id(rejected), version(rejected), false, "补充截图", 1, 99);
        service.end(a.id, a.version, 1, 99);
        assertEquals(id(first), id(service.submit(null, a.id, "", List.of("show/a.jpg"), key, 10, "用户", "", 1)));
        assertThrows(ShowException.class, () -> submit(a, null));
        assertThrows(ShowException.class, () -> service.submit(id(rejected), a.id, null, List.of("show/new.jpg"),
                UUID.randomUUID().toString(), 10, "用户", "", 1));
        assertDoesNotThrow(() -> service.review(id(first), version(first), true, "", 1, 99));
    }

    @Test
    void pendingConsumesOrderQuotaButOtherOrderWorks() {
        ShowActivity a = activity("PER_ORDER");
        submit(a, "ORDER1");
        assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
        assertDoesNotThrow(() -> submit(a, "ORDER2"));
    }

    @Test
    void unlimitedAllowsAnotherOrderButNotTheSameOrder() {
        ShowActivity a = activity("UNLIMITED");
        submit(a, "ORDER1");
        assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
        assertDoesNotThrow(() -> submit(a, "ORDER2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ONCE", "PER_ORDER", "UNLIMITED"})
    void participatedOrderIsDisabledInEveryAuditStateAndResubmitsOriginalRecord(String policy) {
        ShowActivity a = activity(policy);
        Map<String, Object> record = submit(a, "ORDER1");
        for (String status : List.of("PENDING", "REJECTED", "APPROVED")) {
            if ("REJECTED".equals(status)) {
                service.review(id(record), version(record), false, "补充截图", 1, 99);
            } else if ("APPROVED".equals(status)) {
                record = service.submit(id(record), a.id, "ORDER1", List.of("show/new.jpg"), UUID.randomUUID().toString(),
                        10, "测试用户", "", 1);
                service.review(id(record), version(record), true, "", 1, 99);
            }
            Map<String, Object> selected = service.selectedOrder(a.id, "ORDER1", 10, 1);
            assertEquals(true, selected.get("disabled"));
            assertTrue(selected.get("disabledReason").toString().contains("该订单已参与过本活动"));
            assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
            List<?> results = (List<?>) service.orders(a.id, 10, 1, 1, 20).get("results");
            for (Object result : results) {
                Map<?, ?> row = (Map<?, ?>) result;
                boolean blocked = "ORDER1".equals(row.get("orderSn")) || "ONCE".equals(policy);
                assertEquals(blocked, row.get("disabled"));
                if (blocked && "ORDER2".equals(row.get("orderSn"))) {
                    assertTrue(row.get("disabledReason").toString().contains("参与次数上限"));
                }
            }
        }
        assertEquals(1L, service.summary(10, 1).get("submissionCount"));
    }

    @Test
    void sameOrderCanParticipateInDifferentActivitiesAndKeepReceiptRetry() {
        ShowActivity first = activity("UNLIMITED"), second = activity("UNLIMITED");
        String key = UUID.randomUUID().toString();
        Map<String, Object> receipt = service.submit(null, first.id, "ORDER1", List.of("show/a.jpg"), key, 10, "用户", "", 1);
        assertEquals(id(receipt), id(service.submit(null, first.id, "ORDER1", List.of("show/a.jpg"), key, 10, "用户", "", 1)));
        assertEquals(false, service.selectedOrder(second.id, "ORDER1", 10, 1).get("disabled"));
        assertDoesNotThrow(() -> submit(second, "ORDER1"));
    }

    @Test
    void sameRequestIsIdempotent() {
        ShowActivity a = activity("ONCE");
        String key = UUID.randomUUID().toString();
        Map<String, Object> first = service.submit(null, a.id, "ORDER1", List.of("show/1.png"), key, 10, "用户", "", 1);
        Map<String, Object> second = service.submit(null, a.id, "ORDER1", List.of("show/1.png"), key, 10, "用户", "", 1);
        assertEquals(id(first), id(second));
    }

    @Test
    void rejectedResubmitsSameRecordWithNoNewQuota() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        service.review(id(s), version(s), false, "请显示点赞", 1, 99);
        Map<String, Object> next = service.submit(id(s), a.id, "ORDER1", List.of("show/new.png"), UUID.randomUUID().toString(), 10, "用户",
                "", 1);
        assertEquals(id(s), id(next));
        assertEquals(2, ((Number) next.get("attempts")).intValue());
        assertEquals("PENDING", next.get("auditStatus"));
        assertEquals("", next.get("note"));
    }

    @Test
    void resubmitCannotChangeOrderOrOwner() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        service.review(id(s), version(s), false, "请修改", 1, 99);
        assertThrows(ShowException.class,
                () -> service.submit(id(s), a.id, "ORDER2", List.of("show/a.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1));
        assertThrows(ShowException.class, () -> service.detail(id(s), 20L, 1));
    }

    @Test
    void endedBlocksNewAndResubmissionButAllowsReview() {
        ShowActivity a = activity("UNLIMITED");
        Map<String, Object> s = submit(a, "ORDER1");
        Map<String, Object> r = submit(a, "ORDER2");
        service.review(id(r), version(r), false, "修改", 1, 99);
        service.end(a.id, a.version, 1, 99);
        assertThrows(ShowException.class, () -> submit(a, "ORDER1"));
        assertThrows(ShowException.class,
                () -> service.submit(id(r), a.id, "ORDER2", List.of("show/a.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1));
        assertDoesNotThrow(() -> service.review(id(s), version(s), true, "", 1, 99));
    }

    @Test
    void approvalUsesLatestRewardAndIsIdempotent() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        a.rewardPrice = new BigDecimal("12.00");
        service.save(a, 1, 99);
        Map<String, Object> approved = service.review(id(s), version(s), true, "", 1, 99);
        assertEquals(new BigDecimal("12.00"), approved.get("rewardPrice"));
        service.review(id(s), version(s), true, "", 1, 99);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_coupon", Integer.class));
        assertEquals(1, reviewEvents.size());
    }

    @Test
    void failedCouponRollsBackAndCanRetry() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        failCoupon.set(true);
        assertThrows(IllegalStateException.class, () -> service.review(id(s), version(s), true, "", 1, 99));
        assertEquals("PENDING", service.detail(id(s), 10L, 1).get("auditStatus"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_coupon", Integer.class));
        assertTrue(reviewEvents.isEmpty());
        failCoupon.set(false);
        assertDoesNotThrow(() -> service.review(id(s), version(s), true, "", 1, 99));
    }

    @Test
    void rejectRequiresReasonAndStaleReviewCannotApproveResubmission() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        assertThrows(ShowException.class, () -> service.review(id(s), version(s), false, " ", 1, 99));
        service.review(id(s), version(s), false, "修改", 1, 99);
        service.submit(id(s), a.id, "ORDER1", List.of("show/b.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1);
        assertThrows(ShowException.class, () -> service.review(id(s), version(s), true, "", 1, 99));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ONCE", "PER_ORDER", "UNLIMITED"})
    void concurrentSubmissionsCannotExceedQuota(String policy) throws Exception {
        ShowActivity a = activity(policy);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<Boolean> work = () -> {
                go.await();
                try {
                    submit(a, "ORDER1");
                    return true;
                } catch (ShowException ex) {
                    return false;
                }
            };
            Future<Boolean> x = pool.submit(work), y = pool.submit(work);
            go.countDown();
            assertNotEquals(x.get(10, TimeUnit.SECONDS), y.get(10, TimeUnit.SECONDS));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_show_submission", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentApprovalsCreateOneCoupon() throws Exception {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Map<String, Object>> work = () -> service.review(id(s), version(s), true, "", 1, 99);
            Future<Map<String, Object>> x = pool.submit(work), y = pool.submit(work);
            assertEquals(x.get(10, TimeUnit.SECONDS).get("couponId"), y.get(10, TimeUnit.SECONDS).get("couponId"));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_coupon", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void validationAndImageLimits() {
        ShowActivity a = activity("ONCE");
        a.rules = List.of("");
        assertThrows(ShowException.class, () -> service.save(a, 1, 99));
        assertThrows(ShowException.class,
                () -> service.submit(null, a.id, "ORDER1", List.of("blob:bad.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1));
        assertThrows(ShowException.class,
                () -> service.submit(null, a.id, "ORDER1", List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg"), UUID.randomUUID().toString(), 10,
                        "用户", "", 1));
    }

    @Test
    void receiptRetryAfterDeadlineDoesNotCreateAnotherSubmission() {
        ShowActivity a = activity("ONCE");
        String key = UUID.randomUUID().toString();
        Map<String, Object> s = service.submit(null, a.id, "ORDER1", List.of("show/a.jpg"), key, 10, "用户", "", 1);
        service.end(a.id, a.version, 1, 99);
        assertEquals(id(s), id(service.submit(null, a.id, "ORDER1", List.of("show/a.jpg"), key, 10, "用户", "", 1)));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_show_submission", Integer.class));
    }

    @Test
    void refundedOrderCannotReceiveReward() {
        ShowActivity a = activity("ONCE");
        Map<String, Object> s = submit(a, "ORDER1");
        db.update("UPDATE dodo_order_info SET pay_status=6 WHERE order_id=1");
        assertThrows(ShowException.class, () -> service.review(id(s), version(s), true, "", 1, 99));
        assertEquals("PENDING", service.detail(id(s), 10L, 1).get("auditStatus"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_coupon", Integer.class));
    }

    @Test
    void mediaFailureDoesNotConsumeQuota() {
        ShowActivity a = activity("ONCE");
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("media", (ShowMediaStore) url -> {
            throw new ShowException(400, "图片转存失败，请重新上传后重试");
        });
        ShowActivityService failing = newService(beans);
        assertThrows(ShowException.class,
                () -> failing.submit(null, a.id, "ORDER1", List.of("tmp/a.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_show_submission", Integer.class));
        assertDoesNotThrow(() -> submit(a, "ORDER1"));
    }

    @Test
    void mapperQueriesPreserveFiltersStatisticsAndOrderSnapshot() {
        ShowActivity a = activity("UNLIMITED");
        Map<String, Object> first = submit(a, "ORDER1"), second = submit(a, "ORDER2");
        long created = ((Number) first.get("createTime")).longValue();
        service.review(id(first), version(first), false, "补充材料", 1, 99);
        Map<String, Object> resubmitted = service.submit(id(first), a.id, "ORDER1", List.of("show/new.jpg"), UUID.randomUUID().toString(),
                10, "更改昵称", "", 1);
        assertEquals(created, ((Number) resubmitted.get("createTime")).longValue());
        assertEquals("测试用户", resubmitted.get("nickName"));
        db.update("UPDATE dodo_order_info SET order_amount=99 WHERE order_id=1");
        assertEquals(0,
                new BigDecimal("50.00").compareTo(new BigDecimal(((Map<?, ?>) resubmitted.get("order")).get("orderAmount").toString())));
        service.review(id(resubmitted), version(resubmitted), true, "", 1, 99);
        assertEquals(1L, service.submissions(null, 1, a.id, "APPROVED", "测试", 1, 1).get("total"));
        assertEquals(0L, service.submissions(20L, 1, a.id, null, null, 1, 20).get("total"));
        assertEquals(0L, service.submissions(null, 2, a.id, null, null, 1, 20).get("total"));
        assertEquals(2L, service.submissions(null, 1, a.id, null, null, 1, 1).get("total"));
        assertEquals(1, ((List<?>) service.submissions(null, 1, a.id, null, null, 2, 1).get("results")).size());
        Map<String, Object> stats = service.statistics(a.id, 1);
        assertEquals(2L, stats.get("submittedCount"));
        assertEquals(1L, stats.get("pendingCount"));
        assertEquals(1L, stats.get("approvedCount"));
        assertEquals(new BigDecimal("5.00"), stats.get("issuedAmount"));
        db.update("UPDATE dodo_coupon SET status=2");
        assertEquals(1L, service.statistics(a.id, 1).get("usedCount"));
        assertEquals(2L, service.summary(10, 1).get("submissionCount"));
        assertEquals(new BigDecimal("5.00"), service.summary(10, 1).get("rewardAmount"));
        assertEquals(0L, service.summary(10, 2).get("submissionCount"));
        List<Map<String, Object>> logs = service.logs(a.id, 1, 1, 100);
        assertEquals("APPROVE", logs.get(0).get("action"));
        assertEquals(1, logs.stream().filter(log -> "APPROVE".equals(log.get("action"))).count());
        assertEquals(1, service.logs(a.id, 1, 2, 1).size());
        assertThrows(ShowException.class, () -> service.logs(a.id, 2, 1, 10));
        assertFalse(service.detail(id(second), 10L, 1).containsKey("requestKey"));
    }

    @Test
    void catalogOrdersPagesAndRejectsStaleConfiguration() {
        ShowActivity first = activity("ONCE"), second = activity("ONCE");
        first.sortOrder = 0;
        int staleVersion = first.version;
        ShowActivity saved = service.save(first, 1, 99);
        first.version = staleVersion;
        assertThrows(ShowException.class, () -> service.save(first, 1, 99));
        List<?> page = (List<?>) service.catalog(1, false, 1, 1).get("results");
        assertEquals(first.id, ((ShowActivity) page.get(0)).id);
        assertEquals(second.id, ((ShowActivity) ((List<?>) service.catalog(1, false, 2, 1).get("results")).get(0)).id);
        assertEquals(0L, service.catalog(2, false, 1, 10).get("total"));
        service.end(saved.id, saved.version, 1, 99);
        assertEquals(1L, service.catalog(1, true, 1, 10).get("total"));
        assertEquals(2L, service.catalog(1, false, 1, 10).get("total"));
        assertThrows(ShowException.class, () -> service.catalog(1, false, 0, 10));
    }

    @Test
    void rejectionReasonsIncludeCurrentStoreAndSharedDictionaryOnly() {
        db.update(
                "INSERT INTO dodo_dict VALUES (1,'show_reject_reason','bad','本店',1,1),(2,'show_reject_reason','shared','共用',1,0),(3,'show_reject_reason','null','默认',1,NULL),(4,'show_reject_reason','other','他店',1,2),(5,'show_reject_reason','disabled','停用',0,1)");
        assertEquals(List.of("本店", "共用", "默认"),
                service.reasons(1).stream().map(row -> row.get("label")).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void reviewHistoryRetainsEachAttemptAndScopesToSubmissionAndStore() {
        ShowActivity a = activity("UNLIMITED");
        Map<String, Object> first = submit(a, "ORDER1");
        submit(a, "ORDER2");
        service.review(id(first), version(first), false, "缺少话题，请补图", 1, 99);
        service.submit(id(first), a.id, "ORDER1", List.of("show/new.jpg"), UUID.randomUUID().toString(), 10, "用户", "", 1);
        Map<String, Object> history = service.submissionHistory(id(first), 1, 1, 20);
        assertEquals(3L, history.get("total"));
        List<?> rows = (List<?>) history.get("results");
        Map<?, ?> latest = (Map<?, ?>) rows.get(0), rejected = (Map<?, ?>) rows.get(1), original = (Map<?, ?>) rows.get(2);
        assertEquals("RESUBMIT", latest.get("action"));
        assertEquals(2, latest.get("attempts"));
        assertEquals(List.of("show/new.jpg"), latest.get("images"));
        assertEquals("当前用户昵称", latest.get("actorName"));
        assertEquals("测试审核员", rejected.get("actorName"));
        assertEquals("缺少话题，请补图", rejected.get("reply"));
        assertEquals(List.of("show/screenshot.jpg"), original.get("images"));
        assertEquals(1, ((List<?>) service.submissionHistory(id(first), 1, 2, 1).get("results")).size());
        assertThrows(ShowException.class, () -> service.submissionHistory(id(first), 2, 1, 20));
        assertThrows(ShowException.class, () -> service.submissionHistory(99999, 1, 1, 20));
        assertThrows(ShowException.class, () -> service.submissionHistory(id(first), 1, 0, 20));
        db.update("UPDATE dodo_show_activity_log SET detail='{}' WHERE submission_id=? AND action='SUBMIT'", id(first));
        Map<?, ?> legacy = (Map<?, ?>) ((List<?>) service.submissionHistory(id(first), 1, 1, 20).get("results")).get(2);
        assertNull(legacy.get("attempts"));
        assertEquals(List.of(), legacy.get("images"));
    }

    @Test
    void operationLogsResolveNamesAndPreserveConfigurationAndIssuedReward() {
        ShowActivity a = activity("UNLIMITED");
        a.rewardPrice = new BigDecimal("8.00");
        service.save(a, 1, 99);
        Map<String, Object> edit = service.logs(a.id, 1, 1, 20).get(0);
        Map<?, ?> detail = (Map<?, ?>) edit.get("detailData");
        assertEquals("测试审核员", edit.get("actorName"));
        assertEquals("ADMIN", edit.get("actorType"));
        assertEquals(5.0, ((Number) ((Map<?, ?>) detail.get("before")).get("rewardPrice")).doubleValue());
        assertEquals(8.0, ((Number) ((Map<?, ?>) detail.get("after")).get("rewardPrice")).doubleValue());
        Map<String, Object> submitted = submit(a, "ORDER1");
        service.review(id(submitted), version(submitted), true, "符合规则", 1, 99);
        a.rewardPrice = new BigDecimal("10.00");
        service.save(a, 1, 99);
        Map<?, ?> approved = (Map<?, ?>) service.logs(a.id, 1, 1, 20).stream()
                .filter(row -> "APPROVE".equals(row.get("action"))).findFirst().orElseThrow().get("detailData");
        assertEquals(8.0, ((Number) approved.get("rewardPrice")).doubleValue());
        assertNotNull(approved.get("couponId"));
        db.update("DELETE FROM dodo_admin_user WHERE user_id=99");
        assertEquals("员工（ID：99）", service.logs(a.id, 1, 1, 20).get(0).get("actorName"));
        db.update("UPDATE dodo_show_activity_log SET detail='invalid' WHERE id=?", edit.get("id"));
        assertEquals(false, service.logs(a.id, 1, 1, 20).stream().filter(row -> edit.get("id").equals(row.get("id")))
                .findFirst().orElseThrow().get("detailReadable"));
    }

    @Test
    void submittedOrderBatchIncludesAllAuditStatesAndEnforcesScopeAndLimits() {
        ShowActivity a = activity("UNLIMITED");
        Map<String, Object> first = submit(a, "ORDER1"), second = submit(a, "ORDER2");
        assertEquals(Set.of("ORDER1", "ORDER2"), new HashSet<>(service.submittedOrders(10, 1, List.of(" ORDER1 ", "ORDER2", "ORDER1"))));
        service.review(id(first), version(first), false, "补充材料", 1, 99);
        service.review(id(second), version(second), true, "", 1, 99);
        assertEquals(2, service.submittedOrders(10, 1, List.of("ORDER1", "ORDER2")).size());
        assertTrue(service.submittedOrders(20, 1, List.of("ORDER1", "ORDER2")).isEmpty());
        assertTrue(service.submittedOrders(10, 2, List.of("ORDER1", "ORDER2")).isEmpty());
        assertTrue(service.submittedOrders(10, 1, List.of("UNKNOWN")).isEmpty());
        assertThrows(ShowException.class, () -> service.submittedOrders(10, 1, null));
        assertThrows(ShowException.class, () -> service.submittedOrders(10, 1, List.of()));
        assertThrows(ShowException.class, () -> service.submittedOrders(10, 1, Collections.nCopies(101, "ORDER1")));
        assertThrows(ShowException.class, () -> service.submittedOrders(10, 1, List.of(" ")));
    }
}
