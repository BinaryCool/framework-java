-- Analytics 日志主库 SQL（MySQL 8.0.16+）：连接日志主库后整份执行，可重复执行。
-- 只包含行为事件表、事件 ID 精确幂等升级和门店接收时间索引，不依赖业务主库表。
-- 保留历史事件；不修改业务台账或菜单，不需要存储过程权限。
SET NAMES utf8mb4;

-- 日志主库：行为事件暂存表，事件 ID 使用二进制排序规则保证精确幂等。

CREATE TABLE IF NOT EXISTS `analytics_event` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `event_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '事件ID，精确匹配的幂等键，重试复用同一值',
  `event_name` varchar(64) NOT NULL COMMENT '事件名，白名单校验',
  `schema_version` int(11) NOT NULL DEFAULT 1 COMMENT '事件协议版本',
  `occurred_at` bigint(20) NOT NULL COMMENT '客户端发生时刻，UTC毫秒',
  `received_at` bigint(20) NOT NULL COMMENT '服务端接收时刻，UTC毫秒',
  `anonymous_id` varchar(64) NOT NULL DEFAULT '' COMMENT '安装级匿名标识',
  `user_id` varchar(64) NOT NULL DEFAULT '' COMMENT '服务端派生的用户ID，未登录为空',
  `session_id` varchar(64) NOT NULL DEFAULT '' COMMENT '会话标识',
  `page_instance_id` varchar(64) NOT NULL DEFAULT '' COMMENT '页面实例标识，用于曝光去重',
  `platform` varchar(32) NOT NULL DEFAULT '' COMMENT 'h5/android/ios/mp-weixin',
  `app_version` varchar(64) NOT NULL DEFAULT '' COMMENT '应用版本',
  `store_id` varchar(64) NOT NULL DEFAULT '' COMMENT '服务端派生的门店',
  `properties` text COMMENT '事件私有字段，JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_received_at` (`received_at`),
  KEY `idx_name_occurred` (`event_name`, `occurred_at`),
  KEY `idx_user_occurred` (`user_id`, `occurred_at`),
  KEY `idx_store_received` (`store_id`, `received_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行为埋点事件暂存表';

SET @analytics_sql = IF(EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'analytics_event' AND column_name = 'event_id' AND (collation_name <> 'utf8mb4_bin' OR column_comment <> '事件ID，精确匹配的幂等键，重试复用同一值')),
'ALTER TABLE analytics_event MODIFY COLUMN event_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT ''事件ID，精确匹配的幂等键，重试复用同一值''',
'DO 0');
PREPARE analytics_statement FROM @analytics_sql;
EXECUTE analytics_statement;
DEALLOCATE PREPARE analytics_statement;

SET @analytics_sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'analytics_event' GROUP BY index_name HAVING SUM(sub_part IS NOT NULL) = 0 AND (GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'store_id,received_at' OR CONCAT(GROUP_CONCAT(column_name ORDER BY seq_in_index), ',') LIKE 'store_id,received_at,id,%')), IF(EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'analytics_event' AND index_name = 'idx_store_received'), 'ALTER TABLE analytics_event DROP INDEX idx_store_received, ADD INDEX idx_store_received (store_id,received_at,id)', 'ALTER TABLE analytics_event ADD INDEX idx_store_received (store_id,received_at,id)'), 'DO 0');
PREPARE analytics_statement FROM @analytics_sql;
EXECUTE analytics_statement;
DEALLOCATE PREPARE analytics_statement;

SELECT DATABASE() AS database_name, 'log' AS target,
    '日志库建表、事件ID排序规则及门店索引已更新' AS result;
