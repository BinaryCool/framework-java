package com.dodosupermarket.show.core;

import com.dodosupermarket.show.bean.po.ShowActivityLog;
import com.dodosupermarket.show.bean.po.ShowActivityRecord;
import com.dodosupermarket.show.bean.po.ShowSubmissionRecord;
import com.dodosupermarket.show.dao.ShowOrderDAO;
import com.dodosupermarket.show.service.ShowActivityLogService;
import com.dodosupermarket.show.service.ShowActivityRecordService;
import com.dodosupermarket.show.service.ShowSubmissionRecordService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 晒单业务编排服务，持久化统一通过框架通用 Service 和 Mapper 完成。
 */
public class ShowActivityService {
    private final ShowActivityRecordService activities;
    private final ShowSubmissionRecordService submissions;
    private final ShowActivityLogService activityLogs;
    private final ApplicationEventPublisher events;
    private final ShowOrderDAO orders;
    private final ObjectMapper json;
    private final ObjectProvider<ShowRewardIssuer> rewards;
    private final ObjectProvider<ShowMediaStore> media;
    private final TransactionTemplate tx;

    public ShowActivityService(ShowActivityRecordService activities, ShowSubmissionRecordService submissions,
                               ShowActivityLogService activityLogs, ShowOrderDAO orders,
                               DataSourceTransactionManager manager, ObjectMapper json,
                               ObjectProvider<ShowRewardIssuer> rewards, ObjectProvider<ShowMediaStore> media,
                               ApplicationEventPublisher events) {
        this.activities = activities;
        this.submissions = submissions;
        this.activityLogs = activityLogs;
        this.events = events;
        this.orders = orders;
        this.json = json;
        this.rewards = rewards;
        this.media = media;
        this.tx = new TransactionTemplate(manager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.tx.setTimeout(30);
    }

    /**
     * 事务开始前固定业务库主库，直到提交或回滚后恢复调用方路由。
     * 旧读写插件会把 SELECT（含锁查询）切到自动提交的读连接，必须覆盖完整事务。
     * 普通读取也使用主库，避免刚提交或审核后因复制延迟显示旧状态。
     */
    private <T> T onPrimary(Supplier<T> work) {
        Boolean previousForce = ConnectionHolder.FORCE_WRITE.get();
        String previousConnection = ConnectionHolder.CURRENT_CONNECTION.get();
        String previousDataSource = DataSourceHolder.CURRENT_DATASOURCE.get();
        ConnectionHolder.FORCE_WRITE.set(true);
        ConnectionHolder.CURRENT_CONNECTION.set(ConnectionHolder.WRITE);
        DataSourceHolder.CURRENT_DATASOURCE.set(DataSourceHolder.DEFAULT);
        try {
            return work.get();
        } finally {
            restore(ConnectionHolder.FORCE_WRITE, previousForce);
            restore(ConnectionHolder.CURRENT_CONNECTION, previousConnection);
            restore(DataSourceHolder.CURRENT_DATASOURCE, previousDataSource);
        }
    }

    private static <T> void restore(ThreadLocal<T> holder, T value) {
        if (value == null) {
            holder.remove();
        } else {
            holder.set(value);
        }
    }

    private <T> T write(Supplier<T> work) {
        return onPrimary(() -> tx.execute(status -> work.get()));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("晒单数据序列化失败", ex);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("晒单数据读取失败", ex);
        }
    }

    private List<String> strings(String value) {
        try {
            return json.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("晒单图片读取失败", ex);
        }
    }

    /** 将数据库数值列转换为 long，供分页结果组装使用。 */
    public static long number(Object value) {
        return ((Number) value).longValue();
    }

    /** 解析活动配置并补充主键、门店、版本及实时活动状态。 */
    private ShowActivity unpack(ShowActivityRecord record) {
        ShowActivity activity = decode(record.getPayload(), ShowActivity.class);
        activity.id = record.getId();
        activity.storeId = record.getStoreId();
        activity.version = record.getVersion();
        activity.ended = record.getEnded();
        long now = System.currentTimeMillis();
        activity.state = activity.active(now) ? "ACTIVE" : (!activity.ended && activity.startTime > now ? "SCHEDULED" : "ENDED");
        return activity;
    }

    /**
     * 按门店限定活动范围，写操作必须先锁活动，再访问晒单和订单。
     */
    private ShowActivityRecord activityRecord(long id, long store, boolean lock) {
        ShowActivityRecord record = lock ? activities.lockById(id, store)
                : first(activities.queryByArgs("id", id, "storeId", store));
        if (record == null) throw new ShowException(404, "活动不存在");
        return record;
    }

    private ShowActivity activity(long id, long store, boolean lock) {
        return unpack(activityRecord(id, store, lock));
    }

    /** 客户端读取单个活动详情，并实时计算活动状态。 */
    public ShowActivity activity(long id, long store) {
        return onPrimary(() -> activity(id, store, false));
    }

    /**
     * 校验并转存图片；文件转存不参与数据库回滚，失败时需重新上传。
     */
    private String image(String url) {
        ShowException.require(url != null && !url.trim().isEmpty() && url.length() <= 2048, "图片地址不正确");
        String value = url.trim();
        ShowException.require(!value.startsWith("blob:") && !value.startsWith("file:") && !value.startsWith("data:")
                && !value.contains("..") && !value.contains("\\"), "请等待图片上传完成");
        ShowException.require(value.matches("(?i).+\\.(jpg|jpeg|png|webp)(\\?.*)?"), "图片仅支持JPG、PNG、WebP");
        ShowMediaStore storage = media.getIfAvailable();
        return storage == null ? value : storage.persist(value);
    }

    /** 写入操作日志，detail 统一序列化为 JSON 快照保留当时内容。 */
    private void log(long activityId, Long submissionId, long actor, String action, Object detail) {
        ShowActivityLog record = new ShowActivityLog();
        record.setActivityId(activityId);
        record.setSubmissionId(submissionId);
        record.setActorId(actor);
        record.setAction(action);
        record.setDetail(encode(detail));
        record.setCreateMillis(System.currentTimeMillis());
        activityLogs.add(record);
    }

    /**
     * 配置、版本和日志一起保存，旧表单不可覆盖最新配置。
     */
    public ShowActivity save(ShowActivity activity, long store, long actor) {
        return write(() -> saveInternal(activity, store, actor));
    }

    private ShowActivity saveInternal(ShowActivity activity, long store, long actor) {
        long now = System.currentTimeMillis();
        ShowActivityRecord record = activity.id == null ? new ShowActivityRecord() : activityRecord(activity.id, store, true);
        ShowActivity previous = record.getId() == null ? null : unpack(record);
        if (activity.id != null) {
            ShowActivity before = unpack(record);
            ShowException.require(!Boolean.TRUE.equals(before.ended) && (Boolean.TRUE.equals(before.longTerm) || before.endTime > now),
                    "已结束活动不能编辑");
            if (!Objects.equals(activity.version, before.version)) throw new ShowException(409, "活动已被修改，请刷新后重试");
        }
        // 生效时间留空时取本次保存时间，规则及奖励配置仍由共享模型统一校验。
        activity.validate(now);
        activity.storeId = store;
        activity.ended = false;
        activity.state = null;
        activity.coverImage = image(activity.coverImage);
        activity.ruleImages = activity.ruleImages.stream().map(this::image).collect(Collectors.toList());
        activity.version = record.getId() == null ? 1 : record.getVersion() + 1;
        record.setStoreId(store);
        record.setSortOrder(activity.sortOrder);
        record.setStartTime(activity.startTime);
        record.setEndTime(activity.endTime);
        record.setEnded(false);
        record.setVersion(activity.version);
        record.setPayload(encode(activity));
        record.setUpdateMillis(now);
        boolean creating = record.getId() == null;
        if (creating) {
            record.setCreateMillis(now);
            activities.add(record);
        } else {
            activities.update(record);
        }
        activity.id = record.getId();
        // 编辑同时保存前后快照，后续修改不会覆盖当时的差异；旧日志仍保留其单份配置。
        log(activity.id, null, actor, creating ? "CREATE" : "EDIT",
                creating ? activity : Map.of("before", previous, "after", activity));
        return activity(activity.id, store, false);
    }

    /**
     * 结束只关闭新增及重提，保留待审核记录供后续处理。
     */
    public void end(long id, int version, long store, long actor) {
        write(() -> {
            ShowActivityRecord record = activityRecord(id, store, true);
            if (!Objects.equals(record.getVersion(), version)) throw new ShowException(409, "活动已被修改，请刷新后重试");
            if (!Boolean.TRUE.equals(record.getEnded())) {
                record.setEnded(true);
                record.setVersion(record.getVersion() + 1);
                record.setUpdateMillis(System.currentTimeMillis());
                activities.update(record);
                log(id, null, actor, "END", Map.of("version", record.getVersion()));
            }
            return null;
        });
    }

    private static <T> T first(List<T> records) {
        return records.isEmpty() ? null : records.get(0);
    }

    private static int size(int size) {
        return Math.max(1, Math.min(100, size));
    }

    /** 校验页码并换算框架查询需要的偏移量，每页最多 100 条。 */
    private Map<String, Object> paging(int page, int size) {
        ShowException.require(page > 0 && page <= 100000, "页码不正确");
        return new HashMap<>(Map.of("start", (page - 1) * size(size), "limit", size(size)));
    }

    /** 统一分页响应结构。 */
    private Map<String, Object> page(List<?> records, long total, int page, int size) {
        return Map.of("results", records, "total", total, "page", page, "size", size(size));
    }

    /**
     * 客户端仅列出当前有效活动，按排序值升序、活动编号降序分页。
     */
    public Map<String, Object> catalog(long store, boolean activeOnly, int page, int size) {
        return onPrimary(() -> {
            Map<String, Object> params = paging(page, size);
            params.put("storeId", store);
            if (activeOnly) params.put("activeAt", System.currentTimeMillis());
            long total = activities.countByArgs(params);
            List<ShowActivity> records = activities.queryByArgs(params).stream().map(this::unpack).collect(Collectors.toList());
            return page(records, total, page, size);
        });
    }

    /**
     * 核销数量沿用现有优惠券 status=2 的口径。
     */
    public Map<String, Object> statistics(long id, long store) {
        return onPrimary(() -> {
            activityRecord(id, store, false);
            return activities.statistics(id, store);
        });
    }

    /** 分页读取活动操作日志，活动必须属于当前门店。 */
    public List<Map<String, Object>> logs(long id, long store, int page, int size) {
        return onPrimary(() -> {
            activityRecord(id, store, false);
            Map<String, Object> params = paging(page, size);
            params.put("activityId", id);
            return activityLogs.queryByArgs(params).stream().map(this::logView).collect(Collectors.toList());
        });
    }

    /**
     * 先验证晒单所属门店，再按原记录分页读取提交与审核日志；图片取日志快照，不使用已被重提覆盖的当前材料。
     */
    public Map<String, Object> submissionHistory(long id, long store, int page, int size) {
        return onPrimary(() -> {
            ShowSubmissionRecord submission = submission(id, store, false);
            Map<String, Object> params = paging(page, size);
            params.put("activityId", submission.getActivityId());
            params.put("submissionId", id);
            long total = activityLogs.countByArgs(params);
            List<Map<String, Object>> records = activityLogs.queryByArgs(params).stream().map(this::logView).collect(Collectors.toList());
            return page(records, total, page, size);
        });
    }

    /**
     * 管理日志与审核历史使用同一投影；客户与员工按操作类型关联，避免相同编号串名。
     * 历史异常内容只影响该条展示，不应阻止整页日志读取，也不猜测缺失的提交次数。
     */
    private Map<String, Object> logView(ShowActivityLog record) {
        Map<?, ?> detail;
        boolean readable = true;
        try {
            detail = json.readValue(record.getDetail(), Map.class);
            if (detail == null) throw new IllegalArgumentException("日志为空");
        } catch (Exception ex) {
            detail = Map.of();
            readable = false;
        }
        boolean customer = "SUBMIT".equals(record.getAction()) || "RESUBMIT".equals(record.getAction());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("submissionId", record.getSubmissionId());
        view.put("actorId", record.getActorId());
        view.put("actorType", customer ? "CUSTOMER" : "ADMIN");
        view.put("actorName", record.getActorName() == null ? (customer ? "用户" : "员工") + "（ID：" + record.getActorId() + "）"
                : record.getActorName());
        view.put("action", record.getAction());
        view.put("detail", record.getDetail());
        view.put("detailData", detail);
        view.put("detailReadable", readable);
        view.put("createTime", record.getCreateMillis());
        view.put("reply", Objects.toString(detail.get("reply"), ""));
        view.put("images", detail.get("images") instanceof List ? detail.get("images") : List.of());
        view.put("attempts", detail.get("attempts"));
        return view;
    }

    /**
     * 提交和发券都复核订单归属、完成和退款状态，写入时锁定订单避免状态交错。
     */
    private Map<String, Object> order(String sn, long user, long store, boolean lock) {
        Map<String, Object> order = orders.findOwned(sn, user, store, lock);
        if (order == null) throw new ShowException(404, "订单不存在或不属于当前用户和门店");
        if (order.get("createTime") instanceof java.util.Date) {
            order.put("createTime", ((java.util.Date) order.get("createTime")).getTime());
        }
        int status = ((Number) order.get("orderStatus")).intValue();
        ShowException.require(status == 3 || status == 4, "仅可关联已完成的订单");
        int paid = ((Number) order.get("payStatus")).intValue();
        ShowException.require(paid != 4 && paid != 5 && paid != 6, "取消付款或退款中的订单不能参与");
        order.put("goods", orders.goods(number(order.get("orderId"))));
        return order;
    }

    /**
     * 同一活动中的订单只可新提交一次，待审、通过和驳回均占用；驳回应修改原记录。
     * 每人一次另检查用户总名额，不限次数仍允许不关联订单的多次提交。
     */
    private String participationBlockReason(ShowActivity activity, long user, String sn) {
        Map<String, Object> params = new HashMap<>(Map.of("activityId", activity.id, "storeId", activity.storeId, "userId", user));
        if (!sn.isEmpty()) {
            params.put("orderSn", sn);
            if (submissions.countByArgs(params) > 0) return "该订单已参与过本活动，请查看或修改原记录";
            params.remove("orderSn");
        }
        if ("ONCE".equals(activity.participation) && submissions.countByArgs(params) > 0) {
            return "已达到本活动参与次数上限，请查看或修改原记录";
        }
        return "";
    }

    /** 组装订单选项，并标记该订单在当前活动中是否还能提交。 */
    private Map<String, Object> selectableOrder(ShowActivity activity, String sn, long user, long store) {
        Map<String, Object> order = order(sn, user, store, false);
        String reason = participationBlockReason(activity, user, sn);
        order.put("disabled", !reason.isEmpty());
        order.put("disabledReason", reason);
        return order;
    }

    /** 分页列出用户可用于本活动的已完成订单，并标注是否已参与或已占用名额。 */
    public Map<String, Object> orders(long activityId, long user, long store, int page, int size) {
        return onPrimary(() -> {
            ShowActivity activity = activity(activityId, store, false);
            ShowException.require(activity.active(System.currentTimeMillis()), "活动尚未开始或已结束");
            Map<String, Object> params = paging(page, size);
            List<Map<String, Object>> records = new ArrayList<>();
            for (String sn : orders.eligibleOrders(user, store, (Integer) params.get("start"), size(size))) {
                records.add(selectableOrder(activity, sn, user, store));
            }
            return page(records, orders.countEligible(user, store), page, size);
        });
    }

    /**
     * 带入的订单号仍需验证归属及名额，不能直接信任页面参数。
     */
    public Map<String, Object> selectedOrder(long activityId, String sn, long user, long store) {
        return onPrimary(() -> {
            ShowActivity activity = activity(activityId, store, false);
            ShowException.require(activity.active(System.currentTimeMillis()), "活动尚未开始或已结束");
            return selectableOrder(activity, sn, user, store);
        });
    }

    /** 按门店读取晒单记录；写入路径传 lock=true 加行锁。 */
    private ShowSubmissionRecord submission(long id, long store, boolean lock) {
        ShowSubmissionRecord record = lock ? submissions.lockById(id, store)
                : first(submissions.queryByArgs("id", id, "storeId", store));
        if (record == null) throw new ShowException(404, "晒单记录不存在");
        return record;
    }

    /**
     * 显式构造接口视图，不泄露持久层的提交凭证及框架审计字段。
     */
    private Map<String, Object> detail(ShowSubmissionRecord record, Long user, long store) {
        if (record.getStoreId() != store || (user != null && !Objects.equals(record.getUserId(), user))) {
            throw new ShowException(404, "晒单记录不存在");
        }
        ShowActivity activity = activity(record.getActivityId(), store, false);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("activityId", record.getActivityId());
        view.put("userId", record.getUserId());
        view.put("nickName", record.getNickName());
        view.put("avatarUrl", record.getAvatarUrl());
        view.put("orderSn", record.getOrderSn());
        view.put("images", strings(record.getImages()));
        view.put("auditStatus", record.getAuditStatus());
        view.put("note", record.getNote());
        view.put("couponId", record.getCouponId());
        view.put("rewardPrice", record.getRewardPrice());
        view.put("attempts", record.getAttempts());
        view.put("version", record.getVersion());
        view.put("createTime", record.getCreateMillis());
        view.put("updateTime", record.getUpdateMillis());
        view.put("activity", activity);
        view.put("canResubmit", "REJECTED".equals(record.getAuditStatus()) && activity.active(System.currentTimeMillis()));
        // 使用提交时的订单快照，历史材料不随后续订单变化而改变。
        view.put("order", decode(record.getOrderSnapshot(), Map.class));
        return view;
    }

    /** 读取晒单详情；user 非空时同时校验记录归属于该用户。 */
    public Map<String, Object> detail(long id, Long user, long store) {
        return onPrimary(() -> detail(submission(id, store, false), user, store));
    }

    /** 分页查询晒单记录：管理端可按活动、审核状态和昵称筛选，客户端只能看到本人数据。 */
    public Map<String, Object> submissions(Long user, long store, Long activityId, String status, String nickName, int page, int size) {
        return onPrimary(() -> {
            Map<String, Object> params = paging(page, size);
            params.put("storeId", store);
            if (user != null) params.put("userId", user);
            if (activityId != null) params.put("activityId", activityId);
            if (status != null && !status.isEmpty()) {
                ShowException.require(List.of("PENDING", "APPROVED", "REJECTED").contains(status), "审核状态不正确");
                params.put("auditStatus", status);
            }
            if (nickName != null && !nickName.isEmpty()) params.put("nickName", nickName);
            long total = submissions.countByArgs(params);
            List<Map<String, Object>> records = submissions.queryByArgs(params).stream()
                    .map(record -> detail(record, user, store)).collect(Collectors.toList());
            return page(records, total, page, size);
        });
    }

    /** 汇总当前用户在本门店的晒单数量及累计奖励金额等入口数据。 */
    public Map<String, Object> summary(long user, long store) {
        return onPrimary(() -> submissions.summary(user, store));
    }

    /**
     * 订单页批量查询已提交状态，待审、通过及驳回均置灰入口；身份和门店由登录会话限定。
     * 这里只影响订单快捷入口，各活动仍按自己的参与规则判断是否可选。
     */
    public List<String> submittedOrders(long user, long store, List<String> orderSns) {
        ShowException.require(orderSns != null && !orderSns.isEmpty() && orderSns.size() <= 100, "每次请查询1～100笔订单");
        ShowException.require(orderSns.stream().allMatch(sn -> sn != null && !sn.trim().isEmpty() && sn.length() <= 100),
                "订单号不正确");
        List<String> normalized = orderSns.stream().map(String::trim).distinct().collect(Collectors.toList());
        // 框架查询会补充分页和默认参数，必须传入可修改的参数表。
        return onPrimary(() -> submissions.queryByArgs(new HashMap<>(Map.of("userId", user, "storeId", store, "orderSns", normalized)))
                .stream().map(ShowSubmissionRecord::getOrderSn).distinct().collect(Collectors.toList()));
    }

    /**
     * 请求凭证用于失败重试去重，驳回重提更新原记录而不另占名额。
     */
    public Map<String, Object> submit(Long id, long activityId, String sn, List<String> images, String key, long user, String nick,
                                      String avatar, long store) {
        // 统一无订单的存储表示，兼容客户端省略字段、空串和首尾空白。
        String orderSn = sn == null ? "" : sn.trim();
        return write(() -> submitInternal(id, activityId, orderSn, images, key, user, nick, avatar, store));
    }

    /**
     * 新建或重提晒单：先锁活动再锁原记录，在锁内校验名额及有效期。
     * 相同 requestKey 的重复请求直接返回原回执，不产生新写入。
     */
    private Map<String, Object> submitInternal(Long id, long activityId, String sn, List<String> images, String key, long user,
                                               String nick, String avatar, long store) {
        // 先锁活动，再锁原晒单和订单，与审核保持相同顺序，并在锁内串行检查参与名额。
        ShowActivity activity = activity(activityId, store, true);
        ShowException.require(images != null && !images.isEmpty() && images.size() <= 3, "请上传1～3张晒单截图");
        ShowException.require(key != null && key.matches("[A-Za-z0-9_-]{16,64}"), "缺少提交凭证，请重新进入");
        ShowSubmissionRecord record = null;
        if (id != null) {
            record = submission(id, store, true);
            ShowException.require(record.getUserId() == user && record.getActivityId() == activityId, "晒单记录不匹配");
            ShowException.require(Objects.equals(record.getOrderSn(), sn), "重新提交不能更换活动或订单");
            if (key.equals(record.getRequestKey())) return detail(record, user, store);
            ShowException.require("REJECTED".equals(record.getAuditStatus()), "仅未通过的晒单可以重新提交");
        } else {
            ShowSubmissionRecord repeated = first(submissions.queryByArgs("userId", user, "requestKey", key));
            if (repeated != null) {
                ShowException.require(repeated.getActivityId() == activityId, "提交凭证已使用");
                return detail(repeated, user, store);
            }
        }
        // 先处理重复请求，再校验有效期，截止后的网络重试仍可取回原回执，不产生新写入。
        ShowException.require(activity.active(System.currentTimeMillis()), "活动尚未开始或已结束，不能提交");
        // 订单要求调整只作用于新提交；原记录重提固定原关联状态，避免配置变更后无法补材料。
        if (record == null) {
            ShowException.require(!sn.isEmpty() || (Boolean.FALSE.equals(activity.orderRequired)
                    && !"PER_ORDER".equals(activity.participation)), "请关联已完成的订单");
        }
        // 可选仅允许不关联；一旦带入订单，仍完整核验归属、完成及退款状态。
        Map<String, Object> order = sn.isEmpty() ? new LinkedHashMap<>() : order(sn, user, store, true);
        if (record == null) {
            String reason = participationBlockReason(activity, user, sn);
            ShowException.require(reason.isEmpty(), reason);
        }
        List<String> saved = images.stream().map(this::image).collect(Collectors.toList());
        long now = System.currentTimeMillis();
        // 图片转存可能耗时，落库前再次检查截止时间。
        ShowException.require(activity.active(now), "活动已结束，不能提交");
        boolean creating = record == null;
        if (creating) {
            record = new ShowSubmissionRecord();
            record.setActivityId(activityId);
            record.setStoreId(store);
            record.setUserId(user);
            record.setNickName(nick);
            record.setAvatarUrl(avatar);
            record.setOrderSn(sn);
            record.setOrderSnapshot(encode(order));
            record.setAttempts(1);
            record.setVersion(1);
            record.setCreateMillis(now);
        } else {
            // 重提保留原活动、订单、用户及首次提交时间，只替换材料并递增次数和版本。
            record.setAttempts(record.getAttempts() + 1);
            record.setVersion(record.getVersion() + 1);
        }
        record.setImages(encode(saved));
        record.setAuditStatus("PENDING");
        record.setNote("");
        record.setRequestKey(key);
        record.setUpdateMillis(now);
        if (creating) submissions.add(record);
        else submissions.update(record);
        log(activityId, record.getId(), user, creating ? "SUBMIT" : "RESUBMIT",
                Map.of("images", saved, "attempts", record.getAttempts()));
        return detail(record, user, store);
    }

    /**
     * 审核、发券和日志共用框架事务；结果消息在提交后投递到统一 IM，活动结束后仍可处理待审。
     */
    public Map<String, Object> review(long id, int version, boolean approve, String note, long store, long actor) {
        return onPrimary(() -> {
            // 活动归属创建后不可变；事务外定位，避免旧代理未同步隔离级别时在等待锁前建立旧快照。
            long activityId = submission(id, store, false).getActivityId();
            return tx.execute(status -> reviewInternal(id, activityId, version, approve, note, store, actor));
        });
    }

    /** 在活动锁和晒单锁内完成审核、发券及日志记录，重复通过只返回原发券结果。 */
    private Map<String, Object> reviewInternal(long id, long activityId, int version, boolean approve, String note, long store,
                                               long actor) {
        ShowActivity activity = activity(activityId, store, true);
        ShowSubmissionRecord record = submission(id, store, true);
        // 锁查询禁用 MyBatis 缓存；重复通过只返回已发券结果，不再创建优惠券。
        if (approve && "APPROVED".equals(record.getAuditStatus())) return detail(record, null, store);
        if (record.getVersion() != version) throw new ShowException(409, "晒单已更新，请刷新后审核");
        ShowException.require("PENDING".equals(record.getAuditStatus()), "该记录已审核，请刷新");
        note = note == null ? "" : note.trim();
        ShowException.require(note.length() <= 1000 && (approve || !note.isEmpty()), "驳回时必须填写回复，最多1000字");
        Long coupon = null;
        if (approve) {
            // 有关联订单时复核退款状态；无订单记录保留原提交资格，奖励仍使用最新配置。
            if (!record.getOrderSn().isEmpty()) {
                order(record.getOrderSn(), record.getUserId(), store, true);
            }
            ShowRewardIssuer issuer = rewards.getIfAvailable();
            if (issuer == null) throw new IllegalStateException("审核发券适配器未配置");
            coupon = issuer.issue(activity, detail(record, null, store));
            ShowException.require(coupon > 0, "优惠券发放失败，请重试");
        }
        record.setAuditStatus(approve ? "APPROVED" : "REJECTED");
        record.setNote(note);
        record.setCouponId(coupon);
        record.setRewardPrice(approve ? activity.rewardPrice : null);
        record.setRewardVersion(approve ? activity.version : null);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateMillis(System.currentTimeMillis());
        submissions.update(record);
        Map<String, Object> reviewDetail = new LinkedHashMap<>(Map.of("reply", note, "activityVersion", activity.version,
                "attempts", record.getAttempts()));
        if (approve) {
            // 发券摘要取本次审核实际采用的配置，后续改券不会让历史日志显示错误金额。
            reviewDetail.put("rewardPrice", activity.rewardPrice);
            reviewDetail.put("minPrice", activity.minPrice);
            reviewDetail.put("validDays", activity.validDays);
            reviewDetail.put("couponId", coupon);
        }
        log(activityId, id, actor, approve ? "APPROVE" : "REJECT", reviewDetail);
        String message = approve ? activity.name + "审核通过，已发放$" + activity.rewardPrice + "优惠券"
                : activity.name + "审核未通过：" + note;
        events.publishEvent(new ShowReviewEvent(id, record.getUserId(), store, record.getVersion(), message));
        return detail(record, null, store);
    }

    /** 读取本门店配置的驳回原因字典。 */
    public List<Map<String, Object>> reasons(long store) {
        return onPrimary(() -> orders.reasons(store));
    }

}
