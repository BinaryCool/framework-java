-- 测试环境：连接已有业务主库后整份执行。MySQL 8.0.16+。
-- 包含晒单、补货、问卷及旧表必要更新；保留已有业务数据。
-- 不建整套商城基础表，复用已有 store、dodo_operate_log 等表。
SET NAMES utf8mb4;

-- 一、晒单活动、提交及操作日志。
CREATE TABLE IF NOT EXISTS dodo_show_activity (
 id          BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
 store_id    BIGINT   NOT NULL COMMENT '门店ID（多租户隔离）',
 sort_order  INT      NOT NULL DEFAULT 1 COMMENT '列表排序值，升序排列',
 start_time  BIGINT   NOT NULL COMMENT '活动生效时间（毫秒时间戳）',
 end_time    BIGINT   NULL COMMENT '活动截止时间（毫秒时间戳），长期活动为空',
 ended       INT      NOT NULL DEFAULT 0 COMMENT '是否已手动结束：0-否，1-是',
 version     INT      NOT NULL COMMENT '配置版本号，编辑时校验，防止旧表单覆盖新配置',
 payload     LONGTEXT NOT NULL COMMENT '活动配置快照（JSON：名称、封面、规则图、规则、券面额、门槛、有效期、参与规则等）',
 create_time BIGINT   NOT NULL COMMENT '创建时间（毫秒时间戳）',
 update_time BIGINT   NOT NULL COMMENT '更新时间（毫秒时间戳）',
 -- 按门店筛选未结束活动并按排序值分页
 KEY idx_show_activity_catalog (store_id, ended, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晒单活动配置表';

CREATE TABLE IF NOT EXISTS dodo_show_submission (
 id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
 activity_id    BIGINT        NOT NULL COMMENT '所属晒单活动ID（dodo_show_activity.id）',
 store_id       BIGINT        NOT NULL COMMENT '门店ID（多租户隔离）',
 user_id        BIGINT        NOT NULL COMMENT '提交用户ID',
 nick_name      VARCHAR(255)  NOT NULL COMMENT '提交时的用户昵称快照',
 avatar_url     VARCHAR(2048) NULL COMMENT '提交时的用户头像地址快照',
 order_sn       VARCHAR(100)  NOT NULL COMMENT '关联订单号；未关联时为空串',
 order_snapshot LONGTEXT      NOT NULL COMMENT '订单快照（JSON：订单金额、付款金额、商品明细等）；未关联时为{}',
 images         TEXT          NOT NULL COMMENT '晒单图片地址列表（JSON数组，1～3张）',
 audit_status   VARCHAR(16)   NOT NULL COMMENT '审核状态：PENDING-待审核，APPROVED-已通过，REJECTED-已驳回',
 note           VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '审核回复：通过时的说明或驳回原因，驳回时必填',
 coupon_id      BIGINT        NULL COMMENT '审核通过后发放的优惠券ID（dodo_coupon.id）',
 reward_price   DECIMAL(10,2) NULL COMMENT '审核通过时发放的奖励金额',
 reward_version INT           NULL COMMENT '发奖时使用的活动配置版本号',
 request_key    VARCHAR(64)   NOT NULL COMMENT '客户端提交凭证，用于失败重试的幂等去重',
 attempts       INT           NOT NULL DEFAULT 1 COMMENT '累计提交次数（含驳回后重新提交）',
 version        INT           NOT NULL DEFAULT 1 COMMENT '记录版本号，驳回重提及审核后递增，用于并发校验',
 create_time    BIGINT        NOT NULL COMMENT '首次提交时间（毫秒时间戳）',
 update_time    BIGINT        NOT NULL COMMENT '最近提交或审核时间（毫秒时间戳）',
 -- 同一用户的提交凭证唯一，保证重试幂等
 UNIQUE KEY uk_show_submission_request (user_id, request_key),
 -- 一张优惠券只能对应一条晒单记录，防止重复发券
 UNIQUE KEY uk_show_submission_coupon (coupon_id),
 -- 参与名额与资格校验
 KEY idx_show_submission_eligibility (activity_id, user_id, order_sn),
 -- 后台及客户端的列表分页检索
 KEY idx_show_submission_list (store_id, user_id, audit_status, update_time),
 -- 外键：晒单记录必须归属已存在的活动
 CONSTRAINT fk_show_submission_activity FOREIGN KEY (activity_id) REFERENCES dodo_show_activity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晒单提交与审核记录表';

CREATE TABLE IF NOT EXISTS dodo_show_activity_log (
 id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
 activity_id   BIGINT      NOT NULL COMMENT '所属晒单活动ID',
 submission_id BIGINT      NULL COMMENT '关联晒单记录ID，活动级操作（创建、编辑、结束）为空',
 actor_id      BIGINT      NOT NULL COMMENT '操作人用户ID',
 action        VARCHAR(24) NOT NULL COMMENT '操作类型：CREATE-创建，EDIT-编辑，END-结束，SUBMIT-提交，RESUBMIT-驳回重提，APPROVE-审核通过，REJECT-审核驳回',
 detail        LONGTEXT    NOT NULL COMMENT '操作详情快照（JSON）',
 create_time   BIGINT      NOT NULL COMMENT '操作时间（毫秒时间戳）',
 -- 按活动倒序查看操作日志
 KEY idx_show_log_activity (activity_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晒单活动操作日志表';

-- 二、补货订阅、通知和供货处理。
CREATE TABLE IF NOT EXISTS dodo_restock_subscription (
    id VARCHAR(32) NOT NULL COMMENT '订阅唯一标识',
    user_id BIGINT NOT NULL COMMENT '订阅用户标识',
    store_id BIGINT NOT NULL COMMENT '所属租户门店标识',
    shop_id BIGINT NOT NULL COMMENT '用户选购店铺标识',
    scheme_id BIGINT NOT NULL COMMENT '订阅时的门店方案标识',
    goods_id BIGINT NOT NULL COMMENT '订阅商品标识',
    goods_name VARCHAR(255) NOT NULL COMMENT '订阅时的商品名称快照',
    goods_img VARCHAR(1024) NOT NULL COMMENT '订阅时的商品图片快照',
    state VARCHAR(24) NOT NULL COMMENT '订阅状态：WAITING等待、NOTIFIED已通知、CANCELLED取消、EXPIRED过期、FULFILLED已购买、UNAVAILABLE不可供货',
    generation INT NOT NULL DEFAULT 1 COMMENT '订阅轮次；终态后主动重新订阅递增，隔离旧通知',
    version INT NOT NULL DEFAULT 1 COMMENT '并发操作版本号',
    subscribed_at DATETIME(3) NOT NULL COMMENT '本轮主动订阅时间',
    expires_at DATETIME(3) NOT NULL COMMENT '本轮订阅失效时间',
    next_check_at DATETIME(3) NOT NULL COMMENT '下次可售状态核查时间',
    updated_at DATETIME(3) NOT NULL COMMENT '订阅最近更新时间',
    PRIMARY KEY (id),
    -- 同一用户同店同商品仅保留一个当前订阅。
    UNIQUE KEY uk_restock_subscription (user_id, store_id, shop_id, goods_id),
    -- 按状态和到期时间轮转核查。
    KEY ix_restock_check (state, next_check_at, id),
    -- 用户当前店铺订阅列表。
    KEY ix_restock_mine (user_id, store_id, shop_id, updated_at, id),
    -- 汇总同店同商品有效等待需求。
    KEY ix_restock_demand (store_id, shop_id, goods_id, state, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品到货提醒订阅及当前轮次状态';

CREATE TABLE IF NOT EXISTS dodo_restock_notice (
    id VARCHAR(32) NOT NULL COMMENT '逻辑通知唯一标识，也是客户端去重标识',
    subscription_id VARCHAR(32) NOT NULL COMMENT '关联订阅标识',
    generation INT NOT NULL COMMENT '生成通知时的订阅轮次',
    kind VARCHAR(24) NOT NULL COMMENT '通知类型：ARRIVAL到货、UNAVAILABLE停止供货',
    user_id BIGINT NOT NULL COMMENT '接收用户标识',
    store_id BIGINT NOT NULL COMMENT '所属租户门店标识',
    shop_id BIGINT NOT NULL COMMENT '通知对应选购店铺标识',
    goods_id BIGINT NOT NULL COMMENT '通知对应商品标识',
    title VARCHAR(100) NOT NULL COMMENT '站内及推送通知标题',
    content VARCHAR(500) NOT NULL COMMENT '站内及推送通知内容',
    delivery_state VARCHAR(24) NOT NULL COMMENT '投递状态：PENDING待发、SENDING发送中、ACCEPTED渠道接受、REJECTED明确拒绝、UNKNOWN待核对、SKIPPED终止外发',
    request_id VARCHAR(64) NOT NULL COMMENT '固定渠道请求标识；所有允许的重试沿用该标识',
    target_client_id VARCHAR(255) NULL COMMENT '首次投递绑定的设备标识；重试不得更换设备',
    attempts INT NOT NULL DEFAULT 0 COMMENT '实际取得发送资格的次数',
    version INT NOT NULL DEFAULT 1 COMMENT '投递及人工核对并发版本号',
    next_attempt_at DATETIME(3) NOT NULL COMMENT '下次允许处理投递的时间',
    expires_at DATETIME(3) NOT NULL COMMENT '外部投递截止时间，过期仍保留站内结果',
    created_at DATETIME(3) NOT NULL COMMENT '通知生成时间',
    updated_at DATETIME(3) NOT NULL COMMENT '投递状态最近更新时间',
    read_at DATETIME(3) NULL COMMENT '用户首次阅读时间',
    clicked_at DATETIME(3) NULL COMMENT '用户首次点击商品入口时间',
    last_error VARCHAR(100) NULL COMMENT '最近一次投递失败或终止原因代码',
    PRIMARY KEY (id),
    -- 每轮订阅每种通知仅生成一次。
    UNIQUE KEY uk_restock_notice (subscription_id, generation, kind),
    -- 渠道请求标识不得复用于其他通知。
    UNIQUE KEY uk_restock_request (request_id),
    -- 按状态和执行时间扫描投递任务。
    KEY ix_restock_delivery (delivery_state, next_attempt_at, id),
    -- 用户门店范围内的站内通知列表。
    KEY ix_restock_inbox (user_id, store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到货提醒站内通知及持久投递台账';

CREATE TABLE IF NOT EXISTS dodo_restock_supply (
    store_id BIGINT NOT NULL COMMENT '所属租户门店标识',
    shop_id BIGINT NOT NULL COMMENT '需求对应选购店铺标识',
    goods_id BIGINT NOT NULL COMMENT '需求对应商品标识',
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '供货处理状态：PENDING待处理、PROCESSING处理中、RESTORED可售已恢复、PAUSED暂缓、STOPPED停供',
    owner_id BIGINT NULL COMMENT '当前认领管理员标识',
    owner_name VARCHAR(100) NULL COMMENT '当前认领管理员名称快照',
    reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '供货处理原因；暂缓和停供时必填',
    expected_at DATETIME(3) NULL COMMENT '人工记录的预计到货时间，不作为已恢复可售的依据',
    version INT NOT NULL DEFAULT 1 COMMENT '供货处理并发版本号',
    updated_at DATETIME(3) NOT NULL COMMENT '供货处理最近更新时间',
    PRIMARY KEY (store_id, shop_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同店同商品汇总供货处理状态及认领信息';

-- 复用操作日志，仅扩容原 VARCHAR，已有更大文本类型保持不变。
SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dodo_operate_log' AND column_name = 'operate_content' AND data_type = 'varchar'), 'ALTER TABLE dodo_operate_log MODIFY COLUMN operate_content TEXT NULL COMMENT ''操作内容；补货提醒保存动作、目标及完整核对凭据的结构化记录''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

-- 三、问卷完整建表。
CREATE TABLE IF NOT EXISTS dudu_survey (
 id VARCHAR(32) PRIMARY KEY COMMENT '问卷唯一标识',
 draft MEDIUMTEXT NOT NULL COMMENT '问卷草稿定义，JSON 格式，包含标题、说明、投放范围、收集时间及题目',
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '问卷状态：DRAFT 草稿，PUBLISHED 已发布，PAUSED 已暂停，ARCHIVED 已归档',
 revision INT NOT NULL DEFAULT 1 COMMENT '修订号，用于乐观锁并发校验，保存、发布及状态变更时递增',
 active_version INT NOT NULL DEFAULT 0 COMMENT '当前发布版本号，0 表示尚未发布',
 created_by BIGINT NOT NULL COMMENT '创建管理员标识',
 updated_by BIGINT NOT NULL COMMENT '最近修改管理员标识',
 created_at BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒时间戳',
 updated_at BIGINT NOT NULL COMMENT '最近更新时间，Unix 毫秒时间戳'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局问卷主表，保存草稿、状态及当前发布版本';

CREATE TABLE IF NOT EXISTS dudu_survey_version (
 survey_id VARCHAR(32) NOT NULL COMMENT '所属问卷标识，对应问卷主表的 id',
 version INT NOT NULL COMMENT '发布版本号，同一问卷内从 1 开始递增',
 definition MEDIUMTEXT NOT NULL COMMENT '发布时的问卷定义快照，JSON 格式，发布后不可修改',
 published_by BIGINT NOT NULL COMMENT '发布管理员标识',
 published_at BIGINT NOT NULL COMMENT '发布时间，Unix 毫秒时间戳',
 PRIMARY KEY(survey_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷发布版本表，保留历史定义供答卷及统计使用';

CREATE TABLE IF NOT EXISTS dudu_survey_ticket (
 token_hash CHAR(64) PRIMARY KEY COMMENT '短期答题凭证的 SHA-256 十六进制摘要，不保存凭证明文',
 survey_id VARCHAR(32) NOT NULL COMMENT '凭证绑定的问卷标识',
 version INT NOT NULL COMMENT '凭证绑定的发布版本号',
 user_id BIGINT NOT NULL COMMENT '凭证绑定的登录用户标识，0 表示游客',
 guest_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '游客浏览器标识，登录用户为空字符串，不作为登录凭据',
 store_id BIGINT NOT NULL COMMENT '凭证签发时的当前门店标识',
 expires_at BIGINT NOT NULL COMMENT '凭证过期时间，Unix 毫秒时间戳',
 KEY idx_ticket_expiry(expires_at), KEY idx_ticket_user(user_id,survey_id,guest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷短期答题凭证表，绑定填写身份、门店及发布版本';

CREATE TABLE IF NOT EXISTS dudu_survey_response (
 id VARCHAR(32) PRIMARY KEY COMMENT '答卷唯一标识',
 survey_id VARCHAR(32) NOT NULL COMMENT '所属问卷标识',
 version INT NOT NULL COMMENT '提交答卷时对应的问卷发布版本号',
 user_id BIGINT NOT NULL COMMENT '答卷提交用户标识，0 表示游客，登录身份取自服务端会话',
 guest_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '游客浏览器标识，登录用户为空字符串，同一浏览器对同一问卷仅可提交一次',
 store_id BIGINT NOT NULL COMMENT '答卷提交门店标识，取自答题凭证',
 answers MEDIUMTEXT NOT NULL COMMENT '经服务端校验的答案，JSON 格式，以题目标识为键',
 submitted_at BIGINT NOT NULL COMMENT '答卷提交时间，Unix 毫秒时间戳',
 UNIQUE KEY uk_survey_user(survey_id,user_id,guest_id),
 KEY idx_response_page(survey_id,version,submitted_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷答卷表，登录用户或游客浏览器跨发布版本仅保留一份答卷';

-- 兼容旧版问卷：补游客字段及去重索引，不清空历史答卷。
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_ticket' AND column_name = 'guest_id'), 'ALTER TABLE dudu_survey_ticket ADD COLUMN guest_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''游客浏览器标识，登录用户为空字符串，不作为登录凭据'' AFTER user_id', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_ticket' AND index_name = 'idx_ticket_user') AND COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_ticket' AND index_name = 'idx_ticket_user'), '') <> 'user_id,survey_id,guest_id', 'ALTER TABLE dudu_survey_ticket DROP INDEX idx_ticket_user', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_ticket' AND index_name = 'idx_ticket_user'), 'ALTER TABLE dudu_survey_ticket ADD KEY idx_ticket_user (user_id,survey_id,guest_id)', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_response' AND column_name = 'guest_id'), 'ALTER TABLE dudu_survey_response ADD COLUMN guest_id VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''游客浏览器标识，登录用户为空字符串，同一浏览器对同一问卷仅可提交一次'' AFTER user_id', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_response' AND index_name = 'uk_survey_user') AND COALESCE((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_response' AND index_name = 'uk_survey_user'), '') <> 'survey_id,user_id,guest_id', 'ALTER TABLE dudu_survey_response DROP INDEX uk_survey_user', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dudu_survey_response' AND index_name = 'uk_survey_user'), 'ALTER TABLE dudu_survey_response ADD UNIQUE KEY uk_survey_user (survey_id,user_id,guest_id)', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

ALTER TABLE dudu_survey
 MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '问卷唯一标识',
 MODIFY COLUMN draft MEDIUMTEXT NOT NULL COMMENT '问卷草稿定义，JSON 格式，包含标题、说明、投放范围、收集时间及题目',
 MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '问卷状态：DRAFT 草稿，PUBLISHED 已发布，PAUSED 已暂停，ARCHIVED 已归档',
 MODIFY COLUMN revision INT NOT NULL DEFAULT 1 COMMENT '修订号，用于乐观锁并发校验，保存、发布及状态变更时递增',
 MODIFY COLUMN active_version INT NOT NULL DEFAULT 0 COMMENT '当前发布版本号，0 表示尚未发布',
 MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建管理员标识',
 MODIFY COLUMN updated_by BIGINT NOT NULL COMMENT '最近修改管理员标识',
 MODIFY COLUMN created_at BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒时间戳',
 MODIFY COLUMN updated_at BIGINT NOT NULL COMMENT '最近更新时间，Unix 毫秒时间戳',
 COMMENT='全局问卷主表，保存草稿、状态及当前发布版本';

ALTER TABLE dudu_survey_version
 MODIFY COLUMN survey_id VARCHAR(32) NOT NULL COMMENT '所属问卷标识，对应问卷主表的 id',
 MODIFY COLUMN version INT NOT NULL COMMENT '发布版本号，同一问卷内从 1 开始递增',
 MODIFY COLUMN definition MEDIUMTEXT NOT NULL COMMENT '发布时的问卷定义快照，JSON 格式，发布后不可修改',
 MODIFY COLUMN published_by BIGINT NOT NULL COMMENT '发布管理员标识',
 MODIFY COLUMN published_at BIGINT NOT NULL COMMENT '发布时间，Unix 毫秒时间戳',
 COMMENT='问卷发布版本表，保留历史定义供答卷及统计使用';

ALTER TABLE dudu_survey_ticket
 MODIFY COLUMN token_hash CHAR(64) NOT NULL COMMENT '短期答题凭证的 SHA-256 十六进制摘要，不保存凭证明文',
 MODIFY COLUMN survey_id VARCHAR(32) NOT NULL COMMENT '凭证绑定的问卷标识',
 MODIFY COLUMN version INT NOT NULL COMMENT '凭证绑定的发布版本号',
 MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '凭证绑定的登录用户标识，0 表示游客',
 MODIFY COLUMN guest_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '游客浏览器标识，登录用户为空字符串，不作为登录凭据',
 MODIFY COLUMN store_id BIGINT NOT NULL COMMENT '凭证签发时的当前门店标识',
 MODIFY COLUMN expires_at BIGINT NOT NULL COMMENT '凭证过期时间，Unix 毫秒时间戳',
 COMMENT='问卷短期答题凭证表，绑定填写身份、门店及发布版本';

ALTER TABLE dudu_survey_response
 MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '答卷唯一标识',
 MODIFY COLUMN survey_id VARCHAR(32) NOT NULL COMMENT '所属问卷标识',
 MODIFY COLUMN version INT NOT NULL COMMENT '提交答卷时对应的问卷发布版本号',
 MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '答卷提交用户标识，0 表示游客，登录身份取自服务端会话',
 MODIFY COLUMN guest_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '游客浏览器标识，登录用户为空字符串，同一浏览器对同一问卷仅可提交一次',
 MODIFY COLUMN store_id BIGINT NOT NULL COMMENT '答卷提交门店标识，取自答题凭证',
 MODIFY COLUMN answers MEDIUMTEXT NOT NULL COMMENT '经服务端校验的答案，JSON 格式，以题目标识为键',
 MODIFY COLUMN submitted_at BIGINT NOT NULL COMMENT '答卷提交时间，Unix 毫秒时间戳',
 COMMENT='问卷答卷表，登录用户或游客浏览器跨发布版本仅保留一份答卷';

-- 榜单完整建表及升级统一在同目录 05-rank.sql 执行。
