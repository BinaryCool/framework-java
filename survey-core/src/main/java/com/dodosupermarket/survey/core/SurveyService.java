package com.dodosupermarket.survey.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Supplier;

/**
 * 问卷调查核心服务。
 * 管理端：问卷的创建/编辑/发布/暂停/归档、答卷明细、统计与 CSV 导出；
 * 用户端：问卷中心列表、签发答题凭证（支持游客）、拉取 schema 与提交答卷。
 * 使用独立数据源与事务，关闭时释放连接池。
 */
public class SurveyService implements AutoCloseable {
    private final HikariDataSource source;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    /**
     * 答题凭证有效期：2 小时
     */
    private static final long TICKET_TTL = 2 * 60 * 60 * 1000L;

    /**
     * 构造服务：基于专用连接池创建 JdbcTemplate 与事务模板（事务超时 20 秒）
     */
    public SurveyService(HikariDataSource source, ObjectMapper mapper) {
        this.source = source;
        this.mapper = mapper;
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setTimeout(20);
    }

    /**
     * 关闭连接池
     */
    @Override
    public void close() {
        source.close();
    }

    /**
     * 生成 32 位随机主键
     */
    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * MySQL 流式查询：逐行读取答卷，避免一次性缓冲整张表
     */
    private void stream(String sql, String id, int version, RowCallbackHandler callback) {
        jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(Integer.MIN_VALUE); // MySQL streaming: do not buffer the entire response table.
            statement.setString(1, id);
            statement.setInt(2, version);
            return statement;
        }, callback);
    }

    /**
     * 读取行中的数值字段
     */
    private static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    /**
     * 解析库中存储的 JSON 文本
     */
    private JsonNode json(Object text) {
        try {
            return mapper.readTree(text.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Stored survey JSON is invalid", e);
        }
    }

    /**
     * 按 ID 查询问卷；ID 格式非法或不存在抛 404，lock 为 true 时加行锁（FOR UPDATE）
     */
    private Map<String, Object> find(String id, boolean lock) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new SurveyException(404, "问卷不存在");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM dudu_survey WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new SurveyException(404, "问卷不存在");
        return rows.get(0);
    }

    /**
     * 乐观锁校验：revision 不一致说明问卷已被他人修改，抛 409
     */
    private static void revision(Map<String, Object> row, int revision) {
        if (n(row, "revision") != revision) throw new SurveyException(409, "问卷已被修改，请刷新后重试");
    }

    /**
     * 在事务内锁定问卷行后执行动作，保证编辑/发布/提交等操作对同一问卷串行执行
     */
    private <T> T locked(String id, Supplier<T> action) {
        return tx.execute(status -> {
            find(id, true);
            return action.get();
        });
    }

    /**
     * 校验分页参数
     */
    private static void page(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw SurveyException.invalid("分页参数无效");
    }

    /**
     * 读取指定发布版本的问卷定义，不存在抛 404
     */
    private JsonNode version(String id, int version) {
        List<String> rows = jdbc.queryForList("SELECT definition FROM dudu_survey_version WHERE survey_id=? AND version=?", String.class, id, version);
        if (rows.isEmpty()) throw new SurveyException(404, "发布版本不存在");
        return json(rows.get(0));
    }

    /**
     * 组装问卷视图：草稿、当前发布版本、目标门店、是否有未发布变更、答卷数
     */
    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonNode draft = json(row.get("draft"));
        result.put("id", row.get("id"));
        result.put("definition", draft);
        result.put("title", draft.path("title").asText());
        result.put("status", row.get("status"));
        result.put("revision", n(row, "revision"));
        result.put("version", n(row, "active_version"));
        JsonNode published = n(row, "active_version") > 0 ? version(row.get("id").toString(), (int) n(row, "active_version")) : null;
        result.put("targetStoreId", (published == null ? draft : published).path("storeId").asLong());
        result.put("hasUnpublishedChanges", published != null && !published.equals(draft));
        result.put("updatedAt", row.get("updated_at"));
        result.put("responseCount", jdbc.queryForObject("SELECT COUNT(*) FROM dudu_survey_response WHERE survey_id=?", Long.class, row.get("id")));
        return result;
    }

    /**
     * 管理端分页查询问卷列表，按标题模糊搜索（LIKE 特殊字符转义）
     */
    public Map<String, Object> list(int page, int size, String search) {
        page(page, size);
        if (search == null) search = "";
        if (search.length() > 120) throw SurveyException.invalid("搜索词过长");
        String filter = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String where = " WHERE JSON_UNQUOTE(JSON_EXTRACT(draft,'$.title')) LIKE ? ESCAPE '!'";
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM dudu_survey" + where + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?", filter, size, (page - 1) * size);
        List<Map<String, Object>> items = new ArrayList<>();
        rows.forEach(r -> items.add(view(r)));
        return Map.of("items", items, "total", jdbc.queryForObject("SELECT COUNT(*) FROM dudu_survey" + where, Long.class, filter));
    }

    /**
     * 管理端问卷详情：视图信息 + 历史发布版本列表
     */
    public Map<String, Object> detail(String id) {
        Map<String, Object> result = view(find(id, false));
        result.put("versions", jdbc.queryForList("SELECT version,published_at AS publishedAt FROM dudu_survey_version WHERE survey_id=? ORDER BY version DESC", id));
        return result;
    }

    /**
     * 创建（id 为空）或保存问卷草稿；带乐观锁校验，已归档的问卷不可编辑
     */
    public Map<String, Object> save(String id, int revision, JsonNode definition, long admin) {
        ObjectNode draft = SurveyDefinition.normalize(definition);
        long now = System.currentTimeMillis();
        if (id == null || id.isEmpty()) {
            String created = id();
            jdbc.update("INSERT INTO dudu_survey(id,draft,created_by,updated_by,created_at,updated_at) VALUES(?,?,?,?,?,?)", created, draft.toString(), admin, admin, now, now);
            return detail(created);
        }
        return locked(id, () -> {
            Map<String, Object> row = find(id, false);
            revision(row, revision);
            if ("ARCHIVED".equals(row.get("status"))) throw new SurveyException(409, "已归档的问卷不可编辑");
            jdbc.update("UPDATE dudu_survey SET draft=?,revision=revision+1,updated_by=?,updated_at=? WHERE id=?", draft.toString(), admin, now, id);
            return detail(id);
        });
    }

    /**
     * 发布问卷：将当前草稿固化为新的版本记录，并将问卷置为已发布状态；已过结束时间的问卷不可发布
     */
    public Map<String, Object> publish(String id, int revision, long admin) {
        return locked(id, () -> {
            Map<String, Object> row = find(id, false);
            revision(row, revision);
            if ("ARCHIVED".equals(row.get("status"))) throw new SurveyException(409, "已归档的问卷不可发布");
            JsonNode draft = SurveyDefinition.normalize(json(row.get("draft")));
            long now = System.currentTimeMillis();
            if (draft.path("endsAt").asLong() > 0 && draft.path("endsAt").asLong() <= now)
                throw SurveyException.invalid("问卷已经超过结束时间");
            int next = (int) n(row, "active_version") + 1;
            jdbc.update("INSERT INTO dudu_survey_version(survey_id,version,definition,published_by,published_at) VALUES(?,?,?,?,?)", id, next, draft.toString(), admin, now);
            jdbc.update("UPDATE dudu_survey SET active_version=?,status='PUBLISHED',revision=revision+1,updated_by=?,updated_at=? WHERE id=?", next, admin, now, id);
            return detail(id);
        });
    }

    /**
     * 变更问卷状态（仅支持暂停 PAUSED / 归档 ARCHIVED）；已归档不可再变更
     */
    public Map<String, Object> state(String id, int revision, String state, long admin) {
        if (!Set.of("PAUSED", "ARCHIVED").contains(state)) throw SurveyException.invalid("状态无效");
        return locked(id, () -> {
            Map<String, Object> row = find(id, false);
            revision(row, revision);
            if ("ARCHIVED".equals(row.get("status"))) throw new SurveyException(409, "问卷已经归档");
            jdbc.update("UPDATE dudu_survey SET status=?,revision=revision+1,updated_by=?,updated_at=? WHERE id=?", state, admin, System.currentTimeMillis(), id);
            return detail(id);
        });
    }

    /**
     * 判断问卷当前是否可填写：已发布、门店匹配（0 表示全部门店）、处于起止时间范围内
     */
    private boolean available(Map<String, Object> row, JsonNode definition, long store) {
        long now = System.currentTimeMillis(), target = definition.path("storeId").asLong();
        return "PUBLISHED".equals(row.get("status")) && (target == 0 || target == store)
                && definition.path("startsAt").asLong() <= now && (definition.path("endsAt").asLong() == 0 || now < definition.path("endsAt").asLong());
    }

    /**
     * 目录查询的 FROM/WHERE 片段。
     * <p>答卷关联必须同时带上 {@code guest_id}：只按 {@code user_id} 关联时，
     * 游客身份(user_id=0)会命中其它所有游客的答卷——"是否已填写"恒为真，
     * 且一个问卷会因多行答卷在分页中重复出现。
     */
    public static String catalogWhere() {
        return " FROM dudu_survey s JOIN dudu_survey_version v ON v.survey_id=s.id AND v.version=s.active_version"
                + " LEFT JOIN dudu_survey_response r ON r.survey_id=s.id AND r.user_id=? AND r.guest_id=?"
                + " WHERE s.status='PUBLISHED'"
                + " AND (JSON_EXTRACT(v.definition,'$.storeId')=0 OR JSON_EXTRACT(v.definition,'$.storeId')=?)"
                + " AND JSON_EXTRACT(v.definition,'$.startsAt')<=?"
                + " AND (JSON_EXTRACT(v.definition,'$.endsAt')=0 OR JSON_EXTRACT(v.definition,'$.endsAt')>?)";
    }

    /**
     * 用户端问卷中心：分页查询当前用户/门店可见的已发布问卷（含题目数、截止时间、是否已填写）；定向过滤在 SQL 层完成，草稿修改不影响已发布的定向
     * <p>登录用户的答卷以 {@code (user_id, guest_id='')} 定位，游客请使用带 guestId 的重载。
     */
    public Map<String, Object> catalog(long user, long store, int page, int size) {
        return catalog(user, store, page, size, "");
    }

    /**
     * 用户端问卷中心（可指定游客标识）。
     */
    public Map<String, Object> catalog(long user, long store, int page, int size, String guestId) {
        page(page, size);
        String guest = guestId == null ? "" : guestId;
        // Filter in SQL before pagination; an edited draft cannot change published targeting.
        String where = catalogWhere();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> result = new ArrayList<>();
        jdbc.query("SELECT s.id,s.active_version,v.definition,r.id AS response_id" + where + " ORDER BY v.published_at DESC,s.id DESC LIMIT ? OFFSET ?", (RowCallbackHandler) r -> {
            JsonNode d = json(r.getString("definition"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getString("id"));
            item.put("title", d.path("title").asText());
            item.put("description", d.path("description").asText());
            item.put("version", r.getInt("active_version"));
            item.put("questionCount", d.path("questions").size());
            item.put("endsAt", d.path("endsAt").asLong());
            item.put("answered", r.getString("response_id") != null);
            result.add(item);
        }, user, guest, store, now, now, size, (page - 1) * size);
        return Map.of("items", result, "total", jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, user, guest, store, now, now));
    }

    /**
     * 答题凭证的 SHA-256 哈希：库中只存哈希不落明文，格式非法直接抛 401
     */
    private static String hash(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}"))
            throw new SurveyException(401, "答题凭证无效，请从问卷中心重新进入");
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 签发答题凭证（ticket）：支持登录用户与游客（guestId 仅为浏览器化名，账号身份以会话为准）。
     * 校验问卷可填写、防重复填写，限制同一用户/游客的凭证数量（防刷），凭证 2 小时有效。
     */
    public Map<String, Object> issue(String id, long user, long store, String guestId) {
        // A guest ID is only a browser pseudonym; account identity always comes from the session.
        final String guest = user > 0 ? "" : guestId;
        if (user < 0 || (user == 0 && (guest == null || !guest.matches("[a-f0-9]{32}"))))
            throw SurveyException.invalid("游客标识无效，请重新打开问卷");
        return locked(id, () -> {
            Map<String, Object> row = find(id, false);
            JsonNode d = version(id, (int) n(row, "active_version"));
            // Guests have no verified current store. Attribute a targeted shared survey to its published store.
            long responseStore = user > 0 ? store : d.path("storeId").asLong();
            if (!available(row, d, responseStore)) throw new SurveyException(409, "问卷暂不可填写");
            if (!jdbc.queryForList("SELECT id FROM dudu_survey_response WHERE survey_id=? AND user_id=? AND guest_id=?", id, user, guest).isEmpty())
                throw new SurveyException(409, "你已经填写过这份问卷");
            long now = System.currentTimeMillis();
            jdbc.update("DELETE FROM dudu_survey_ticket WHERE expires_at<? LIMIT 1000", now);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dudu_survey_ticket WHERE survey_id=? AND user_id=? AND guest_id=? AND expires_at>?", Long.class, id, user, guest, now) >= 30)
                throw new SurveyException(429, "打开次数过多，请稍后重试");
            String token = id() + id();
            jdbc.update("INSERT INTO dudu_survey_ticket(token_hash,survey_id,version,user_id,guest_id,store_id,expires_at) VALUES(?,?,?,?,?,?,?)", hash(token), id, n(row, "active_version"), user, guest, responseStore, now + TICKET_TTL);
            return Map.of("ticket", token, "expiresAt", now + TICKET_TTL);
        });
    }

    /**
     * 校验答题凭证：存在且未过期，否则抛 401
     */
    private Map<String, Object> ticket(String token) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM dudu_survey_ticket WHERE token_hash=? AND expires_at>?", hash(token), System.currentTimeMillis());
        if (rows.isEmpty()) throw new SurveyException(401, "答题凭证已过期，请从问卷中心重新进入");
        return rows.get(0);
    }

    /**
     * 校验凭证对应的问卷仍处于发布中且版本未变，返回凭证对应版本的问卷定义
     */
    private JsonNode requireActive(Map<String, Object> t) {
        String id = t.get("survey_id").toString();
        Map<String, Object> row = find(id, false);
        JsonNode d = version(id, (int) n(t, "version"));
        if (!available(row, d, n(t, "store_id")) || n(row, "active_version") != n(t, "version"))
            throw new SurveyException(409, "问卷已更新或停止收集，请返回问卷中心");
        return d;
    }

    /**
     * 凭答题凭证拉取问卷填写页所需的 SurveyJS schema
     */
    public Map<String, Object> fill(String token) {
        Map<String, Object> t = ticket(token);
        JsonNode d = requireActive(t);
        return Map.of("surveyId", t.get("survey_id"), "version", n(t, "version"), "schema", SurveyDefinition.schema(d));
    }

    /**
     * 提交答卷：事务内再次校验凭证（可能因等待行锁而过期）与问卷状态；
     * 重复提交幂等返回已有答卷 ID，不重复落库。
     */
    public Map<String, Object> submit(String token, JsonNode answers) {
        Map<String, Object> t = ticket(token);
        String id = t.get("survey_id").toString();
        return locked(id, () -> {
            // A ticket may expire while waiting for a concurrent submission's row lock.
            ticket(token);
            List<String> previous = jdbc.queryForList("SELECT id FROM dudu_survey_response WHERE survey_id=? AND user_id=? AND guest_id=?", String.class, id, n(t, "user_id"), t.get("guest_id"));
            if (!previous.isEmpty()) return Map.of("responseId", previous.get(0), "alreadySubmitted", true);
            JsonNode d = requireActive(t);
            ObjectNode clean = SurveyDefinition.answers(d, answers);
            String response = id();
            jdbc.update("INSERT INTO dudu_survey_response(id,survey_id,version,user_id,guest_id,store_id,answers,submitted_at) VALUES(?,?,?,?,?,?,?,?)", response, id, n(t, "version"), n(t, "user_id"), t.get("guest_id"), n(t, "store_id"), clean.toString(), System.currentTimeMillis());
            return Map.of("responseId", response, "alreadySubmitted", false);
        });
    }

    /**
     * 管理端分页查看某发布版本的答卷明细（登录用户与游客分开标识）
     */
    public Map<String, Object> responses(String id, int version, int page, int size) {
        page(page, size);
        find(id, false);
        JsonNode d = version(id, version);
        List<Map<String, Object>> items = new ArrayList<>();
        jdbc.query("SELECT id,user_id,guest_id,store_id,answers,submitted_at FROM dudu_survey_response WHERE survey_id=? AND version=? ORDER BY submitted_at DESC,id DESC LIMIT ? OFFSET ?", (RowCallbackHandler) r -> {
            boolean loggedIn = r.getLong("user_id") > 0;
            items.add(Map.of("id", r.getString("id"), "userId", loggedIn ? r.getString("user_id") : "", "guestId", r.getString("guest_id"), "loggedIn", loggedIn, "storeId", r.getString("store_id"), "answers", json(r.getString("answers")), "submittedAt", r.getLong("submitted_at")));
        }, id, version, size, (page - 1) * size);
        return Map.of("items", items, "definition", d, "total", jdbc.queryForObject("SELECT COUNT(*) FROM dudu_survey_response WHERE survey_id=? AND version=?", Long.class, id, version));
    }

    /**
     * 统计某版本答卷：选择/评分/判断题按选项计数，文本题抽样最多 20 条；流式扫描避免内存溢出
     */
    public Map<String, Object> stats(String id, int version) {
        find(id, false);
        JsonNode d = version(id, version);
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Map<String, Long>> counts = new HashMap<>();
        Map<String, List<String>> samples = new HashMap<>();
        Map<String, Long> answered = new HashMap<>();
        for (JsonNode q : d.path("questions")) {
            String name = q.path("name").asText();
            Map<String, Long> values = new LinkedHashMap<>();
            q.path("choices").forEach(c -> values.put(c.asText(), 0L));
            if (q.path("type").asText().equals("rating")) for (int i = 1; i <= 5; i++) values.put("" + i, 0L);
            if (q.path("type").asText().equals("boolean")) {
                values.put("true", 0L);
                values.put("false", 0L);
            }
            counts.put(name, values);
            samples.put(name, new ArrayList<>());
            answered.put(name, 0L);
            results.add(Map.of("name", name, "title", q.path("title").asText(), "type", q.path("type").asText(), "counts", values, "samples", samples.get(name)));
        }
        long[] total = {0};
        stream("SELECT answers FROM dudu_survey_response WHERE survey_id=? AND version=? ORDER BY submitted_at DESC,id DESC", id, version, r -> {
            total[0]++;
            JsonNode a = json(r.getString(1));
            for (JsonNode q : d.path("questions")) {
                String name = q.path("name").asText();
                JsonNode value = a.get(name);
                if (value == null) continue;
                answered.put(name, answered.get(name) + 1);
                if (Set.of("text", "comment").contains(q.path("type").asText())) {
                    if (samples.get(name).size() < 20) samples.get(name).add(value.asText());
                } else {
                    Iterable<JsonNode> values = value.isArray() ? value : List.of(value);
                    for (JsonNode v : values) counts.get(name).merge(v.asText(), 1L, Long::sum);
                }
            }
        });
        return Map.of("total", total[0], "questions", results, "answered", answered, "version", version);
    }

    /**
     * CSV 单元格转义：双引号包裹 + 防表格公式注入（=+-@ 等前导字符加单引号前缀）
     */
    private static String csv(String text) {
        // Spreadsheet formula injection protection applies to every cell, including titles.
        String trimmed = text.stripLeading();
        if (!trimmed.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        else if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /**
     * 导出某版本答卷为 CSV：事务快照保证导出期间数据一致，限制单次 1 万份、内容 10MB，带 BOM 便于 Excel 打开
     */
    public Map<String, Object> export(String id, int version) {
        // Snapshot prevents concurrent submissions from changing pagination during export.
        return tx.execute(status -> {
            find(id, false);
            JsonNode d = version(id, version);
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM dudu_survey_response WHERE survey_id=? AND version=?", Long.class, id, version);
            if (total > 10000) throw SurveyException.invalid("单次导出最多 10000 份答卷，请联系管理员分批导出");
            StringBuilder out = new StringBuilder("\uFEFF\"答卷ID\",\"填写身份\",\"用户ID\",\"游客标识\",\"门店ID\",\"提交时间(UTC)\"");
            for (JsonNode q : d.path("questions")) out.append(',').append(csv(q.path("title").asText()));
            out.append("\r\n");
            stream("SELECT * FROM dudu_survey_response WHERE survey_id=? AND version=? ORDER BY submitted_at,id", id, version, r -> {
                if (out.length() > 10000000) throw SurveyException.invalid("导出文件过大，请联系管理员分批导出");
                boolean loggedIn = r.getLong("user_id") > 0;
                out.append(csv(r.getString("id"))).append(',').append(csv(loggedIn ? "登录用户" : "游客")).append(',').append(csv(loggedIn ? r.getString("user_id") : "")).append(',').append(csv(r.getString("guest_id"))).append(',').append(csv(r.getString("store_id"))).append(',').append(csv(java.time.Instant.ofEpochMilli(r.getLong("submitted_at")).toString()));
                JsonNode a = json(r.getString("answers"));
                for (JsonNode q : d.path("questions")) {
                    JsonNode value = a.get(q.path("name").asText());
                    out.append(',').append(csv(value == null ? "" : value.isTextual() ? value.asText() : value.toString()));
                }
                out.append("\r\n");
            });
            return Map.of("filename", "survey-" + id + "-v" + version + ".csv", "content", out.toString());
        });
    }
}
