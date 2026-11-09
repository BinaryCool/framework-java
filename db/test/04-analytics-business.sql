-- Analytics 业务主库 SQL（MySQL 8.0.16+）：连接业务主库后整份执行，可重复执行。
-- 包含付款状态台账、扫描游标、订单索引及后台菜单，不创建日志库 analytics_event 表。
-- 无需修改选项或存储过程权限；保留台账、扫描游标和历史业务数据。
SET NAMES utf8mb4;

-- 一、业务主库：付款状态观察台账和扫描游标，保留已有采集进度，不初始化或重置业务游标。
-- 前提是已有商城的 dodo_order_info、dodo_menu、dodo_role、dodo_role_menu_ref 表。

CREATE TABLE IF NOT EXISTS dodo_analytics_outbox (
  event_id varchar(64) NOT NULL COMMENT '事件唯一标识，付款状态事件为 srv_order_paid_<订单ID>，用于幂等去重',
  user_id varchar(64) NOT NULL COMMENT '下单用户ID',
  store_id varchar(64) NOT NULL COMMENT '订单所属门店ID',
  occurred_at bigint NOT NULL COMMENT '服务端观察到订单已付款状态的时刻，UTC毫秒时间戳，非实际付款时间',
  properties text NOT NULL COMMENT '事件扩展属性，JSON格式，包含订单信息和观察来源',
  attempts int NOT NULL DEFAULT 0 COMMENT '累计投递失败次数，用于计算重试退避时间',
  next_attempt_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次允许投递的时间，精确到毫秒',
  delivered_at datetime(3) NULL COMMENT '成功投递到日志库的时间，精确到毫秒，空值表示尚未确认成功',
  last_error varchar(128) NULL COMMENT '最近一次投递失败的错误摘要，投递成功后清空',
  PRIMARY KEY(event_id),
  KEY idx_delivery(delivered_at,next_attempt_at),
  KEY idx_store(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单付款状态观察待投递台账';

CREATE TABLE IF NOT EXISTS dodo_analytics_payment_scan (
  task_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扫描任务标识：order_paid_recent近期扫描，order_paid_reconcile补偿扫描',
  started_at bigint NOT NULL COMMENT '首次初始化的采集起点，UTC毫秒时间戳，扫描不早于此时刻',
  checkpoint_at bigint NOT NULL COMMENT '最近完成窗口的截止时刻，UTC毫秒时间戳，初始值为采集起点',
  window_from bigint NOT NULL DEFAULT 0 COMMENT '当前扫描窗口起始时刻，UTC毫秒时间戳，0表示无进行中窗口',
  window_to bigint NOT NULL DEFAULT 0 COMMENT '当前扫描窗口截止时刻，UTC毫秒时间戳，0表示无进行中窗口',
  cursor_time bigint NOT NULL DEFAULT 0 COMMENT '窗口内分页游标时间，取订单更新时间或无更新时间时的创建时间，UTC毫秒时间戳',
  cursor_order_id bigint unsigned NOT NULL DEFAULT 0 COMMENT '窗口内分页游标订单ID，与游标时间组成联合游标，0表示初始或已完成',
  PRIMARY KEY(task_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台付款状态采集游标';

-- 已有业务表同步中文注释，字段定义沿用原脚本，保留全部台账和游标数据。

ALTER TABLE dodo_analytics_outbox
  COMMENT = '订单付款状态观察待投递台账',
  MODIFY COLUMN event_id varchar(64) NOT NULL COMMENT '事件唯一标识，付款状态事件为 srv_order_paid_<订单ID>，用于幂等去重',
  MODIFY COLUMN user_id varchar(64) NOT NULL COMMENT '下单用户ID',
  MODIFY COLUMN store_id varchar(64) NOT NULL COMMENT '订单所属门店ID',
  MODIFY COLUMN occurred_at bigint NOT NULL COMMENT '服务端观察到订单已付款状态的时刻，UTC毫秒时间戳，非实际付款时间',
  MODIFY COLUMN properties text NOT NULL COMMENT '事件扩展属性，JSON格式，包含订单信息和观察来源',
  MODIFY COLUMN attempts int NOT NULL DEFAULT 0 COMMENT '累计投递失败次数，用于计算重试退避时间',
  MODIFY COLUMN next_attempt_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次允许投递的时间，精确到毫秒',
  MODIFY COLUMN delivered_at datetime(3) NULL COMMENT '成功投递到日志库的时间，精确到毫秒，空值表示尚未确认成功',
  MODIFY COLUMN last_error varchar(128) NULL COMMENT '最近一次投递失败的错误摘要，投递成功后清空';

ALTER TABLE dodo_analytics_payment_scan
  COMMENT = '后台付款状态采集游标',
  MODIFY COLUMN task_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扫描任务标识：order_paid_recent近期扫描，order_paid_reconcile补偿扫描',
  MODIFY COLUMN started_at bigint NOT NULL COMMENT '首次初始化的采集起点，UTC毫秒时间戳，扫描不早于此时刻',
  MODIFY COLUMN checkpoint_at bigint NOT NULL COMMENT '最近完成窗口的截止时刻，UTC毫秒时间戳，初始值为采集起点',
  MODIFY COLUMN window_from bigint NOT NULL DEFAULT 0 COMMENT '当前扫描窗口起始时刻，UTC毫秒时间戳，0表示无进行中窗口',
  MODIFY COLUMN window_to bigint NOT NULL DEFAULT 0 COMMENT '当前扫描窗口截止时刻，UTC毫秒时间戳，0表示无进行中窗口',
  MODIFY COLUMN cursor_time bigint NOT NULL DEFAULT 0 COMMENT '窗口内分页游标时间，取订单更新时间或无更新时间时的创建时间，UTC毫秒时间戳',
  MODIFY COLUMN cursor_order_id bigint unsigned NOT NULL DEFAULT 0 COMMENT '窗口内分页游标订单ID，与游标时间组成联合游标，0表示初始或已完成';

-- 补齐两个扫描分支的时间索引；已有同名或等价索引不重复创建。

SET @analytics_sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dodo_order_info' GROUP BY index_name HAVING SUM(sub_part IS NOT NULL) = 0 AND (GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'update_time' OR CONCAT(GROUP_CONCAT(column_name ORDER BY seq_in_index), ',') LIKE 'update_time,order_id,%')), IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dodo_order_info' AND index_name = 'idx_analytics_order_updated'), 'ALTER TABLE dodo_order_info DROP INDEX idx_analytics_order_updated, ADD INDEX idx_analytics_order_updated (update_time,order_id)', 'ALTER TABLE dodo_order_info ADD INDEX idx_analytics_order_updated (update_time,order_id)'), 'DO 0');
PREPARE analytics_statement FROM @analytics_sql;
EXECUTE analytics_statement;
DEALLOCATE PREPARE analytics_statement;

SET @analytics_sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dodo_order_info' GROUP BY index_name HAVING SUM(sub_part IS NOT NULL) = 0 AND (GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'create_time' OR CONCAT(GROUP_CONCAT(column_name ORDER BY seq_in_index), ',') LIKE 'create_time,order_id,%')), IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'dodo_order_info' AND index_name = 'idx_analytics_order_created'), 'ALTER TABLE dodo_order_info DROP INDEX idx_analytics_order_created, ADD INDEX idx_analytics_order_created (create_time,order_id)', 'ALTER TABLE dodo_order_info ADD INDEX idx_analytics_order_created (create_time,order_id)'), 'DO 0');
PREPARE analytics_statement FROM @analytics_sql;
EXECUTE analytics_statement;
DEALLOCATE PREPARE analytics_statement;

-- 二、业务主库：补齐报表中心 / 日志分析 / 行为事件明细及 admin 角色授权。
-- 按名称及 URL 复用旧菜单 ID；已有页面不搬动，角色授权沿页面实际父链补齐。
START TRANSACTION;

SET @analytics_report_root = (SELECT id FROM dodo_menu WHERE name = '报表中心' AND level = 1 ORDER BY id LIMIT 1);

INSERT INTO dodo_menu(name,parent_id,level,order_index,icon,status) SELECT '报表中心',NULL,1,2.9,'el-icon-data-analysis',1 WHERE @analytics_report_root IS NULL;

SET @analytics_report_root = (SELECT id FROM dodo_menu WHERE name = '报表中心' AND level = 1 ORDER BY id LIMIT 1);

UPDATE dodo_menu SET status=1 WHERE id=@analytics_report_root AND (status IS NULL OR status<>1);

SET @analytics_parent = (SELECT id FROM dodo_menu WHERE name='日志分析' AND level=2 AND parent_id=@analytics_report_root ORDER BY id LIMIT 1);

INSERT INTO dodo_menu(name,parent_id,level,order_index,status) SELECT '日志分析',@analytics_report_root,2,90,1 WHERE @analytics_parent IS NULL;

SET @analytics_parent = (SELECT id FROM dodo_menu WHERE name='日志分析' AND level=2 AND parent_id=@analytics_report_root ORDER BY id LIMIT 1);

UPDATE dodo_menu SET status=1 WHERE id=@analytics_parent AND (status IS NULL OR status<>1);

INSERT INTO dodo_menu(name,url,parent_id,level,order_index,status)
SELECT '行为事件明细','/log/AnalyticsEvents',@analytics_parent,3,99,1
WHERE @analytics_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dodo_menu WHERE url='/log/AnalyticsEvents');

UPDATE dodo_menu SET status=1 WHERE url='/log/AnalyticsEvents' AND (status IS NULL OR status<>1);

INSERT INTO dodo_role_menu_ref(role_id,menu_id,status)
SELECT r.role_id,m.id,1
FROM dodo_role r
JOIN dodo_menu page ON page.url='/log/AnalyticsEvents' AND page.status=1
LEFT JOIN dodo_menu parent ON parent.id=page.parent_id AND parent.status=1
JOIN dodo_menu m ON m.id IN(page.id,COALESCE(parent.id,0),COALESCE(parent.parent_id,0)) AND m.status=1
WHERE r.role_code='admin' AND r.status=1
AND NOT EXISTS(SELECT 1 FROM dodo_role_menu_ref ref WHERE ref.role_id=r.role_id AND ref.menu_id=m.id);

COMMIT;

-- 执行结果。
SELECT DATABASE() AS database_name, 'business' AS target,
    '业务库建表、注释、索引及菜单已更新' AS result;
