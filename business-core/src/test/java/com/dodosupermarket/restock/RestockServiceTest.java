package com.dodosupermarket.restock;

import com.dodosupermarket.restock.dao.RestockContextDAO;
import com.dodosupermarket.restock.dao.RestockNoticeDAO;
import com.dodosupermarket.restock.dao.RestockSubscriptionDAO;
import com.dodosupermarket.restock.dao.RestockSupplyDAO;
import com.dodosupermarket.restock.service.logic.RestockNoticeServiceImpl;
import com.dodosupermarket.restock.service.logic.RestockSubscriptionServiceImpl;
import com.dodosupermarket.restock.service.logic.RestockSupplyServiceImpl;
import com.dodosupermarket.support.BusinessSqlFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RestockServiceTest {
    protected JdbcTemplate jdbc;
    protected RestockService service;

    @BeforeEach
    public void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
        jdbc = new JdbcTemplate(source);
        service = newService(source);
        fixture(jdbc);
        new ResourceDatabasePopulator(BusinessSqlFixture.restockSchema()).execute(source);
    }

    /** H2 和真实 MySQL 均经过生产 Mapper 与框架 Service，验证行锁、缓存和事务行为。 */
    protected RestockService newService(javax.sql.DataSource source) {
        Configuration configuration = new Configuration(new Environment("restock", new SpringManagedTransactionFactory(), source));
        for (String name : List.of("Subscription", "Notice", "Supply", "Context")) {
            String resource = "com/dodosupermarket/restock/dao/Restock" + name + "DAO.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(Objects.requireNonNull(input), configuration, resource, configuration.getSqlFragments()).parse();
            } catch (java.io.IOException error) {
                throw new IllegalStateException("补货提醒测试 Mapper 加载失败", error);
            }
        }
        SqlSessionTemplate sessions = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        return new RestockService(new RestockSubscriptionServiceImpl(sessions.getMapper(RestockSubscriptionDAO.class)),
                new RestockNoticeServiceImpl(sessions.getMapper(RestockNoticeDAO.class)),
                new RestockSupplyServiceImpl(sessions.getMapper(RestockSupplyDAO.class)),
                sessions.getMapper(RestockContextDAO.class), new DataSourceTransactionManager(source));
    }

    static void fixture(JdbcTemplate db) {
        new ResourceDatabasePopulator(new ClassPathResource("db/restock-existing-fixture.sql")).execute(db.getDataSource());
        db.update("INSERT INTO dodo_goods VALUES (100,1,'测试商品','',0,1,1,0,0,1,1,NULL,NULL)");
        db.update("INSERT INTO dodo_shop_goods VALUES (10,100,1)");
        db.update("INSERT INTO dodo_shop_goods VALUES (11,100,1)");
        db.update("INSERT INTO dodo_users VALUES (5,'test-device',1,1,1)");
        db.update("INSERT INTO dodo_admin_user VALUES (9,'测试管理员')");
    }

    private Map<String, Object> subscribe() {
        return service.subscribe(5, 1, 10, 20, 100, null);
    }

    private String restock() {
        subscribe();
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        Map<String, Object> result = service.scan(1L, null, 50, false);
        assertEquals(new ArrayList<>(), result.get("failedIds"));
        return jdbc.queryForObject("SELECT id FROM dodo_restock_notice", String.class);
    }

    private static void concurrent(int count, Callable<?> work) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return work.call();
                }));
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void readMarkersSurviveDeliveryUpdatesAndRetry() {
        String notice = restock();
        service.read(5, 1, 10, notice, true);
        Map<String, Object> before = service.deliveries(1, 10).get(0);

        service.deliver(notice, row -> RestockGateway.Result.REJECTED);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        service.retry(1, 9, notice, version);
        assertEquals("ACCEPTED", service.deliver(notice, row -> RestockGateway.Result.ACCEPTED));

        Map<String, Object> after = service.deliveries(1, 10).get(0);
        assertEquals(before.get("readAt"), after.get("readAt"));
        assertEquals(before.get("clickedAt"), after.get("clickedAt"));
        assertEquals(before.get("requestId"), after.get("requestId"));
        assertTrue(after.get("readAt") instanceof java.sql.Timestamp);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class).intValue());
    }

    @Test
    public void concurrentSubscriptionAndScanCreateOneNotification() throws Exception {
        concurrent(8, this::subscribe);
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_subscription", Integer.class));
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        concurrent(8, () -> service.scan(1L, null, 50, false));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class));
        assertEquals("NOTIFIED", service.status(5, 1, 10, 100).get("state"));
    }

    @Test
    public void concurrentDeliveryInvokesChannelOnce() throws Exception {
        String notice = restock();
        AtomicInteger sent = new AtomicInteger();
        concurrent(8, () -> service.deliver(notice, row -> {
            sent.incrementAndGet();
            return RestockGateway.Result.ACCEPTED;
        }));
        assertEquals(1, sent.get());
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
    }

    @Test
    public void timeoutAndInterruptedWorkerNeverBlindlyResend() {
        String notice = restock();
        AtomicInteger sent = new AtomicInteger();
        assertEquals("UNKNOWN", service.deliver(notice, row -> {
            sent.incrementAndGet();
            throw new IllegalStateException("timeout after possible acceptance");
        }));
        service.deliver(notice, row -> {
            sent.incrementAndGet();
            return RestockGateway.Result.ACCEPTED;
        });
        assertEquals(1, sent.get());
        assertTrue(service.pending(1L, 50).isEmpty());
        Map<String, Object> delivery = service.deliveries(1, 10).get(0);
        expect("RECONCILIATION_REQUIRED", () -> service.retry(1, 9, notice, ((Number) delivery.get("version")).intValue()));
        jdbc.update("UPDATE dodo_restock_notice SET delivery_state='SENDING',updated_at=?", java.sql.Timestamp.valueOf("2000-01-01 00:00:00"));
        assertEquals(1, service.recoverUnknown());
        assertTrue(service.pending(1L, 50).isEmpty());
    }

    @Test
    public void explicitRejectionRetryKeepsSameChannelRequestId() {
        String notice = restock();
        List<String> keys = new ArrayList<>();
        RestockGateway rejected = row -> {
            keys.add(row.get("requestId").toString());
            return RestockGateway.Result.REJECTED;
        };
        service.deliver(notice, rejected);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        service.retry(1, 9, notice, version);
        service.retry(1, 9, notice, version);
        service.deliver(notice, rejected);
        assertEquals(2, keys.size());
        assertEquals(keys.get(0), keys.get(1));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_operate_log WHERE operate_type='补货提醒'", Integer.class));
    }

    @Test
    public void notificationAndSubscriptionRollbackTogether() {
        subscribe();
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        jdbc.execute("ALTER TABLE dodo_restock_notice ADD CONSTRAINT reject_arrival CHECK (kind<>'ARRIVAL')");
        assertEquals(1, ((List<?>) service.scan(1L, null, 50, false).get("failedIds")).size());
        assertEquals("WAITING", service.status(5, 1, 10, 100).get("state"));
        assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class));
    }

    @Test
    public void cancelAndRearmCannotReviveOldGeneration() {
        String notice = restock();
        Map<String, Object> state = service.status(5, 1, 10, 100);
        service.cancel(5, 1, 10, state.get("id").toString(), 2);
        jdbc.update("UPDATE dodo_goods SET goods_number=0");
        assertEquals("CANCELLED", subscribe().get("state"));
        state = service.subscribe(5, 1, 10, 20, 100, 3);
        assertEquals(2, ((Number) state.get("generation")).intValue());
        AtomicInteger sent = new AtomicInteger();
        service.deliver(notice, row -> {
            sent.incrementAndGet();
            return RestockGateway.Result.ACCEPTED;
        });
        assertEquals(0, sent.get());
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        service.scan(1L, null, 50, false);
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class));
    }

    @Test
    public void storeOwnershipAndShopIsolation() {
        Map<String, Object> first = subscribe();
        service.subscribe(5, 1, 11, 21, 100, null);
        expect("NOT_FOUND", () -> service.cancel(6, 1, 10, first.get("id").toString(), 1));
        expect("NOT_FOUND", () -> service.cancel(5, 1, 11, first.get("id").toString(), 1));
        expect("INVALID_GOODS", () -> service.subscribe(5, 2, 10, 20, 100, null));
        assertEquals(1, service.activeGoods(5, 1, 10).size());
        assertEquals(1L, ((Number) service.mine(5, 1, 10, 1, 20).get("total")).longValue());
        assertEquals(2, ((List<?>) service.demands(1, 1, 20).get("results")).size());
    }

    @Test
    public void sharedStockStillRespectsDelayMinimumAndStatus() {
        jdbc.update("INSERT INTO dodo_goods_share_stock VALUES (30,10,1,1)");
        jdbc.update("INSERT INTO dodo_goods_delay VALUES (40,?,1,1,1)", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusDays(10)));
        jdbc.update("UPDATE dodo_goods SET share_stock_id=30,delay_id=40");
        assertEquals("NOT_STARTED", service.availabilityFor(1, 10, 100).get("unavailableReason"));
        jdbc.update("UPDATE dodo_goods SET delay_id=NULL,min_sales_num=12");
        assertEquals("OUT_OF_STOCK", service.availabilityFor(1, 10, 100).get("unavailableReason"));
        jdbc.update("UPDATE dodo_goods SET min_sales_num=1");
        assertEquals(true, service.availabilityFor(1, 10, 100).get("canSell"));
        jdbc.update("UPDATE dodo_goods SET status=0");
        assertEquals("UNAVAILABLE", service.availabilityFor(1, 10, 100).get("unavailableReason"));
    }

    @Test
    public void noPushPermissionLeavesVisibleInboxAndReadIsIdempotent() {
        String notice = restock();
        jdbc.update("UPDATE dodo_users SET is_open_push=0");
        service.deliver(notice, row -> {
            fail("No permission must not call channel");
            return RestockGateway.Result.ACCEPTED;
        });
        service.read(5, 1, 10, notice, true);
        service.read(5, 1, 10, notice, true);
        Map<String, Object> item = ((List<Map<String, Object>>) service.mine(5, 1, 10, 1, 20).get("results")).get(0);
        assertEquals("NOTIFIED", item.get("state"));
        assertEquals("SKIPPED", item.get("deliveryState"));
        assertNotNull(item.get("clickedAt"));
    }

    @Test
    public void dryRunAndSupplyStopDoNotSendMessages() {
        subscribe();
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        service.scan(1L, null, 50, true);
        assertEquals("WAITING", service.status(5, 1, 10, 100).get("state"));
        service.supply(1, 10, 100, 9, "员工", "STOPPED", "供应商确认停供", null, 1);
        assertEquals("UNAVAILABLE", service.status(5, 1, 10, 100).get("state"));
        assertEquals("SKIPPED", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
    }

    @Test
    public void paidOrderSuppressesArrivalAndExpiryDoesNotBlockOtherRows() {
        subscribe();
        jdbc.update("INSERT INTO dodo_order_info VALUES (1,5,1,10,1,3,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO dodo_order_goods VALUES (1,100,1,1)");
        service.scan(1L, null, 50, false);
        assertEquals("FULFILLED", service.status(5, 1, 10, 100).get("state"));
        service.subscribe(6, 1, 10, 20, 100, null);
        jdbc.update("UPDATE dodo_restock_subscription SET expires_at=? WHERE user_id=6", java.sql.Timestamp.valueOf("2000-01-01 00:00:00"));
        service.scan(1L, null, 50, false);
        assertEquals("EXPIRED", service.status(6, 1, 10, 100).get("state"));
    }

    @Test
    public void cancellationDuringExternalCallCannotStartAnotherDelivery() throws Exception {
        String notice = restock();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        AtomicInteger calls = new AtomicInteger();
        try {
            var sent = executor.submit(() -> service.deliver(notice, row -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    throw new IllegalStateException(error);
                }
                return RestockGateway.Result.ACCEPTED;
            }));
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
            Map<String, Object> sub = service.status(5, 1, 10, 100);
            service.cancel(5, 1, 10, sub.get("id").toString(), ((Number) sub.get("version")).intValue());
            service.deliver(notice, row -> {
                calls.incrementAndGet();
                return RestockGateway.Result.ACCEPTED;
            });
            release.countDown();
            assertEquals("ACCEPTED", sent.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals("CANCELLED", service.status(5, 1, 10, 100).get("state"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectedRetryDoesNotSwitchTargetDeviceAndExpiredRetryFails() {
        String notice = restock();
        service.deliver(notice, row -> RestockGateway.Result.REJECTED);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        jdbc.update("UPDATE dodo_restock_notice SET expires_at='2000-01-01 00:00:00'");
        expect("EXPIRED", () -> service.retry(1, 9, notice, version));
        jdbc.update("UPDATE dodo_restock_notice SET expires_at='2099-01-01 00:00:00'");
        service.retry(1, 9, notice, version);
        jdbc.update("UPDATE dodo_users SET client_id='another-device'");
        service.deliver(notice, row -> {
            fail("The same request cannot target another device");
            return RestockGateway.Result.ACCEPTED;
        });
        assertEquals("DEVICE_CHANGED", jdbc.queryForObject("SELECT last_error FROM dodo_restock_notice", String.class));
    }

    @Test
    public void unknownRequiresScopedEvidenceAndVersionBeforeRetry() {
        String notice = restock();
        service.deliver(notice, row -> RestockGateway.Result.UNKNOWN);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        expect("EVIDENCE_REQUIRED", () -> service.resolve(1, 9, notice, version, "REJECTED", ""));
        expect("VERSION_CONFLICT", () -> service.resolve(2, 9, notice, version, "REJECTED", "渠道确认尚未发出"));
        expect("VERSION_CONFLICT", () -> service.resolve(1, 9, notice, version - 1, "REJECTED", "渠道确认尚未发出"));
        service.resolve(1, 9, notice, version, "REJECTED", "渠道记录123确认尚未接受");
        service.retry(1, 9, notice, version + 1);
        assertEquals("ACCEPTED", service.deliver(notice, row -> RestockGateway.Result.ACCEPTED));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_operate_log WHERE operate_type='补货提醒'", Integer.class));
    }

    @Test
    public void stockLossDefersSameNoticeAndNeverCreatesAnotherIdentity() {
        String notice = restock();
        jdbc.update("UPDATE dodo_goods SET goods_number=0");
        service.deliver(notice, row -> {
            fail("Sold out goods must not trigger external delivery");
            return RestockGateway.Result.ACCEPTED;
        });
        assertEquals("PENDING", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        jdbc.update("UPDATE dodo_restock_notice SET next_attempt_at='2000-01-01 00:00:00'");
        assertEquals("ACCEPTED", service.deliver(notice, row -> RestockGateway.Result.ACCEPTED));
        service.scan(1L, null, 50, false);
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class));
    }

    @Test
    public void stoppedSupplyRejectsNewSubscriptionsAndRestoredRequiresRealStock() {
        subscribe();
        expect("NOT_AVAILABLE", () -> service.supply(1, 10, 100, 9, "员工", "RESTORED", "", null, 1));
        service.supply(1, 10, 100, 9, "员工", "STOPPED", "供应商已确认停供", null, 1);
        expect("SUPPLY_STOPPED", () -> service.subscribe(6, 1, 10, 20, 100, null));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_subscription", Integer.class));
    }

    @Test
    public void batchAvailabilityRespectsInactiveRelationshipsAndSharedPools() {
        assertFalse(service.availabilityBatch(1, 10, java.util.Arrays.asList(100L, 101L)).containsKey(101L));
        jdbc.update("UPDATE dodo_shop_goods SET status=0 WHERE shop_id=10");
        assertEquals("UNAVAILABLE", service.availabilityBatch(1, 10, java.util.Collections.singletonList(100L)).get(100L).get("unavailableReason"));
        jdbc.update("UPDATE dodo_shop_goods SET status=1");
        jdbc.update("INSERT INTO dodo_goods_share_stock VALUES (30,10,1,0)");
        jdbc.update("UPDATE dodo_goods SET share_stock_id=30");
        assertEquals("INVALID_SHARED_STOCK", service.availabilityFor(1, 10, 100).get("unavailableReason"));
    }

    @Test
    public void manualCheckBypassesDueTimeAndStoppedSupplySuppressesPendingPush() {
        subscribe();
        service.scan(1L, null, 50, false);
        jdbc.update("UPDATE dodo_goods SET goods_number=10");
        assertEquals(0, service.scan(1L, null, 50, false).get("scanned"));
        assertEquals(1, service.scan(1L, null, 50, false, true).get("scanned"));
        String notice = jdbc.queryForObject("SELECT id FROM dodo_restock_notice", String.class);
        service.supply(1, 10, 100, 9, "员工", "STOPPED", "供应商确认停供", null, 1);
        service.deliver(notice, row -> {
            fail("Supply stopped before claim must suppress the push");
            return RestockGateway.Result.ACCEPTED;
        });
        assertEquals("SKIPPED", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
    }

    @Test
    public void existingOperationLogKeepsFullEvidenceAndActor() throws Exception {
        String notice = restock();
        service.deliver(notice, row -> RestockGateway.Result.UNKNOWN);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        String evidence = "渠道确认\n\"" + "凭据".repeat(246);
        service.resolve(1, 9, notice, version, "REJECTED", evidence);

        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM dodo_operate_log");
        var content = new ObjectMapper().readTree(log.get("operate_content").toString());
        assertEquals("RESTOCK", content.get("business").asText());
        assertEquals("RESOLVE_REJECTED", content.get("action").asText());
        assertEquals(notice, content.get("targetId").asText());
        assertEquals(evidence, content.get("detail").asText());
        assertEquals("补货提醒", log.get("operate_type"));
        assertEquals("测试管理员", log.get("operate_user_name"));
        assertEquals(1, ((Number) log.get("store_id")).intValue());
        assertEquals(9, ((Number) log.get("operate_user_id")).intValue());
    }

    @Test
    public void auditFailureKeepsUnknownAndCannotAuthorizeResend() {
        String notice = restock();
        service.deliver(notice, row -> RestockGateway.Result.UNKNOWN);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        rejectOperationLog();

        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.resolve(1, 9, notice, version, "REJECTED", "渠道确认未接受该请求"));
        assertEquals("UNKNOWN", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
        assertEquals(version, jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class).intValue());
        expect("RECONCILIATION_REQUIRED", () -> service.retry(1, 9, notice, version));
        assertTrue(service.pending(1L, 50).isEmpty());
    }

    @Test
    public void auditFailureRollsBackRetryEligibility() {
        String notice = restock();
        service.deliver(notice, row -> RestockGateway.Result.REJECTED);
        int version = jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class);
        rejectOperationLog();

        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.retry(1, 9, notice, version));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT delivery_state FROM dodo_restock_notice", String.class));
        assertEquals(version, jdbc.queryForObject("SELECT version FROM dodo_restock_notice", Integer.class).intValue());
    }

    @Test
    public void auditFailureRollsBackSupplyAndSubscriptionTogether() {
        subscribe();
        rejectOperationLog();

        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.supply(1, 10, 100, 9, "员工", "STOPPED", "供应商确认停供", null, 1));
        assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM dodo_restock_supply", String.class));
        assertEquals("WAITING", service.status(5, 1, 10, 100).get("state"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dodo_restock_notice", Integer.class).intValue());
    }

    private void rejectOperationLog() {
        jdbc.execute("ALTER TABLE dodo_operate_log ADD CONSTRAINT reject_restock_log CHECK (operate_type<>'补货提醒')");
    }

    private static void expect(String code, Runnable action) {
        try {
            action.run();
            fail("Expected " + code);
        } catch (RestockException error) {
            assertEquals(code, error.code);
        }
    }
}
