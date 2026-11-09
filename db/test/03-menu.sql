-- 测试环境：在后台共用的业务主库执行。
-- 菜单及角色菜单关联可重复执行；不创建角色，不调整员工角色。
SET NAMES utf8mb4;
START TRANSACTION;

-- 客服中心。
SET @customer_root = (SELECT id FROM dodo_menu WHERE name = '客服中心' AND level = 1);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '客服中心', NULL, 1, 2, 'el-icon-service', 1
WHERE @customer_root IS NULL;
SET @customer_root = (SELECT id FROM dodo_menu WHERE name = '客服中心' AND level = 1);
UPDATE dodo_menu SET status = 1 WHERE id = @customer_root AND (status IS NULL OR status <> 1);

-- 报表中心。
SET @report_root = (SELECT id FROM dodo_menu WHERE name = '报表中心' AND level = 1);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '报表中心', NULL, 1, 2.9, 'el-icon-data-analysis', 1
WHERE @report_root IS NULL;
SET @report_root = (SELECT id FROM dodo_menu WHERE name = '报表中心' AND level = 1);
UPDATE dodo_menu SET status = 1 WHERE id = @report_root AND (status IS NULL OR status <> 1);

-- IM 客服。
SET @im_group = (SELECT id FROM dodo_menu WHERE name = 'IM 客服' AND level = 2 AND parent_id = @customer_root);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT 'IM 客服', @customer_root, 2, 30, 'el-icon-chat-dot-round', 1
WHERE @im_group IS NULL AND @customer_root IS NOT NULL;
SET @im_group = (SELECT id FROM dodo_menu WHERE name = 'IM 客服' AND level = 2 AND parent_id = @customer_root);
UPDATE dodo_menu SET status = 1 WHERE id = @im_group AND (status IS NULL OR status <> 1);

-- 晒单领券。
SET @show_group = (SELECT id FROM dodo_menu WHERE name = '晒单领券' AND level = 2 AND parent_id = @customer_root);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '晒单领券', @customer_root, 2, 40, 'el-icon-picture-outline', 1
WHERE @show_group IS NULL AND @customer_root IS NOT NULL;
SET @show_group = (SELECT id FROM dodo_menu WHERE name = '晒单领券' AND level = 2 AND parent_id = @customer_root);
UPDATE dodo_menu SET status = 1 WHERE id = @show_group AND (status IS NULL OR status <> 1);

-- 问卷调查。
SET @survey_group = (SELECT id FROM dodo_menu WHERE name = '问卷调查' AND level = 2 AND parent_id = @customer_root);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '问卷调查', @customer_root, 2, 50, 'el-icon-document', 1
WHERE @survey_group IS NULL AND @customer_root IS NOT NULL;
SET @survey_group = (SELECT id FROM dodo_menu WHERE name = '问卷调查' AND level = 2 AND parent_id = @customer_root);
UPDATE dodo_menu SET status = 1 WHERE id = @survey_group AND (status IS NULL OR status <> 1);

-- 日志分析。
SET @log_group = (SELECT id FROM dodo_menu WHERE name = '日志分析' AND level = 2 AND parent_id = @report_root);
INSERT INTO dodo_menu (name, parent_id, level, order_index, icon, status)
SELECT '日志分析', @report_root, 2, 3, 'el-icon-data-line', 1
WHERE @log_group IS NULL AND @report_root IS NOT NULL;
SET @log_group = (SELECT id FROM dodo_menu WHERE name = '日志分析' AND level = 2 AND parent_id = @report_root);
UPDATE dodo_menu SET status = 1 WHERE id = @log_group AND (status IS NULL OR status <> 1);

-- 客服工作台：/customer/im。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/customer/im');
UPDATE dodo_menu SET name = '客服工作台', parent_id = @im_group, level = 3, order_index = 1, icon = 'el-icon-chat-dot-round', status = 1
WHERE id = @page_id AND @im_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '客服工作台', '/customer/im', @im_group, 3, 1, 'el-icon-chat-dot-round', 1
WHERE @page_id IS NULL AND @im_group IS NOT NULL;

-- 活动管理：/customer/ShowPage。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/customer/ShowPage');
UPDATE dodo_menu SET name = '活动管理', parent_id = @show_group, level = 3, order_index = 1, icon = 'el-icon-setting', status = 1
WHERE id = @page_id AND @show_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '活动管理', '/customer/ShowPage', @show_group, 3, 1, 'el-icon-setting', 1
WHERE @page_id IS NULL AND @show_group IS NOT NULL;

-- 晒单审核：/customer/show。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/customer/show');
UPDATE dodo_menu SET name = '晒单审核', parent_id = @show_group, level = 3, order_index = 2, icon = 'el-icon-document-checked', status = 1
WHERE id = @page_id AND @show_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '晒单审核', '/customer/show', @show_group, 3, 2, 'el-icon-document-checked', 1
WHERE @page_id IS NULL AND @show_group IS NOT NULL;

-- 问卷管理：/customer/surveys。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/customer/surveys');
UPDATE dodo_menu SET name = '问卷管理', parent_id = @survey_group, level = 3, order_index = 1, icon = 'el-icon-document', status = 1
WHERE id = @page_id AND @survey_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '问卷管理', '/customer/surveys', @survey_group, 3, 1, 'el-icon-document', 1
WHERE @page_id IS NULL AND @survey_group IS NOT NULL;

-- 提醒补货：/log/GoodsNoticeReplenish。
SET @page_id = (SELECT id FROM dodo_menu WHERE url = '/log/GoodsNoticeReplenish');
UPDATE dodo_menu SET name = '提醒补货', parent_id = @log_group, level = 3, order_index = 4, icon = 'el-icon-bell', status = 1
WHERE id = @page_id AND @log_group IS NOT NULL;
INSERT INTO dodo_menu (name, url, parent_id, level, order_index, icon, status)
SELECT '提醒补货', '/log/GoodsNoticeReplenish', @log_group, 3, 4, 'el-icon-bell', 1
WHERE @page_id IS NULL AND @log_group IS NOT NULL;

-- 给目标角色补齐页面、二级分组及一级菜单，保留其他所有既有授权。
UPDATE dodo_role_menu_ref ref
JOIN (
SELECT '/customer/im' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'cusSer' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'try_cus' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'schedule' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'admin_market' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'admin_market' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'cusSer' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'try_cus' AS role_code
UNION ALL
SELECT '/customer/surveys' AS url, 'admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'goodsAdmin' AS role_code
) grants ON 1 = 1
JOIN dodo_role role ON role.role_code = grants.role_code AND role.status = 1
JOIN dodo_menu page ON page.url = grants.url AND page.status = 1
LEFT JOIN dodo_menu parent ON parent.id = page.parent_id
JOIN dodo_menu menu ON menu.status = 1 AND menu.id IN (page.id, page.parent_id, parent.parent_id)
SET ref.status = 1
WHERE ref.role_id = role.role_id AND ref.menu_id = menu.id AND (ref.status IS NULL OR ref.status <> 1);

INSERT INTO dodo_role_menu_ref (role_id, menu_id, status)
SELECT DISTINCT role.role_id, menu.id, 1
FROM (
SELECT '/customer/im' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'cusSer' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'try_cus' AS role_code
UNION ALL
SELECT '/customer/im' AS url, 'schedule' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/customer/ShowPage' AS url, 'admin_market' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'admin' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'admin_market' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'cusSer' AS role_code
UNION ALL
SELECT '/customer/show' AS url, 'try_cus' AS role_code
UNION ALL
SELECT '/customer/surveys' AS url, 'admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'sys_admin' AS role_code
UNION ALL
SELECT '/log/GoodsNoticeReplenish' AS url, 'goodsAdmin' AS role_code
) grants
JOIN dodo_role role ON role.role_code = grants.role_code AND role.status = 1
JOIN dodo_menu page ON page.url = grants.url AND page.status = 1
LEFT JOIN dodo_menu parent ON parent.id = page.parent_id
JOIN dodo_menu menu ON menu.status = 1 AND menu.id IN (page.id, page.parent_id, parent.parent_id)
WHERE NOT EXISTS (SELECT 1 FROM dodo_role_menu_ref ref WHERE ref.role_id = role.role_id AND ref.menu_id = menu.id);

COMMIT;

-- 查看五个页面的最终菜单位置。
SELECT page.id, page.name, page.url, parent.name AS parent_name, page.status
FROM dodo_menu page LEFT JOIN dodo_menu parent ON parent.id = page.parent_id
WHERE page.url IN ('/customer/im', '/customer/ShowPage', '/customer/show', '/customer/surveys', '/log/GoodsNoticeReplenish')
ORDER BY page.parent_id, page.order_index;
