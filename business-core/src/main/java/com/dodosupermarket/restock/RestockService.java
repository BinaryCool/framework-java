package com.dodosupermarket.restock;

import com.dodosupermarket.restock.bean.po.RestockNotice;
import com.dodosupermarket.restock.bean.po.RestockSubscription;
import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import com.dodosupermarket.restock.dao.RestockContextDAO;
import com.dodosupermarket.restock.service.RestockNoticeService;
import com.dodosupermarket.restock.service.RestockSubscriptionService;
import com.dodosupermarket.restock.service.RestockSupplyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 到货订阅业务编排，持久化复用框架 Service、DAO 和 MyBatis。
 * 订阅与投递台账共用业务主库，不依赖 Redis 去重，不调用旧群发的跨库事务。
 */
public class RestockService {
    private static final ObjectMapper AUDIT_JSON = new ObjectMapper();
    private final RestockSubscriptionService subscriptions;
    private final RestockNoticeService notices;
    private final RestockSupplyService supplies;
    private final RestockContextDAO context;
    private final TransactionTemplate tx;

    public RestockService(RestockSubscriptionService subscriptions, RestockNoticeService notices,
                          RestockSupplyService supplies, RestockContextDAO context, DataSourceTransactionManager manager) {
        this.subscriptions = subscriptions;
        this.notices = notices;
        this.supplies = supplies;
        this.context = context;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(20);
    }

    private <T> T primary(Supplier<T> work) {
        Boolean force = ConnectionHolder.FORCE_WRITE.get();
        String connection = ConnectionHolder.CURRENT_CONNECTION.get();
        String source = DataSourceHolder.CURRENT_DATASOURCE.get();
        ConnectionHolder.FORCE_WRITE.set(true);
        ConnectionHolder.CURRENT_CONNECTION.set(ConnectionHolder.WRITE);
        DataSourceHolder.CURRENT_DATASOURCE.set(DataSourceHolder.DEFAULT);
        try {
            return work.get();
        } finally {
            restore(ConnectionHolder.FORCE_WRITE, force);
            restore(ConnectionHolder.CURRENT_CONNECTION, connection);
            restore(DataSourceHolder.CURRENT_DATASOURCE, source);
        }
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) {
            local.remove();
        } else {
            local.set(previous);
        }
    }

    private <T> T transaction(Supplier<T> work) {
        // 仅重试已经回滚的数据库事务；外部渠道调用始终位于此方法之外。
        for (int attempt = 0; ; attempt++) {
            try {
                return primary(() -> tx.execute(status -> work.get()));
            } catch (org.springframework.dao.ConcurrencyFailureException failure) {
                if (attempt >= 2) {
                    throw failure;
                }
            }
        }
    }

    private LocalDateTime now() {
        // 与 DATETIME(3) 一致，避免插入时向上舍入使新任务被误判为尚未到执行时间。
        return context.currentTime().toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    private Timestamp timestamp() {
        return Timestamp.valueOf(now());
    }

    /** 调用前必须已持有订阅行锁；状态、版本和通知生成仍在同一事务内提交。 */
    private void transition(RestockSubscription sub, String state, LocalDateTime current) {
        sub.setState(state);
        sub.setVersion(sub.getVersion() + 1);
        sub.setUpdatedAt(Timestamp.valueOf(current));
        subscriptions.update(sub);
    }

    /** 仅更新已持有行锁的投递记录；已读和点击由独立原子更新维护，不随投递覆盖。 */
    private void transition(RestockNotice notice, String state) {
        notice.setDeliveryState(state);
        notice.setVersion(notice.getVersion() + 1);
        notice.setUpdatedAt(timestamp());
        notices.update(notice);
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new RestockException(code, message);
        }
    }

    private static void scope(long user, long store, long shop) {
        require(user > 0 && store > 0 && shop > 0, "INVALID_SCOPE", "请登录并选择门店");
    }

    private Map<String, Object> goods(long store, long shop, long goodsId) {
        List<Map<String, Object>> found = goodsBatch(store, shop, java.util.Collections.singletonList(goodsId));
        return found.isEmpty() ? null : found.get(0);
    }

    private List<Map<String, Object>> goodsBatch(long store, long shop, List<Long> goodsIds) {
        return context.goods(store, shop, goodsIds);
    }

    private String dispatchDays(long store) {
        String days = context.dispatchDays(store);
        return days == null ? "" : days;
    }

    private Map<String, Object> availability(long store, long shop, long goodsId) {
        return RestockAvailability.evaluate(goods(store, shop, goodsId), now(), dispatchDays(store));
    }

    /** 查询单个商品当前能否购买及不可购买原因，供商品详情页展示。 */
    public Map<String, Object> availabilityFor(long store, long shop, long goodsId) {
        require(store > 0 && shop > 0 && goodsId > 0, "INVALID_SCOPE", "门店或商品无效");
        return primary(() -> availability(store, shop, goodsId));
    }

    /** 商品列表批量读取一次主库事实，避免每张商品卡分别建立三次数据库查询。 */
    public Map<Long, Map<String, Object>> availabilityBatch(long store, long shop, List<Long> goodsIds) {
        require(store > 0 && shop > 0 && goodsIds != null && goodsIds.size() <= 1000, "INVALID_SCOPE", "门店或商品批次无效");
        return primary(() -> {
            Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
            if (goodsIds.isEmpty()) {
                return result;
            }
            LocalDateTime current = now();
            String days = dispatchDays(store);
            for (Map<String, Object> product : goodsBatch(store, shop, goodsIds)) {
                result.put(number(product, "goodsId"), RestockAvailability.evaluate(product, current, days));
            }
            return result;
        });
    }

    /** 唯一身份先落库再加行锁；终态必须带预期版本才能主动重新订阅，旧请求不能复活已取消记录。 */
    public Map<String, Object> subscribe(long user, long store, long shop, long scheme, long goodsId, Integer expectedVersion) {
        scope(user, store, shop);
        require(goodsId > 0 && scheme > 0, "INVALID_GOODS", "商品或门店无效");
        return transaction(() -> {
            LocalDateTime current = now();
            Map<String, Object> product = goods(store, shop, goodsId);
            require(product != null && RestockAvailability.number(product.get("status")) == 1
                    && RestockAvailability.number(product.get("isDelete")) == 0
                    && RestockAvailability.number(product.get("inShop")) > 0, "INVALID_GOODS", "商品已失效或不属于当前门店");
            // 同商品的订阅和停供统一先锁供货行，防止新订阅越过并发停供检查。
            RestockSupply seed = new RestockSupply();
            seed.setId(new RestockSupplyKey(store, shop, goodsId));
            seed.setState("PENDING");
            seed.setReason("");
            seed.setVersion(1);
            seed.setUpdatedAt(Timestamp.valueOf(current));
            supplies.add(seed);
            RestockSupply supply = supplies.lockByKey(seed.getId());

            RestockSubscription candidate = new RestockSubscription();
            candidate.setId(id());
            candidate.setUserId(user);
            candidate.setStoreId(store);
            candidate.setShopId(shop);
            candidate.setSchemeId(scheme);
            candidate.setGoodsId(goodsId);
            candidate.setGoodsName(product.get("goodsName") == null ? "商品" : product.get("goodsName").toString());
            candidate.setGoodsImg(product.get("goodsImg") == null ? "" : product.get("goodsImg").toString());
            candidate.setState("WAITING");
            candidate.setGeneration(1);
            candidate.setVersion(1);
            candidate.setSubscribedAt(Timestamp.valueOf(current));
            candidate.setExpiresAt(Timestamp.valueOf(current.plusDays(30)));
            candidate.setNextCheckAt(Timestamp.valueOf(current));
            candidate.setUpdatedAt(Timestamp.valueOf(current));
            // 框架 create 的 Mapper 保留唯一身份插入语义，重复请求不会覆盖当前轮次。
            subscriptions.add(candidate);
            RestockSubscription sub = subscriptions.lockIdentity(user, store, shop, goodsId);
            boolean created = candidate.getId().equals(sub.getId());
            boolean expired = !sub.getExpiresAt().toLocalDateTime().isAfter(current);
            if (!created && "WAITING".equals(sub.getState()) && expired) {
                transition(sub, "EXPIRED", current);
            }
            if (!created && ("WAITING".equals(sub.getState()) || expectedVersion == null)) {
                return RestockViews.subscription(sub);
            }
            require(created || sub.getVersion().equals(expectedVersion), "VERSION_CONFLICT", "状态已变化，请刷新后重试");
            Map<String, Object> available = availability(store, shop, goodsId);
            require(!Boolean.TRUE.equals(available.get("canSell")), "ALREADY_AVAILABLE", "商品已可购买，请直接加购");
            require(!"UNAVAILABLE".equals(available.get("unavailableReason")), "INVALID_GOODS", "商品不支持到货提醒");
            require(supply == null || !"STOPPED".equals(supply.getState()), "SUPPLY_STOPPED", "该商品已停止供货");
            if (!created) {
                sub.setGeneration(sub.getGeneration() + 1);
                sub.setSchemeId(scheme);
                sub.setSubscribedAt(Timestamp.valueOf(current));
                sub.setExpiresAt(Timestamp.valueOf(current.plusDays(30)));
                sub.setNextCheckAt(Timestamp.valueOf(current));
                transition(sub, "WAITING", current);
            }
            return RestockViews.subscription(sub);
        });
    }

    /** 查询用户对指定商品的订阅状态并附带实时可售信息；从未订阅时 state 为 NONE。 */
    public Map<String, Object> status(long user, long store, long shop, long goodsId) {
        scope(user, store, shop);
        return primary(() -> {
            RestockSubscription sub = subscriptions.queryFirst("userId", user, "storeId", store, "shopId", shop, "goodsId", goodsId);
            Map<String, Object> result = sub == null ? new LinkedHashMap<>() : RestockViews.subscription(sub);
            if (sub == null) {
                result.put("state", "NONE");
            }
            result.putAll(availability(store, shop, goodsId));
            return result;
        });
    }

    /** 取消也持有订阅锁。所有投递均按订阅→通知的顺序加锁，避免取消与重试间死锁。 */
    public Map<String, Object> cancel(long user, long store, long shop, String subscriptionId, int version) {
        scope(user, store, shop);
        return transaction(() -> {
            RestockSubscription sub = subscriptions.lockById(subscriptionId, user, store, shop);
            require(sub != null, "NOT_FOUND", "提醒不存在或不属于当前门店");
            if ("CANCELLED".equals(sub.getState())) {
                return RestockViews.subscription(sub);
            }
            require(sub.getVersion() == version, "VERSION_CONFLICT", "状态已变化，请刷新后重试");
            transition(sub, "CANCELLED", now());
            notices.cancelPending(subscriptionId, timestamp());
            return RestockViews.subscription(sub);
        });
    }

    /** 分页查询我的到货提醒，并为每条记录补充商品当前可售状态。 */
    public Map<String, Object> mine(long user, long store, long shop, int page, int size) {
        scope(user, store, shop);
        require(page > 0 && page <= 100000 && size > 0 && size <= 100, "INVALID_PAGE", "分页参数无效");
        return primary(() -> {
            List<Map<String, Object>> list = subscriptions.mine(user, store, shop, size, (page - 1) * size);
            for (Map<String, Object> item : list) {
                item.putAll(availability(store, shop, number(item, "goodsId")));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("results", list);
            result.put("total", subscriptions.countByArgs("userId", user, "storeId", store, "shopId", shop));
            return result;
        });
    }

    /** 标记通知已读或已点击；重复标记保持幂等，不影响投递状态。 */
    public void read(long user, long store, long shop, String notificationId, boolean clicked) {
        scope(user, store, shop);
        transaction(() -> {
            int changed = notices.markRead(notificationId, user, store, shop, clicked, timestamp());
            require(changed > 0 || notices.queryFirst("id", notificationId, "userId", user, "storeId", store, "shopId", shop) != null,
                    "NOT_FOUND", "通知不存在");
            return null;
        });
    }

    /** next_check_at 轮转等待记录，避免前一批长期缺货的商品饿死后面的订阅。 */
    public Map<String, Object> scan(Long store, Long goodsId, int limit, boolean dryRun) {
        return scan(store, goodsId, limit, dryRun, false);
    }

    /** 人工核查可立即查看尚未到定时核查时间的订阅，仍由订阅锁和唯一通知键去重。 */
    public Map<String, Object> scan(Long store, Long goodsId, int limit, boolean dryRun, boolean force) {
        require(limit > 0 && limit <= 500, "INVALID_LIMIT", "每批最多核查 500 条");
        Long scopedStore = store == null ? null : positive(store);
        Long scopedGoods = goodsId == null ? null : positive(goodsId);
        List<String> due = primary(() -> subscriptions.dueIds(scopedStore, scopedGoods, limit, force));
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (String candidate : due) {
            try {
                String result = transaction(() -> inspect(candidate, dryRun));
                counts.merge(result, 1, Integer::sum);
            } catch (RuntimeException error) {
                // 单条故障不丢弃整批；只返回订阅 ID，避免把个人或渠道信息写入日志。
                failures.add(candidate);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", due.size());
        result.put("counts", counts);
        result.put("failedIds", failures);
        result.put("dryRun", dryRun);
        return result;
    }

    private static long positive(Long value) {
        require(value != null && value > 0, "INVALID_SCOPE", "门店或商品无效");
        return value;
    }

    /**
     * 核查单条订阅：过期、已购买、不可供货或已可售时进入终态，否则延后两分钟再查。
     * dryRun 只返回将要进入的状态，不写库。
     */
    private String inspect(String subscriptionId, boolean dryRun) {
        RestockSubscription sub = subscriptions.lockById(subscriptionId, null, null, null);
        if (sub == null || !"WAITING".equals(sub.getState())) {
            return "SKIPPED";
        }
        LocalDateTime current = now();
        String terminal = null;
        if (!sub.getExpiresAt().toLocalDateTime().isAfter(current)) {
            terminal = "EXPIRED";
        } else if (purchased(sub)) {
            terminal = "FULFILLED";
        } else {
            Map<String, Object> available = availability(sub.getStoreId(), sub.getShopId(), sub.getGoodsId());
            if ("UNAVAILABLE".equals(available.get("unavailableReason"))) {
                terminal = "UNAVAILABLE";
            } else if (Boolean.TRUE.equals(available.get("canSell"))) {
                terminal = "NOTIFIED";
            }
        }
        if (dryRun) {
            return terminal == null ? "WAITING" : terminal;
        }
        if (terminal == null) {
            subscriptions.deferCheck(subscriptionId, Timestamp.valueOf(current.plusMinutes(2)));
            return "WAITING";
        }
        transition(sub, terminal, current);
        if ("NOTIFIED".equals(terminal) || "UNAVAILABLE".equals(terminal)) {
            createNotice(sub, terminal.equals("NOTIFIED") ? "ARRIVAL" : "UNAVAILABLE", null);
        }
        return terminal;
    }

    private boolean purchased(RestockSubscription sub) {
        // 订单创建时间仅到秒，同秒订单保守按已满足处理；不推断不存在的支付时间戳。
        Timestamp since = Timestamp.valueOf(sub.getSubscribedAt().toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        return context.purchased(sub.getUserId(), sub.getStoreId(), sub.getShopId(), sub.getGoodsId(), since) > 0;
    }

    /** 按订阅轮次生成站内通知；到货通知进入待投递队列，不可供货通知只留档。 */
    private void createNotice(RestockSubscription sub, String kind, String reason) {
        LocalDateTime current = now();
        String title = "ARRIVAL".equals(kind) ? "您关注的商品到货了" : "商品供货状态更新";
        String content = "ARRIVAL".equals(kind) ? sub.getGoodsName() + "已恢复可购买，库存以商品页实时展示为准。"
                : sub.getGoodsName() + "暂不能继续等待到货。" + (reason == null ? "商品已失效。" : reason);
        // 原因保留完整审计，用户通知按字段容量截取，避免长原因使停供事务整体回滚。
        if (content.codePointCount(0, content.length()) > 500) {
            content = content.substring(0, content.offsetByCodePoints(0, 500));
        }
        RestockNotice notice = new RestockNotice();
        notice.setId(id());
        notice.setSubscriptionId(sub.getId());
        notice.setGeneration(sub.getGeneration());
        notice.setKind(kind);
        notice.setUserId(sub.getUserId());
        notice.setStoreId(sub.getStoreId());
        notice.setShopId(sub.getShopId());
        notice.setGoodsId(sub.getGoodsId());
        notice.setTitle(title);
        notice.setContent(content);
        notice.setDeliveryState("ARRIVAL".equals(kind) ? "PENDING" : "SKIPPED");
        notice.setRequestId("restock_" + notice.getId());
        notice.setAttempts(0);
        notice.setVersion(1);
        notice.setNextAttemptAt(Timestamp.valueOf(current));
        notice.setExpiresAt(Timestamp.valueOf(current.plusDays(1)));
        notice.setCreatedAt(Timestamp.valueOf(current));
        notice.setUpdatedAt(Timestamp.valueOf(current));
        // 框架新增仍由订阅轮次唯一键去重，与订阅状态在同一事务中持久化。
        notices.add(notice);
    }

    /** 查询到期待投递的通知，供发送调度器分批领取；store 为空时跨门店读取。 */
    public List<Map<String, Object>> pending(Long store, int limit) {
        require(limit > 0 && limit <= 100, "INVALID_LIMIT", "投递批次无效");
        return primary(() -> notices.pending(store == null ? null : positive(store), limit));
    }

    /** 先提交 SENDING 再访问外部渠道。进程中断遗留 SENDING 只能变 UNKNOWN，不能超时抢占重发。 */
    public String deliver(String noticeId, RestockGateway gateway) {
        Map<String, Object> claimed = transaction(() -> claim(noticeId));
        if (claimed == null) {
            return "SKIPPED";
        }
        RestockGateway.Result result;
        try {
            result = gateway.send(claimed);
        } catch (RuntimeException failure) {
            result = RestockGateway.Result.UNKNOWN;
        }
        String state = result == null ? "UNKNOWN" : result.name();
        transaction(() -> {
            notices.finishAttempt(noticeId, ((Number) claimed.get("attempts")).intValue(), state,
                    "ACCEPTED".equals(state) ? null : state, timestamp());
            return null;
        });
        return state;
    }

    /**
     * 投递前领取并复核通知：先锁订阅再锁通知，重新校验状态、有效期、购买及供货情况。
     * 返回 null 表示本轮跳过；再次售罄只延后同一通知，不改变版本。
     */
    private Map<String, Object> claim(String noticeId) {
        RestockNotice initial = notices.queryById(noticeId);
        if (initial == null) {
            return null;
        }
        // 查通知身份后仍先锁订阅，再锁通知；锁查询禁用 MyBatis 缓存。
        RestockSubscription sub = subscriptions.lockById(initial.getSubscriptionId(), null, null, null);
        RestockNotice notice = notices.lockById(noticeId, null);
        if (!"PENDING".equals(notice.getDeliveryState()) || notice.getNextAttemptAt().toLocalDateTime().isAfter(now())) {
            return null;
        }
        String stop = null;
        if (!"NOTIFIED".equals(sub.getState()) || !sub.getGeneration().equals(notice.getGeneration())) {
            stop = "CANCELLED";
        } else if (!notice.getExpiresAt().toLocalDateTime().isAfter(now())) {
            stop = "EXPIRED";
        } else if (purchased(sub)) {
            stop = "SKIPPED";
        } else if (supplies.queryFirst("storeId", sub.getStoreId(), "shopId", sub.getShopId(), "goodsId", sub.getGoodsId(),
                "state", "STOPPED") != null) {
            stop = "SKIPPED";
        } else if (!Boolean.TRUE.equals(availability(sub.getStoreId(), sub.getShopId(), sub.getGoodsId()).get("canSell"))) {
            // 再次售罄只延后同一通知；不递增版本，与原有延后语义保持一致。
            notice.setNextAttemptAt(Timestamp.valueOf(now().plusMinutes(2)));
            notice.setUpdatedAt(timestamp());
            notices.update(notice);
            return null;
        }
        Map<String, Object> user = context.user(sub.getUserId());
        if (stop == null && (user == null || user.get("clientId") == null || user.get("clientId").toString().trim().isEmpty()
                || RestockAvailability.number(user.get("isSubscribeMsg")) != 1 || RestockAvailability.number(user.get("isOpenPush")) != 1)) {
            stop = "SKIPPED";
        }
        if (stop != null) {
            transition(notice, stop);
            return null;
        }
        // 第一次投递固定目标设备，重试时不能把同一个请求标识改投到另一个 CID。
        if (notice.getTargetClientId() != null && !notice.getTargetClientId().equals(user.get("clientId"))) {
            notice.setLastError("DEVICE_CHANGED");
            transition(notice, "SKIPPED");
            return null;
        }
        notice.setTargetClientId(user.get("clientId").toString());
        notice.setAttempts(notice.getAttempts() + 1);
        transition(notice, "SENDING");
        Map<String, Object> claimed = RestockViews.notice(notice);
        claimed.put("schemeId", sub.getSchemeId());
        return claimed;
    }

    /** 已发送但无法确认的请求只记录未知，必须先核对渠道；不以租约到期作为再次发送的授权。 */
    public int recoverUnknown() {
        return transaction(() -> notices.recoverUnknown(timestamp(), Timestamp.valueOf(now().minusMinutes(5))));
    }

    /** 人工重试被渠道明确拒绝的通知，沿用原请求标识；未知结果必须先核对渠道。 */
    public Map<String, Object> retry(long store, long actor, String noticeId, int version) {
        return transaction(() -> {
            RestockNotice notice = notices.lockById(noticeId, store);
            require(notice != null, "NOT_FOUND", "通知不存在");
            if ("PENDING".equals(notice.getDeliveryState())) {
                return RestockViews.notice(notice);
            }
            require(notice.getVersion() == version, "VERSION_CONFLICT", "投递状态已变化");
            require("REJECTED".equals(notice.getDeliveryState()), "RECONCILIATION_REQUIRED", "只能重试渠道明确拒绝的任务；未知结果请先核对渠道");
            require(notice.getExpiresAt().toLocalDateTime().isAfter(now()), "EXPIRED", "通知已过期");
            notice.setNextAttemptAt(timestamp());
            transition(notice, "PENDING");
            audit(store, actor, "RETRY", noticeId, "沿用原 request_id");
            return RestockViews.notice(notice);
        });
    }

    /** 管理端按商品汇总待补货需求，并补充商品当前可售状态。 */
    public Map<String, Object> demands(long store, int page, int size) {
        require(store > 0 && page > 0 && page <= 100000 && size > 0 && size <= 100, "INVALID_PAGE", "分页或门店无效");
        return primary(() -> {
            List<Map<String, Object>> list = subscriptions.demands(store, size, (page - 1) * size);
            for (Map<String, Object> row : list) {
                row.putAll(availability(store, number(row, "shopId"), number(row, "goodsId")));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("results", list);
            result.put("total", subscriptions.demandCount(store));
            return result;
        });
    }

    /**
     * 更新商品供货处理状态；停供时同步结束等待中的订阅并生成不可供货通知。
     * 先锁供货记录再锁订阅，与新增订阅的加锁顺序保持一致。
     */
    public void supply(long store, long shop, long goodsId, long actor, String owner, String state, String reason, LocalDateTime expected, int version) {
        require(Arrays.asList("PENDING", "PROCESSING", "RESTORED", "PAUSED", "STOPPED").contains(state), "INVALID_STATE", "供货状态无效");
        require(owner != null && owner.length() <= 100 && reason != null && reason.length() <= 500, "INVALID_INPUT", "负责人或原因长度无效");
        require(!Arrays.asList("PAUSED", "STOPPED").contains(state) || !reason.trim().isEmpty(), "REASON_REQUIRED", "请填写不能补货的原因");
        transaction(() -> {
            // 先锁供货再锁订阅，与新增订阅保持一致；扫描和投递不反向等待供货锁。
            RestockSupply supply = supplies.lockByKey(new RestockSupplyKey(store, shop, goodsId));
            require(supply != null && supply.getVersion() == version, "VERSION_CONFLICT", "供货状态已变化，请刷新后重试");
            List<RestockSubscription> waiting = subscriptions.lockGoods(store, shop, goodsId);
            require(!"RESTORED".equals(state) || Boolean.TRUE.equals(availability(store, shop, goodsId).get("canSell")),
                    "NOT_AVAILABLE", "商品尚未恢复可购买，请先核查库存、上下架及销售时间");
            supply.setState(state);
            supply.setOwnerId(actor);
            supply.setOwnerName(owner);
            supply.setReason(reason);
            supply.setExpectedAt(expected == null ? null : Timestamp.valueOf(expected));
            supply.setUpdatedAt(timestamp());
            // 联合主键及预期版本一起绑定，保留原条件更新的受影响行数校验。
            require(supplies.updateVersioned(supply) == 1, "VERSION_CONFLICT", "供货状态已变化，请刷新后重试");
            for (RestockSubscription sub : waiting) {
                if ("STOPPED".equals(state) && "WAITING".equals(sub.getState())) {
                    transition(sub, "UNAVAILABLE", now());
                    createNotice(sub, "UNAVAILABLE", reason);
                }
            }
            subscriptions.scheduleGoods(store, shop, goodsId, timestamp());
            audit(store, actor, "SUPPLY_" + state, shop + ":" + goodsId, reason);
            return null;
        });
    }

    /** 查询门店最近的投递台账，供管理端核对渠道处理结果。 */
    public List<Map<String, Object>> deliveries(long store, int limit) {
        require(store > 0 && limit > 0 && limit <= 100, "INVALID_SCOPE", "门店或条数无效");
        return primary(() -> notices.deliveries(store, limit));
    }

    /** 查询用户当前等待中的商品编号，供商品列表标记已订阅状态。 */
    public List<Long> activeGoods(long user, long store, long shop) {
        scope(user, store, shop);
        return primary(() -> subscriptions.activeGoods(user, store, shop));
    }

    /** 人工核对必须记录渠道凭据说明；UNKNOWN 不能直接进入待发队列。 */
    public void resolve(long store, long actor, String noticeId, int version, String outcome, String evidence) {
        require(Arrays.asList("ACCEPTED", "REJECTED").contains(outcome) && evidence != null
                && evidence.trim().length() >= 5 && evidence.length() <= 500, "EVIDENCE_REQUIRED", "请记录渠道核对结果和凭据说明");
        transaction(() -> {
            int changed = notices.reconcile(noticeId, store, version, outcome, timestamp());
            require(changed == 1, "VERSION_CONFLICT", "只有当前版本的未知任务可以核对");
            audit(store, actor, "RESOLVE_" + outcome, noticeId, evidence);
            return null;
        });
    }

    /** 复用业务主库操作日志，与状态变更同事务；日志失败必须回滚，避免无凭据地允许重发。 */
    private void audit(long store, long actor, String action, String target, String detail) {
        require(store > 0 && actor > 0, "INVALID_SCOPE", "操作身份无效");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("business", "RESTOCK");
        content.put("action", action);
        content.put("targetId", target);
        content.put("detail", detail);
        try {
            context.audit(store, actor, AUDIT_JSON.writeValueAsString(content), timestamp());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("补货提醒操作日志序列化失败", error);
        }
    }
}
