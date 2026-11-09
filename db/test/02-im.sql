-- 测试环境：连接实际 IM 数据库后整份执行（默认使用日志库，与业务库分开）。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS im_conversation (
  id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '会话ID，32位无连字符UUID，ASCII二进制区分大小写',
  user_id BIGINT NOT NULL COMMENT '会话归属的客户用户ID，一个客户一条会话',
  read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '客户在会话中的已读消息序号，只增不减',
  sync_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否需要同步上游频道成员：0-否，1-是',
  -- 一个客户只能有一条会话
  UNIQUE KEY uk_im_conversation_user(user_id),
  -- 巡检扫描待同步会话
  KEY ix_im_conversation_sync(sync_required)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM客服会话表';

CREATE TABLE IF NOT EXISTS im_lease (
  id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '租约ID，32位UUID，每次登录/设备连接一条',
  conversation_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属会话ID（im_conversation.id）',
  user_id BIGINT NOT NULL COMMENT '租约归属用户ID，客户或客服',
  session_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '签发租约的Redis会话ID，用于核验登录态',
  uid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游IM频道成员UID，客服身份域带admin_前缀',
  platform VARCHAR(16) NOT NULL COMMENT '客户端平台：web/android/ios',
  device_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '设备标识的SHA-256摘要（URL-safe Base64），不保存明文',
  expires_at BIGINT NOT NULL COMMENT '租约过期时间（毫秒时间戳）',
  state TINYINT NOT NULL COMMENT '租约状态：1-生效中，2-待撤销（等待上游移除），3-已清理（token已清除）',
  sender_role VARCHAR(16) NOT NULL DEFAULT 'customer' COMMENT '发言角色：customer-客户，agent-客服',
  -- 上游UID全局唯一
  UNIQUE KEY uk_im_lease_uid(uid),
  -- 按会话查询设备租约
  KEY ix_im_lease_conversation(conversation_id),
  -- 巡检按状态清理租约
  KEY ix_im_lease_state(state),
  -- 巡检按状态和过期时间筛选待修复租约
  KEY ix_im_lease_state_expiry(state, expires_at),
  CONSTRAINT fk_im_lease_conversation FOREIGN KEY(conversation_id) REFERENCES im_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM设备租约表，一次登录会话在频道中的身份凭证';

CREATE TABLE IF NOT EXISTS im_participant_read (
  conversation_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话ID（im_conversation.id）',
  user_id BIGINT NOT NULL COMMENT '参与者账号ID，客服使用独立的已读水位',
  read_seq BIGINT NOT NULL DEFAULT 0 COMMENT '该参与者在会话中的已读消息序号，只增不减',
  PRIMARY KEY(conversation_id,user_id),
  CONSTRAINT fk_im_read_conversation FOREIGN KEY(conversation_id) REFERENCES im_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM会话参与者已读位置表';

CREATE TABLE IF NOT EXISTS im_send (
  conversation_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话ID（im_conversation.id）',
  user_id BIGINT NOT NULL COMMENT '发送用户ID，0表示系统通知（保留账号）',
  client_msg_no VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端消息号，与会话、用户组成主键，用于发送幂等',
  upstream_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游消息号，用于发送回执幂等',
  sender_uid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上游发送者UID',
  sender_role VARCHAR(16) NOT NULL COMMENT '发送角色：customer-客户，agent-客服',
  payload TEXT NOT NULL COMMENT '消息载荷JSON',
  state TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待确认/未知，1-上游已接受，2-系统消息尚未尝试发送',
  message_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '上游服务端消息ID，接受后回填',
  message_seq BIGINT NOT NULL DEFAULT 0 COMMENT '上游消息序号，接受后回填',
  created_at BIGINT NOT NULL COMMENT '创建时间（毫秒时间戳）',
  PRIMARY KEY(conversation_id,user_id,client_msg_no),
  -- 上游消息号在会话内唯一，保证回执幂等
  UNIQUE KEY uk_im_send_upstream(conversation_id,upstream_no),
  -- 系统通知重试扫描：user_id=0 且 state IN (0,2) 的消息按时间重放
  KEY ix_im_send_system_pending(user_id, state, created_at),
  CONSTRAINT fk_im_send_conversation FOREIGN KEY(conversation_id) REFERENCES im_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM发送意图与投递状态表';

CREATE TABLE IF NOT EXISTS im_attachment (
 id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '附件ID，32位UUID',
 conversation_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属会话ID（im_conversation.id）',
 user_id BIGINT NOT NULL COMMENT '上传用户ID',
 mime VARCHAR(32) NOT NULL COMMENT '图片MIME类型，如image/jpeg',
 width INT NOT NULL COMMENT '图片像素宽度',
 height INT NOT NULL COMMENT '图片像素高度',
 byte_size INT NOT NULL COMMENT '文件字节数，用于统计用户附件配额',
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件SHA-256十六进制摘要，用于去重与完整性校验',
 created_at BIGINT NOT NULL COMMENT '上传时间（毫秒时间戳）',
 PRIMARY KEY (id),
 -- 按会话、用户和时间统计附件用量
 KEY ix_attachment_owner (conversation_id,user_id,created_at),
 CONSTRAINT fk_attachment_conversation FOREIGN KEY (conversation_id) REFERENCES im_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM私有图片附件元数据表';

-- 早期安装补齐巡检标记和租约角色；已有角色不重新覆盖。
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'im_conversation' AND column_name = 'sync_required'), 'ALTER TABLE im_conversation ADD COLUMN sync_required TINYINT NOT NULL DEFAULT 1 COMMENT ''是否需要同步上游频道成员：0-否，1-是''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'im_conversation' AND index_name = 'ix_im_conversation_sync'), 'ALTER TABLE im_conversation ADD KEY ix_im_conversation_sync (sync_required)', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'im_lease' AND column_name = 'sender_role'), 'ALTER TABLE im_lease ADD COLUMN sender_role VARCHAR(16) NOT NULL DEFAULT ''customer'' COMMENT ''发言角色：customer-客户，agent-客服''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

-- 已有 IM 表补齐消息重试与租约巡检索引。
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'im_send' AND index_name = 'ix_im_send_system_pending'), 'ALTER TABLE im_send ADD KEY ix_im_send_system_pending (user_id,state,created_at)', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'im_lease' AND index_name = 'ix_im_lease_state_expiry'), 'ALTER TABLE im_lease ADD KEY ix_im_lease_state_expiry (state,expires_at)', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

