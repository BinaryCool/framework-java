-- 榜单统一 SQL：MySQL 8.0.16+，连接已有业务主库后整份执行。
-- 默认 migrate：完整五张榜单表、旧字段升级、预检及首次数据初始化；可重复执行。
-- check：返回阻断问题和逐条诊断；preview：预览排序；status：查看结构。
-- check/preview/status 不改业务表；本文件会临时创建过程，需 CREATE ROUTINE 权限。
-- DDL 隐式提交；旧环境请在业务停写窗口备份后执行，遇错停止，禁止忽略错误继续。
-- 包含系统管理 / 主题馆 / 榜单列表菜单及原有三个管理角色的授权。不包含模拟数据。
SET @rank_action = 'migrate';
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS codex_rank_apply;
DELIMITER $$
CREATE PROCEDURE codex_rank_apply()
apply_rank: BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF @rank_action = 'status' THEN
-- BEGIN RANK_STATUS
SELECT DATABASE() AS current_database;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('store', 'dodo_theme_top_list', 'dodo_theme_top_goods_ref', 'dodo_goods',
                     'dodo_rank_config', 'dodo_rank_tag', 'dodo_rank_tag_board')
ORDER BY table_name;

SELECT table_name, column_name, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'dodo_theme_top_list'
  AND column_name IN ('association_priority', 'recommend_position')
ORDER BY ordinal_position;
-- END RANK_STATUS
        LEAVE apply_rank;
    ELSEIF @rank_action = 'check' THEN
-- BEGIN RANK_PREFLIGHT
SELECT 'NULL_OR_DUPLICATE_GOODS' AS issue, r.store_id, r.parent_id AS board_id
FROM dodo_theme_top_goods_ref r WHERE r.status = 1
GROUP BY r.store_id, r.parent_id
HAVING COUNT(*) != COUNT(r.goods_id) OR COUNT(*) != COUNT(DISTINCT r.goods_id)
    OR COUNT(*) != COUNT(r.goods_sn) OR COUNT(*) != COUNT(DISTINCT LOWER(TRIM(r.goods_sn)))
    OR COUNT(*) != COUNT(r.sort_order)
    OR SUM(CASE WHEN TRIM(r.goods_sn) = '' THEN 1 ELSE 0 END) > 0
UNION ALL
SELECT 'INVALID_GOODS_OR_OWNER', r.store_id, r.parent_id
FROM dodo_theme_top_goods_ref r
LEFT JOIN dodo_theme_top_list b ON b.id = r.parent_id AND b.store_id = r.store_id AND b.status = 1
LEFT JOIN dodo_goods g ON g.goods_id = r.goods_id AND g.store_id = r.store_id
    AND g.goods_sn = r.goods_sn
WHERE r.status = 1 AND (b.id IS NULL OR g.goods_id IS NULL)
UNION ALL
SELECT 'UNKNOWN_STORE_OR_EMPTY_PUBLISHED', b.store_id, b.id
FROM dodo_theme_top_list b LEFT JOIN store s ON s.id = b.store_id
WHERE b.status = 1 AND (s.id IS NULL OR (b.is_prod = 1 AND NOT EXISTS (
    SELECT 1 FROM dodo_theme_top_goods_ref r WHERE r.store_id = b.store_id AND r.parent_id = b.id AND r.status = 1)));
-- END RANK_PREFLIGHT
-- BEGIN RANK_DETAILS
WITH checked_refs AS (
    SELECT r.id AS ref_id, r.store_id, r.parent_id AS board_id,
           r.goods_id, r.goods_sn AS linked_goods_sn, r.goods_name AS linked_goods_name,
           r.sort_order,
           COUNT(*) OVER (PARTITION BY r.store_id, r.parent_id, r.goods_id) AS same_goods_count,
           COUNT(*) OVER (PARTITION BY r.store_id, r.parent_id, LOWER(TRIM(r.goods_sn))) AS same_sku_count,
           COUNT(*) OVER (PARTITION BY r.store_id, r.parent_id, r.sort_order) AS same_sort_count
    FROM dodo_theme_top_goods_ref r
    WHERE r.status = 1
), diagnosed AS (
    SELECT r.*, b.title AS board_title, b.is_prod AS board_is_prod, b.status AS board_status,
           b.store_id AS board_store_id,
           g.goods_sn AS current_goods_sn, g.goods_name AS current_goods_name,
           g.store_id AS current_goods_store_id, g.status AS goods_status, g.is_delete AS goods_is_delete,
           CONCAT_WS('; ',
               CASE WHEN r.goods_id IS NULL THEN 'MISSING_GOODS_ID:商品ID为空' END,
               CASE WHEN r.goods_id IS NOT NULL AND r.same_goods_count > 1 THEN 'DUPLICATE_GOODS_ID:同榜商品ID重复' END,
               CASE WHEN r.linked_goods_sn IS NULL OR TRIM(r.linked_goods_sn) = '' THEN 'MISSING_SKU:关系SKU为空' END,
               CASE WHEN r.linked_goods_sn IS NOT NULL AND r.same_sku_count > 1 THEN 'DUPLICATE_SKU:同榜SKU重复' END,
               CASE WHEN r.sort_order IS NULL THEN 'MISSING_SORT:商品排序为空' END,
               CASE WHEN r.sort_order IS NOT NULL AND r.same_sort_count > 1 THEN 'DUPLICATE_SORT:同榜商品排序重复' END,
               CASE WHEN b.id IS NULL THEN 'BOARD_NOT_FOUND:关联榜单不存在' END,
               CASE WHEN b.id IS NOT NULL AND NOT ((b.store_id = r.store_id) <=> TRUE) THEN 'BOARD_STORE_MISMATCH:关系与榜单门店不一致或为空' END,
               CASE WHEN b.id IS NOT NULL AND NOT (b.status <=> 1) THEN 'BOARD_INACTIVE:关联榜单已失效' END,
               CASE WHEN g.goods_id IS NULL THEN 'GOODS_NOT_FOUND:关联商品不存在' END,
               CASE WHEN g.goods_id IS NOT NULL AND NOT ((g.store_id = r.store_id) <=> TRUE) THEN 'GOODS_STORE_MISMATCH:关系与商品门店不一致或为空' END,
               CASE WHEN g.goods_id IS NOT NULL AND NOT ((g.goods_sn = r.linked_goods_sn) <=> TRUE) THEN 'GOODS_SKU_MISMATCH:关系SKU与当前商品SKU不匹配' END,
               CASE WHEN g.goods_id IS NOT NULL AND NOT (g.status <=> 1) THEN 'GOODS_INACTIVE:商品状态失效' END,
               CASE WHEN g.goods_id IS NOT NULL AND NOT (g.is_delete <=> 0) THEN 'GOODS_DELETED:商品已删除或删除标记异常' END
           ) AS reason
    FROM checked_refs r
    LEFT JOIN dodo_theme_top_list b ON b.id = r.board_id
    LEFT JOIN dodo_goods g ON g.goods_id = r.goods_id
)
SELECT store_id, board_id, board_title, board_is_prod, board_status,
       ref_id, goods_id, linked_goods_sn, current_goods_sn,
       linked_goods_name, current_goods_name, sort_order,
       same_goods_count, same_sku_count, same_sort_count,
       board_store_id, current_goods_store_id, goods_status, goods_is_delete,
       CASE WHEN reason REGEXP 'MISSING_|DUPLICATE_GOODS_ID|DUPLICATE_SKU|BOARD_NOT_FOUND|BOARD_STORE_MISMATCH|BOARD_INACTIVE|GOODS_NOT_FOUND|GOODS_STORE_MISMATCH|GOODS_SKU_MISMATCH'
            THEN 'BLOCK' ELSE 'INFO' END AS severity, reason
FROM diagnosed
WHERE reason <> ''
ORDER BY store_id, board_id, sort_order, ref_id;

-- 与迁移保护的第三类条件一致；不把零库存视为无效商品，也不自动下线榜单。
SELECT b.store_id, b.id AS board_id, b.title AS board_title, b.is_prod AS board_is_prod,
       CONCAT_WS('; ',
           CASE WHEN s.id IS NULL THEN 'STORE_NOT_FOUND:榜单所属门店不存在' END,
           CASE WHEN b.is_prod = 1 AND NOT EXISTS (
               SELECT 1 FROM dodo_theme_top_goods_ref r
               WHERE r.store_id = b.store_id AND r.parent_id = b.id AND r.status = 1
           ) THEN 'EMPTY_PUBLISHED_BOARD:已发布榜单没有有效商品关系' END
       ) AS reason
FROM dodo_theme_top_list b
LEFT JOIN store s ON s.id = b.store_id
WHERE b.status = 1
  AND (s.id IS NULL OR (b.is_prod = 1 AND NOT EXISTS (
      SELECT 1 FROM dodo_theme_top_goods_ref r
      WHERE r.store_id = b.store_id AND r.parent_id = b.id AND r.status = 1
  )))
ORDER BY b.store_id, b.id;
-- END RANK_DETAILS
        LEAVE apply_rank;
    ELSEIF @rank_action = 'preview' THEN
-- BEGIN RANK_PREVIEW
WITH ranked AS (
    SELECT id AS ref_id, store_id, parent_id AS board_id, goods_id, goods_sn,
           status AS ref_status, sort_order AS old_sort_order,
           ROW_NUMBER() OVER (PARTITION BY store_id, parent_id ORDER BY sort_order, id DESC) AS new_sort_order
    FROM dodo_theme_top_goods_ref
), conflicts AS (
    SELECT store_id, parent_id AS board_id
    FROM dodo_theme_top_goods_ref
    WHERE status = 1
    GROUP BY store_id, parent_id
    HAVING COUNT(*) > COUNT(DISTINCT sort_order)
)
SELECT r.store_id, r.board_id, b.title AS board_title,
       r.ref_id, r.goods_id, r.goods_sn, r.old_sort_order, r.new_sort_order, r.ref_status,
       NOT (r.old_sort_order <=> r.new_sort_order) AS will_change
FROM ranked r
JOIN conflicts c ON c.store_id = r.store_id AND c.board_id = r.board_id
LEFT JOIN dodo_theme_top_list b ON b.id = r.board_id AND b.store_id = r.store_id
ORDER BY r.store_id, r.board_id, r.new_sort_order;
-- END RANK_PREVIEW
        LEAVE apply_rank;
    ELSEIF NOT (@rank_action <=> 'migrate') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rank_action must be migrate/check/preview/status';
    END IF;

    -- 先检查既有历史关系；阻断失败时不修改任何业务表结构或数据。
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
               AND table_name='dodo_theme_top_list')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
                   AND table_name='dodo_theme_top_goods_ref') THEN
        IF EXISTS (SELECT 1 FROM (SELECT 'NULL_OR_DUPLICATE_GOODS' AS issue, r.store_id, r.parent_id AS board_id
FROM dodo_theme_top_goods_ref r WHERE r.status = 1
GROUP BY r.store_id, r.parent_id
HAVING COUNT(*) != COUNT(r.goods_id) OR COUNT(*) != COUNT(DISTINCT r.goods_id)
    OR COUNT(*) != COUNT(r.goods_sn) OR COUNT(*) != COUNT(DISTINCT LOWER(TRIM(r.goods_sn)))
    OR COUNT(*) != COUNT(r.sort_order)
    OR SUM(CASE WHEN TRIM(r.goods_sn) = '' THEN 1 ELSE 0 END) > 0
UNION ALL
SELECT 'INVALID_GOODS_OR_OWNER', r.store_id, r.parent_id
FROM dodo_theme_top_goods_ref r
LEFT JOIN dodo_theme_top_list b ON b.id = r.parent_id AND b.store_id = r.store_id AND b.status = 1
LEFT JOIN dodo_goods g ON g.goods_id = r.goods_id AND g.store_id = r.store_id
    AND g.goods_sn = r.goods_sn
WHERE r.status = 1 AND (b.id IS NULL OR g.goods_id IS NULL)
UNION ALL
SELECT 'UNKNOWN_STORE_OR_EMPTY_PUBLISHED', b.store_id, b.id
FROM dodo_theme_top_list b LEFT JOIN store s ON s.id = b.store_id
WHERE b.status = 1 AND (s.id IS NULL OR (b.is_prod = 1 AND NOT EXISTS (
    SELECT 1 FROM dodo_theme_top_goods_ref r WHERE r.store_id = b.store_id AND r.parent_id = b.id AND r.status = 1)))) rank_issues) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rank preflight failed; choose check and repair ALL blocking issues';
        END IF;
    END IF;
    -- 字段添加前记录首次迁移标记，重跑不得清空优先级、推荐位、版本或背景配置。
    SET @rank_initialize = NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='dodo_theme_top_list' AND column_name='association_priority');
-- BEGIN RANK_TABLES
CREATE TABLE IF NOT EXISTS `dodo_theme_top_list` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `store_id` int DEFAULT NULL COMMENT '门店ID',
  `store_name` varchar(20) DEFAULT NULL COMMENT '门店名称',
  `title` varchar(50) DEFAULT NULL COMMENT '标题',
  `sort_order` mediumint DEFAULT '50' COMMENT '排序',
  `is_prod` tinyint(1) DEFAULT '1' COMMENT '是否应用于生产',
  `top_url` varchar(200) DEFAULT NULL COMMENT '榜单详情顶部图片地址',
  `background_color` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '背景颜色',
  `background_image` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '背景图片',
  `goods_count` decimal(10,1) DEFAULT NULL COMMENT '商品数量',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` varchar(20) DEFAULT NULL COMMENT '创建人',
  `create_name` varchar(45) DEFAULT NULL COMMENT '创建人姓名',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by` varchar(20) DEFAULT NULL COMMENT '更新人',
  `update_name` varchar(45) DEFAULT NULL COMMENT '更新人姓名',
  `status` smallint DEFAULT '1' COMMENT '状态(0: 删除, 1: 正常)',
  `association_priority` int DEFAULT NULL COMMENT '商品关联榜单优先级，同门店已发布榜单唯一正整数',
  `recommend_position` int DEFAULT NULL COMMENT '首页推荐位置，仅已发布榜单可设为一至三，空值表示不推荐',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='榜单列表';

CREATE TABLE IF NOT EXISTS `dodo_theme_top_goods_ref` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `store_id` int DEFAULT NULL COMMENT '门店ID',
  `store_name` varchar(20) DEFAULT NULL COMMENT '门店名称',
  `parent_id` int DEFAULT NULL COMMENT '父ID',
  `goods_id` int DEFAULT NULL COMMENT '关联商品编号',
  `goods_sn` varchar(60) DEFAULT '' COMMENT '关联商品编码',
  `goods_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '关联商品名称快照',
  `promote_price` decimal(10,2) DEFAULT NULL COMMENT '促销价格',
  `limit_per_order` decimal(10,1) DEFAULT NULL COMMENT '每单限购',
  `sort_order` decimal(10,2) DEFAULT '0.00' COMMENT '排序',
  `create_by` varchar(10) DEFAULT NULL COMMENT '创建人账号',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(10) DEFAULT NULL COMMENT '更新人账号',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态',
  PRIMARY KEY (`id`),
  KEY `idx_parentid` (`parent_id` DESC),
  KEY `idx_goodsid` (`goods_id`),
  KEY `idx_goodssn_parentid` (`goods_sn`,`parent_id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='榜单和商品的关系';

CREATE TABLE IF NOT EXISTS dodo_rank_config (
    store_id BIGINT NOT NULL COMMENT '门店主键，每门店一条配置',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '配置乐观锁版本，每次成功修改递增',
    homepage_background_image VARCHAR(2048) NULL COMMENT '榜单首页背景图片地址，空值表示使用默认背景',
    PRIMARY KEY (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店榜单共享配置及并发控制';

CREATE TABLE IF NOT EXISTS dodo_rank_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '榜单标签主键',
    store_id BIGINT NOT NULL COMMENT '标签所属门店主键',
    name VARCHAR(100) NOT NULL COMMENT '榜单标签显示名称',
    sort_order INT NOT NULL COMMENT '标签显示顺序，按提交数组顺序生成',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '标签是否启用，一启用零停用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rank_tag_store_id (store_id, id),
    KEY idx_rank_tag_store_order (store_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店榜单分类标签';

CREATE TABLE IF NOT EXISTS dodo_rank_tag_board (
    store_id BIGINT NOT NULL COMMENT '标签与榜单共同所属门店主键',
    tag_id BIGINT NOT NULL COMMENT '榜单标签主键',
    board_id BIGINT NOT NULL COMMENT '现有主题榜单主键',
    PRIMARY KEY (store_id, tag_id, board_id),
    KEY idx_rank_tag_board_board (store_id, board_id),
    CONSTRAINT fk_rank_tag_board_tag FOREIGN KEY (store_id, tag_id) REFERENCES dodo_rank_tag (store_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同门店榜单标签与榜单关系';
-- END RANK_TABLES
-- 旧榜单补字段。
SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dodo_theme_top_list' AND column_name = 'association_priority'), 'ALTER TABLE dodo_theme_top_list ADD COLUMN association_priority INT NULL COMMENT ''商品关联榜单优先级，同门店已发布榜单唯一正整数''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dodo_theme_top_list' AND column_name = 'recommend_position'), 'ALTER TABLE dodo_theme_top_list ADD COLUMN recommend_position INT NULL COMMENT ''首页推荐位置，仅已发布榜单可设为一至三，空值表示不推荐''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;

SET @ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'dodo_rank_config' AND column_name = 'homepage_background_image'), 'ALTER TABLE dodo_rank_config ADD COLUMN homepage_background_image VARCHAR(2048) NULL COMMENT ''榜单首页背景图片地址，空值表示使用默认背景''', 'DO 0');
PREPARE ddl_statement FROM @ddl;
EXECUTE ddl_statement;
DEALLOCATE PREPARE ddl_statement;


START TRANSACTION;
-- 仅修复有重复排序的榜单，完整保留原 sort_order、id DESC 对应的先后顺序。
-- 旧接口会对包含失效关系的全集计算名次，因此冲突榜单的全部关系统一重排，保留旧名次。
-- 新接口仍先计算有效关系名次，再过滤不可展示商品；商品失效不会触发后续名次重排。
-- 聚合和窗口派生表在 MySQL 中物化；只更新排序，不删除关系、不更改 SKU 或商品状态。
UPDATE dodo_theme_top_goods_ref r
JOIN (
    SELECT id, store_id, parent_id,
           ROW_NUMBER() OVER (PARTITION BY store_id, parent_id ORDER BY sort_order, id DESC) AS new_sort_order
    FROM dodo_theme_top_goods_ref
) ranked ON ranked.id = r.id
JOIN (
    SELECT store_id, parent_id
    FROM dodo_theme_top_goods_ref
    WHERE status = 1 AND @rank_initialize = 1
    GROUP BY store_id, parent_id
    HAVING COUNT(*) > COUNT(DISTINCT sort_order)
) conflicts ON conflicts.store_id = ranked.store_id AND conflicts.parent_id = ranked.parent_id
SET r.sort_order = ranked.new_sort_order;

INSERT INTO dodo_rank_config (store_id, version)
SELECT s.id, 0 FROM store s
WHERE NOT EXISTS (SELECT 1 FROM dodo_rank_config c WHERE c.store_id = s.id);

-- 按历史排序及主键倒序确定次序，排序和关联优先级均生成门店内唯一值。
UPDATE dodo_theme_top_list b JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY store_id ORDER BY sort_order, id DESC) AS position
    FROM dodo_theme_top_list
) ranked ON ranked.id = b.id
SET b.sort_order = ranked.position, b.association_priority = ranked.position, b.recommend_position = NULL
WHERE @rank_initialize = 1;

INSERT INTO dodo_rank_tag (store_id, name, sort_order, enabled)
SELECT s.id, '综合', 1, 1 FROM store s
WHERE @rank_initialize = 1 AND NOT EXISTS (
    SELECT 1 FROM dodo_rank_tag t WHERE t.store_id = s.id AND t.name = '综合'
);
INSERT INTO dodo_rank_tag_board (store_id, tag_id, board_id)
SELECT b.store_id, t.id, b.id FROM dodo_theme_top_list b
JOIN dodo_rank_tag t ON t.store_id = b.store_id AND t.name = '综合'
WHERE b.status = 1 AND @rank_initialize = 1
    AND NOT EXISTS (SELECT 1 FROM dodo_rank_tag_board r
        WHERE r.store_id = b.store_id AND r.tag_id = t.id AND r.board_id = b.id);
-- 系统管理。
SET @system_root = (SELECT id FROM dodo_menu WHERE name = '系统管理' AND level = 1);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '系统管理', NULL, 1, 5, 'el-icon-setting', 1
WHERE @system_root IS NULL;
SET @system_root = (SELECT id FROM dodo_menu WHERE name = '系统管理' AND level = 1);
UPDATE dodo_menu SET status = 1 WHERE id = @system_root AND (status IS NULL OR status <> 1);

-- 主题馆。
SET @theme_group = (SELECT id FROM dodo_menu WHERE name = '主题馆' AND level = 2 AND parent_id = @system_root);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '主题馆', @system_root, 2, 2, 'el-icon-s-grid', 1
WHERE @theme_group IS NULL AND @system_root IS NOT NULL;
SET @theme_group = (SELECT id FROM dodo_menu WHERE name = '主题馆' AND level = 2 AND parent_id = @system_root);
UPDATE dodo_menu SET status = 1 WHERE id = @theme_group AND (status IS NULL OR status <> 1);

-- 榜单列表：/ad/ThemeTopList。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/ad/ThemeTopList');
UPDATE dodo_menu SET name = '榜单列表', parent_id = @theme_group, level = 3, order_index = 16, icon = 'el-icon-trophy', status = 1
WHERE id = @page_id AND @theme_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '榜单列表', '/ad/ThemeTopList', @theme_group, 3, 16, 'el-icon-trophy', 1
WHERE @page_id IS NULL AND @theme_group IS NOT NULL;

-- 为原有管理角色补齐页面及祖先菜单，保留其他所有角色授权。
SET @page_id=(SELECT id FROM dodo_menu WHERE url='/ad/ThemeTopList');
UPDATE dodo_role_menu_ref ref
JOIN dodo_role role ON role.role_id=ref.role_id AND role.status=1
JOIN dodo_menu menu ON menu.id=ref.menu_id
SET ref.status=1
WHERE role.role_code IN ('admin','sys_admin','admin_market')
    AND menu.id IN (@page_id,@theme_group,@system_root);
INSERT INTO dodo_role_menu_ref(role_id,menu_id,status)
SELECT role.role_id,menu.id,1 FROM dodo_role role
JOIN dodo_menu menu ON menu.id IN (@page_id,@theme_group,@system_root) AND menu.status=1
WHERE role.status=1 AND role.role_code IN ('admin','sys_admin','admin_market')
AND NOT EXISTS (SELECT 1 FROM dodo_role_menu_ref ref
    WHERE ref.role_id=role.role_id AND ref.menu_id=menu.id);
COMMIT;

    SELECT DATABASE() AS database_name, '榜单建表及升级完成' AS result;
END$$
DELIMITER ;
CALL codex_rank_apply();
DROP PROCEDURE codex_rank_apply;
