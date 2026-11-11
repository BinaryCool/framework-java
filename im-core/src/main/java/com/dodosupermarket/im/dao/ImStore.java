package com.dodosupermarket.im.dao;

import com.dodosupermarket.im.config.ImProperties;
import com.dodosupermarket.im.exception.ImException;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * IM 数据访问层：会话、设备租约、发消息记录、附件与已读位置的存取。
 * 使用独立的 IM 专用主库连接：SELECT FOR UPDATE 绝不能被读写分离路由到只读副本。
 * Dedicated primary connection: SELECT FOR UPDATE must never be routed to a replica.
 */
public class ImStore implements AutoCloseable {
    private final HikariDataSource source;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    /**
     * 客服会话：一个用户一条，read_seq 为会话整体已读位置
     */
    @Getter
    @Setter
    public static class Conversation {
        private String id;
        private long userId;
        private long readSeq;
    }

    /**
     * 设备租约：一次登录会话在频道中的身份凭证（uid、平台、设备指纹、过期时间、状态：1 生效 / 2 已撤销 / 3 已清理）
     */
    @Getter
    @Setter
    public static class Lease {
        private String id, conversationId, uid, sessionId, platform, deviceHash, senderRole;
        private long userId, expiresAt;
        private int state;
    }

    /**
     * 发送记录：客户端消息号 → 上游消息号 → 服务端消息 ID/序号 的幂等投递链路（state：0 待确认 / 1 已接受 / 2 系统消息尚未尝试发送）
     */
    @Getter
    @Setter
    public static class SendIntent {
        private String conversationId, clientMsgNo, upstreamNo, senderUid, senderRole, payload, messageId;
        private long userId, createdAt, messageSeq;
        private int state;
    }

    /**
     * 按行映射查询发送记录
     */
    private List<SendIntent> sendQuery(String sql, Object... args) {
        return jdbc.query(sql, (r, n) -> {
            SendIntent s = new SendIntent();
            s.conversationId = r.getString("conversation_id");
            s.userId = r.getLong("user_id");
            s.clientMsgNo = r.getString("client_msg_no");
            s.upstreamNo = r.getString("upstream_no");
            s.senderUid = r.getString("sender_uid");
            s.senderRole = r.getString("sender_role");
            s.payload = r.getString("payload");
            s.state = r.getInt("state");
            s.messageId = r.getString("message_id");
            s.messageSeq = r.getLong("message_seq");
            s.createdAt = r.getLong("created_at");
            return s;
        }, args);
    }

    /**
     * 查询会话中出现过的所有发言者（用于维护频道成员）
     */
    public List<SendIntent> publishers(String conversation) {
        return jdbc.query("SELECT DISTINCT sender_uid,user_id,sender_role FROM im_send WHERE conversation_id=?", (r, n) -> {
            SendIntent s = new SendIntent();
            s.senderUid = r.getString(1);
            s.userId = r.getLong(2);
            s.senderRole = r.getString(3);
            return s;
        }, conversation);
    }

    /**
     * 仅扫描保留账号的业务通知，不重放客户或客服手动发送的消息。
     */
    public List<SendIntent> pendingSystemSends(long since, int limit) {
        return sendQuery(
                "SELECT * FROM im_send WHERE user_id=0 AND sender_role='agent' AND state IN (0,2) AND created_at>=? ORDER BY created_at,client_msg_no LIMIT ?",
                since, Math.max(1, Math.min(100, limit)));
    }

    /**
     * 按客户端消息号查询发送记录，重试复用原发送身份。
     */
    public SendIntent send(String conversation, long user, String number) {
        List<SendIntent> found = sendQuery("SELECT * FROM im_send WHERE conversation_id=? AND user_id=? AND client_msg_no=?", conversation,
                user, number);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 按上游消息号查询发送记录（回执幂等）
     */
    public SendIntent upstreamSend(String conversation, String number) {
        List<SendIntent> found = sendQuery("SELECT * FROM im_send WHERE conversation_id=? AND upstream_no=?", conversation, number);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 查询已被上游接受（state=1）的消息
     */
    public java.util.Optional<SendIntent> message(String conversation, String id) {
        List<SendIntent> rows = sendQuery("SELECT * FROM im_send WHERE conversation_id=? AND message_id=? AND state=1 LIMIT 1",
                conversation, id);
        return rows.stream().findFirst();
    }

    /**
     * 系统通知先登记为 2（尚未尝试发送），频道初始化失败也保留可安全重试的意图。
     */
    public void insertSystem(SendIntent s) {
        jdbc.update(
                "INSERT IGNORE INTO im_send(conversation_id,user_id,client_msg_no,upstream_no,sender_uid,sender_role,payload,created_at,state) VALUES(?,?,?,?,?,?,?,?,2)",
                s.conversationId, s.userId, s.clientMsgNo, s.upstreamNo, s.senderUid, s.senderRole, s.payload, s.createdAt);
    }

    public void attemptingSystem(SendIntent s) {
        jdbc.update("UPDATE im_send SET state=0 WHERE conversation_id=? AND user_id=0 AND client_msg_no=? AND state=2",
                s.conversationId, s.clientMsgNo);
    }

    /**
     * 幂等登记普通消息发送意图，重复消息号保留原记录。
     */
    public void insert(SendIntent s) {
        jdbc.update(
                "INSERT IGNORE INTO im_send(conversation_id,user_id,client_msg_no,upstream_no,sender_uid,sender_role,payload,created_at) VALUES(?,?,?,?,?,?,?,?)",
                s.conversationId, s.userId, s.clientMsgNo, s.upstreamNo, s.senderUid, s.senderRole, s.payload, s.createdAt);
    }

    /**
     * 标记消息已被上游接受：回填服务端消息 ID 与序号
     */
    public void accepted(SendIntent s, String id, long seq) {
        jdbc.update("UPDATE im_send SET state=1,message_id=?,message_seq=? WHERE conversation_id=? AND user_id=? AND client_msg_no=?", id,
                seq, s.conversationId, s.userId, s.clientMsgNo);
        s.state = 1;
        s.messageId = id;
        s.messageSeq = seq;
    }

    /**
     * 查询附件记录
     */
    public List<Map<String, Object>> attachments(String conversation, String id) {
        return jdbc.queryForList("SELECT * FROM im_attachment WHERE id=? AND conversation_id=?", id, conversation);
    }

    /**
     * 统计用户在某会话内 since 之后的附件用量（数量与总字节数，用于配额限制）
     */
    public Map<String, Object> attachmentUsage(String conversation, long user, long since) {
        return jdbc.queryForMap(
                "SELECT COUNT(*) AS count,COALESCE(SUM(byte_size),0) AS bytes FROM im_attachment WHERE conversation_id=? AND user_id=? AND created_at>?",
                conversation, user, since);
    }

    /**
     * 插入附件记录（图片尺寸、字节数、SHA-256 用于去重与校验）
     */
    public void insertAttachment(String id, String conversation, long user, String mime, int width, int height, int byteSize, String hash,
                                 long createdAt) {
        jdbc.update(
                "INSERT INTO im_attachment(id,conversation_id,user_id,mime,width,height,byte_size,sha256,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                id, conversation, user, mime, width, height, byteSize, hash, createdAt);
    }

    /**
     * 删除附件记录（文件写入失败时回滚刚插入的记录，避免出现"有记录没文件"）
     */
    public void deleteAttachment(String id) {
        jdbc.update("DELETE FROM im_attachment WHERE id=?", id);
    }

    /**
     * 判断附件是否已被已接受的消息引用（未被引用的附件可回收）
     */
    public boolean hasAcceptedAttachment(String conversation, String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM im_send WHERE conversation_id=? AND state=1 AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.attachmentId'))=?",
                Integer.class, conversation, id);
        return count != null && count > 0;
    }

    /**
     * 使用独立主库连接池，复用物理连接；不能复用业务读写路由数据源，否则行锁可能落到只读副本。
     */
    public ImStore(ImProperties config) {
        if (config.getJdbcUrl().isEmpty() || config.getJdbcUsername().isEmpty())
            throw new IllegalArgumentException("IM dedicated JDBC configuration required");
        source = new HikariDataSource();
        source.setJdbcUrl(config.getJdbcUrl());
        source.setUsername(config.getJdbcUsername());
        source.setPassword(config.getJdbcPassword());
        source.setMaximumPoolSize(config.getJdbcPoolSize());
        source.setMinimumIdle(1);
        source.setConnectionTimeout(5000);
        // 首次查询再初始化，不因创建 Bean 就访问数据库；连接池由 Spring 随服务关闭。
        source.setInitializationFailTimeout(-1);
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(30);
    }

    @Override
    public void close() {
        source.close();
    }

    /**
     * 事务内对会话加行锁后执行动作（成员同步、发消息等串行化）；会话不存在抛 404
     */
    public <T> T locked(String id, Supplier<T> action) {
        return transaction.execute(status -> {
            if (jdbc.queryForList("SELECT id FROM im_conversation WHERE id=? FOR UPDATE", id).isEmpty())
                throw new ImException(404, "IM_CONVERSATION_NOT_FOUND");
            return action.get();
        });
    }

    /**
     * 确保用户拥有会话，没有则创建（INSERT IGNORE 防并发重复）
     */
    public Conversation ensure(long userId) {
        jdbc.update("INSERT IGNORE INTO im_conversation(id,user_id,read_seq) VALUES(?,?,0)", randomId(), userId);
        return conversations(userId).get(0);
    }

    /**
     * 查询用户的会话列表
     */
    public List<Conversation> conversations(long userId) {
        return jdbc.query("SELECT id,user_id,read_seq FROM im_conversation WHERE user_id=?", (r, n) -> {
            Conversation c = new Conversation();
            c.id = r.getString(1);
            c.userId = r.getLong(2);
            c.readSeq = r.getLong(3);
            return c;
        }, userId);
    }

    /**
     * 查询会话并校验归属，不属于该用户则抛 403
     */
    public Conversation require(String id, long owner) {
        return conversations(owner).stream().filter(c -> c.id.equals(id)).findFirst()
                .orElseThrow(() -> new ImException(403, "IM_FORBIDDEN"));
    }

    /**
     * 按 ID 查询会话（先定位归属用户再加载），不存在抛 403
     */
    public Conversation find(String id) {
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM im_conversation WHERE id=?", Long.class, id);
        if (owners.isEmpty()) throw new ImException(403, "IM_FORBIDDEN");
        return require(id, owners.get(0));
    }

    /**
     * 查询会话的全部设备租约
     */
    public List<Lease> leases(String conversation) {
        return jdbc.query("SELECT * FROM im_lease WHERE conversation_id=?", (r, n) -> {
            Lease l = new Lease();
            l.id = r.getString("id");
            l.conversationId = r.getString("conversation_id");
            l.uid = r.getString("uid");
            l.sessionId = r.getString("session_id");
            l.userId = r.getLong("user_id");
            l.platform = r.getString("platform");
            l.deviceHash = r.getString("device_hash");
            l.senderRole = r.getString("sender_role");
            l.expiresAt = r.getLong("expires_at");
            l.state = r.getInt("state");
            return l;
        }, conversation);
    }

    /**
     * 查询指定客户集合中可能在线的租约（生效中且未过期的 customer 角色）；仅为候选，还需向上游确认真实在线状态。Candidate customer transports only; a live lease alone does not imply online.
     */
    public List<Map<String, Object>> customerPresenceCandidates(List<Long> customers, long now) {
        if (customers.isEmpty()) return java.util.Collections.emptyList();
        if (customers.size() > 200) throw new IllegalArgumentException("Presence scope exceeds directory limit");
        String placeholders = String.join(",", java.util.Collections.nCopies(customers.size(), "?"));
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(customers);
        args.add(now);
        return jdbc.queryForList(
                "SELECT user_id,uid FROM im_lease WHERE user_id IN (" + placeholders + ") AND sender_role='customer' AND state=1 AND expires_at>?",
                args.toArray());
    }

    /**
     * 按 租约ID+用户+会话ID 三元组校验租约归属，不匹配抛 403
     */
    public Lease requireLease(String id, long owner, String session) {
        List<String> conversations = jdbc.queryForList("SELECT conversation_id FROM im_lease WHERE id=? AND user_id=? AND session_id=?",
                String.class, id, owner, session);
        if (conversations.isEmpty()) throw new ImException(403, "IM_FORBIDDEN");
        return leases(conversations.get(0)).stream().filter(l -> l.id.equals(id)).findFirst().orElseThrow(IllegalStateException::new);
    }

    /**
     * 插入租约（初始状态 1=生效中）
     */
    public void insert(Lease l) {
        jdbc.update(
                "INSERT INTO im_lease(id,conversation_id,user_id,session_id,uid,platform,device_hash,expires_at,state,sender_role) VALUES(?,?,?,?,?,?,?,?,1,?)",
                l.id, l.conversationId, l.userId, l.sessionId, l.uid, l.platform, l.deviceHash, l.expiresAt, l.senderRole);
    }

    /**
     * 撤销租约（state=2，待从上游频道移除）
     */
    public void revoke(Lease l) {
        jdbc.update("UPDATE im_lease SET state=2 WHERE id=?", l.id);
        l.state = 2;
    }

    /**
     * 登记孤儿租约（直接以 state=2 落库），并标记会话需要重新同步频道成员
     */
    public void recordOrphan(Lease l) {
        locked(l.conversationId, () -> {
            jdbc.update(
                    "INSERT IGNORE INTO im_lease(id,conversation_id,user_id,session_id,uid,platform,device_hash,expires_at,state,sender_role) VALUES(?,?,?,?,?,?,?,?,2,?)",
                    l.id, l.conversationId, l.userId, l.sessionId, l.uid, l.platform, l.deviceHash, l.expiresAt, l.senderRole);
            markSync(l.conversationId, true);
            return null;
        });
    }

    /**
     * 标记租约已清理（state=3，已从上游频道移除）
     */
    public void cleared(Lease l) {
        jdbc.update("UPDATE im_lease SET state=3 WHERE id=?", l.id);
        l.state = 3;
    }

    /**
     * 待巡检会话的查询：只取"确实需要处理"的会话。
     * <p>巡检每 5 秒跑一次，若把所有仍生效的会话都捞出来，会与发消息路径争同一行锁，
     * 随会话量线性放大锁竞争。因此只取三类：待同步、待清理的已撤销租约、已过期仍生效的租约。
     */
    public static final String PENDING_CONVERSATIONS_SQL =
            "SELECT DISTINCT conversation_id FROM ("
                    + "SELECT id AS conversation_id FROM im_conversation WHERE sync_required=1"
                    + " UNION SELECT conversation_id FROM im_lease WHERE state=2"
                    + " UNION SELECT conversation_id FROM im_lease WHERE state=1 AND expires_at<?"
                    + ") pending";

    /**
     * 查询待同步的会话：被标记 sync_required、存在已撤销未清理的租约、或存在已过期仍生效的租约。
     */
    public List<String> pendingConversations(long now) {
        return jdbc.queryForList(PENDING_CONVERSATIONS_SQL, String.class, now);
    }

    /**
     * 会话是否被标记需要同步频道成员
     */
    public boolean needsSync(String id) {
        return jdbc.queryForObject("SELECT sync_required FROM im_conversation WHERE id=?", Integer.class, id) == 1;
    }

    /**
     * 设置会话的频道成员同步标记
     */
    public void markSync(String id, boolean required) {
        jdbc.update("UPDATE im_conversation SET sync_required=? WHERE id=?", required ? 1 : 0, id);
    }

    /**
     * 推进会话整体已读位置（GREATEST 保证只增不减）
     */
    public void read(String conversation, long seq) {
        jdbc.update("UPDATE im_conversation SET read_seq=GREATEST(read_seq,?) WHERE id=?", seq, conversation);
    }

    /**
     * 查询某参与者在会话中的已读位置，无记录返回 0
     */
    public long readSeq(String conversation, long account) {
        List<Long> values = jdbc.queryForList("SELECT read_seq FROM im_participant_read WHERE conversation_id=? AND user_id=?", Long.class,
                conversation, account);
        return values.isEmpty() ? 0 : values.get(0);
    }

    /**
     * 推进某参与者的已读位置（upsert，GREATEST 保证只增不减）
     */
    public void read(String conversation, long account, long seq) {
        jdbc.update(
                "INSERT INTO im_participant_read(conversation_id,user_id,read_seq) VALUES(?,?,?) ON DUPLICATE KEY UPDATE read_seq=GREATEST(read_seq,VALUES(read_seq))",
                conversation, account, seq);
    }

    /**
     * 生成 32 位随机 ID
     */
    public static String randomId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
