/** 显式指定本机独立测试端口后运行；随机临时库，禁止访问现有业务库。 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const port = Number(process.env.RANK_TEST_MYSQL_PORT);
assert(Number.isInteger(port) && port > 0 && port <= 65535, 'Require explicit RANK_TEST_MYSQL_PORT');
const schema = 'dodo_rank_test_' + require('node:crypto').randomBytes(8).toString('hex');
const root = path.resolve(__dirname, '../../../..');
const cli = process.env.RANK_TEST_MYSQL_CLI || 'mysql';
function sql(input, database, success = true) {
    const args = ['--host=127.0.0.1', '--port=' + port, '--user=' + (process.env.RANK_TEST_MYSQL_USER || 'root'),
        '--default-character-set=utf8mb4', '--batch', '--skip-column-names'];
    if (database) {
        args.push(database);
    }
    const result = spawnSync(cli, args, {input, encoding: 'utf8', windowsHide: true});
    if (result.error) {
        throw result.error;
    }
    if (success) {
        assert.equal(result.status, 0, result.stderr);
    }
    return result;
}
const migration = fs.readFileSync(path.join(root, 'db/test/05-rank.sql'), 'utf8').replace(/\r\n/g, '\n');
function section(name) {
    return migration.split('-- BEGIN RANK_' + name + '\n')[1].split('-- END RANK_' + name)[0];
}
const preflight = section('PREFLIGHT');
const details = section('DETAILS');
const preview = section('PREVIEW');
// 保护过程与人工预检必须采用完全相同的阻断规则，避免改动一处后另一处仍报旧错误。
const normalizeSql = value => value.replace(/--[^\r\n]*/g, '').replace(/;\s*$/, '').replace(/\s+/g, ' ').trim();
const guardQuery = migration.match(/IF EXISTS \(SELECT 1 FROM \(([\s\S]+?)\) rank_issues\) THEN/);
assert(guardQuery, 'Missing preflight guard query');
assert.equal(normalizeSql(guardQuery[1]), normalizeSql(preflight), 'Guard and read-only preflight must agree');
// 从既有门店 Mapper 核对物理表名，禁止测试夹具和迁移共同沿用一个猜测表名。
const frontstageRoot = process.env.DODO_FRONTSTAGE_ROOT || path.resolve(root, '../dodo-supermarket-frontstage');
const storeMapper = fs.readFileSync(path.join(frontstageRoot,
    'web-starter/src/main/java/com/dodosupermarket/masterdata/dao/StoreDAO.xml'), 'utf8');
const storeTable = storeMapper.match(/\bfrom\s+([a-zA-Z_][a-zA-Z0-9_]*)\s+t\b/i)?.[1];
assert.equal(storeTable, 'store', 'Ranking fixtures must match the existing StoreDAO physical table');
assert(!/\bdodo_store\b/i.test(migration + preflight), 'Do not invent a prefixed store table');
assert(migration.split(/\r?\n/).includes('DELIMITER $$'), 'Migration routine delimiter must match END');
assert(migration.split(/\r?\n/).includes('END$$'), 'Migration routine must end with the declared delimiter');
assert(!migration.split(/\r?\n/).includes('DELIMITER $'), 'Single-dollar delimiter is a regression');
function assertBlockedWithoutMutation() {
    assert.notEqual(sql(preflight, schema).stdout.trim(), '', 'Unsafe history must block migration');
    const before = sql('SELECT * FROM dodo_theme_top_goods_ref ORDER BY id;', schema).stdout;
    const failed = sql(migration, schema, false);
    assert.notEqual(failed.status, 0);
    assert.match(failed.stderr, /rank preflight failed/);
    assert.equal(sql(`SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='dodo_theme_top_list' AND COLUMN_NAME='association_priority';`, schema).stdout.trim(), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME LIKE 'dodo_rank_%';`, schema).stdout.trim(), '0');
    assert.equal(sql('SELECT * FROM dodo_theme_top_goods_ref ORDER BY id;', schema).stdout, before);
}
try {
    sql('CREATE DATABASE ' + schema + ' CHARACTER SET utf8mb4;');
    sql(`
        CREATE TABLE dodo_menu (id INT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单主键',
            name VARCHAR(100) COMMENT '菜单名称', url VARCHAR(255) COMMENT '页面地址',
            parent_id INT COMMENT '父级菜单', level INT COMMENT '菜单层级',
            order_index DECIMAL(10,2) COMMENT '排序', icon VARCHAR(100) COMMENT '图标',
            status INT COMMENT '有效状态') COMMENT='隔离菜单夹具';
        CREATE TABLE dodo_role (role_id INT PRIMARY KEY COMMENT '角色主键',
            role_code VARCHAR(50) COMMENT '角色代码', status INT COMMENT '有效状态') COMMENT='隔离角色夹具';
        CREATE TABLE dodo_role_menu_ref (role_id INT COMMENT '角色主键', menu_id INT COMMENT '菜单主键',
            status INT COMMENT '有效状态', PRIMARY KEY(role_id,menu_id)) COMMENT='隔离角色菜单夹具';
        INSERT INTO dodo_role VALUES(1,'admin',1),(2,'sys_admin',1),(3,'admin_market',1);
        CREATE TABLE store (id BIGINT PRIMARY KEY COMMENT '门店主键') COMMENT='测试门店';
        CREATE TABLE dodo_goods (
            goods_id BIGINT PRIMARY KEY COMMENT '商品主键', store_id BIGINT COMMENT '所属门店',
            goods_sn VARCHAR(100) COMMENT '商品编码', status INT DEFAULT 1 COMMENT '有效状态',
            is_delete INT DEFAULT 0 COMMENT '删除标记',
            goods_name VARCHAR(100) COMMENT '商品名称') COMMENT='测试商品';
        CREATE TABLE dodo_theme_top_list (
            id BIGINT PRIMARY KEY COMMENT '榜单主键', store_id BIGINT COMMENT '所属门店',
            sort_order INT COMMENT '显示顺序', is_prod TINYINT COMMENT '发布标记',
            status INT DEFAULT 1 COMMENT '有效状态',
            title VARCHAR(100) COMMENT '榜单名称') COMMENT='测试历史榜单';
        CREATE TABLE dodo_theme_top_goods_ref (
            id BIGINT PRIMARY KEY COMMENT '关系主键', store_id BIGINT COMMENT '所属门店',
            parent_id BIGINT COMMENT '所属榜单', goods_id BIGINT COMMENT '商品主键',
            goods_sn VARCHAR(100) COMMENT '商品编码', sort_order INT COMMENT '排序',
            status INT DEFAULT 1 COMMENT '有效状态',
            goods_name VARCHAR(100) COMMENT '关系商品名称') COMMENT='测试历史榜单商品';
        INSERT INTO store VALUES (1),(2),(3);
        INSERT INTO dodo_goods (goods_id,store_id,goods_sn,status,is_delete) VALUES (100,1,'A',1,0),(101,1,'B',1,0),(200,2,'C',1,0);
        INSERT INTO dodo_theme_top_list (id,store_id,sort_order,is_prod,status) VALUES (10,1,5,1,1),(11,1,5,0,1),(20,2,2,1,1);
        INSERT INTO dodo_theme_top_goods_ref (id,store_id,parent_id,goods_id,goods_sn,sort_order,status)
        VALUES (1,1,10,100,'A',5,1),(2,1,10,101,'B',5,1),(3,2,20,200,'C',1,1);`, schema);
    // A 方案允许历史重复排序，由事务内迁移整理；不能要求操作者先删除或手工清空关系。
    assert.equal(sql(preflight, schema).stdout.trim(), '');
    assert.match(sql(details, schema).stdout, /DUPLICATE_SORT/);
    assert.match(sql(details, schema).stdout, /\tINFO\tDUPLICATE_SORT/);
    sql('UPDATE dodo_theme_top_goods_ref SET goods_id=100 WHERE id=2;', schema);
    assertBlockedWithoutMutation();
    sql('UPDATE dodo_theme_top_goods_ref SET goods_id=101 WHERE id=2;', schema);
    sql("UPDATE dodo_theme_top_goods_ref SET goods_sn='A' WHERE id=2;", schema);
    assert.match(sql(preflight, schema).stdout, /NULL_OR_DUPLICATE_GOODS/);
    assert.match(sql(details, schema).stdout, /DUPLICATE_SKU/);
    assert.match(sql(details, schema).stdout, /GOODS_SKU_MISMATCH/);
    assertBlockedWithoutMutation();
    sql("UPDATE dodo_theme_top_goods_ref SET goods_sn='B' WHERE id=2;", schema);
    for (const [invalid, repair] of [
        ['sort_order=NULL', 'sort_order=5'], ['goods_id=NULL', 'goods_id=101'],
        ['goods_sn=NULL', "goods_sn='B'"], ["goods_sn=''", "goods_sn='B'"],
        ["goods_sn='STALE'", "goods_sn='B'"], ['store_id=2', 'store_id=1'],
        ["goods_id=200,goods_sn='C'", "goods_id=101,goods_sn='B'"],
        ['goods_id=999', 'goods_id=101'], ['parent_id=999', 'parent_id=10']
    ]) {
        sql('UPDATE dodo_theme_top_goods_ref SET ' + invalid + ' WHERE id=2;', schema);
        assertBlockedWithoutMutation();
        sql('UPDATE dodo_theme_top_goods_ref SET ' + repair + ' WHERE id=2;', schema);
    }
    sql('UPDATE dodo_theme_top_list SET status=0 WHERE id=10;', schema);
    assertBlockedWithoutMutation();
    sql('UPDATE dodo_theme_top_list SET status=1 WHERE id=10;', schema);
    sql('UPDATE dodo_theme_top_list SET store_id=999 WHERE id=11;', schema);
    assertBlockedWithoutMutation();
    sql('UPDATE dodo_theme_top_list SET store_id=1,is_prod=1 WHERE id=11;', schema);
    assertBlockedWithoutMutation();
    sql('UPDATE dodo_theme_top_list SET is_prod=0 WHERE id=11;', schema);
    sql('UPDATE dodo_theme_top_goods_ref SET sort_order=10 WHERE id=2;', schema);
    assert.equal(sql(preflight, schema).stdout.trim(), '');
    assert.equal(sql(details, schema).stdout.trim(), '');
    // 只读明细必须拆出具体原因，并且自身不能修复、删除或下线任何数据。
    sql(`INSERT INTO dodo_theme_top_list (id,store_id,sort_order,is_prod,status)
        VALUES (90,1,90,0,0),(91,999,91,0,1),(92,1,92,1,1);
        INSERT INTO dodo_goods (goods_id,store_id,goods_sn,status,is_delete)
        VALUES (300,1,'DELETED',1,1),(301,1,'INACTIVE',0,0);
        INSERT INTO dodo_theme_top_goods_ref (id,store_id,parent_id,goods_id,goods_sn,sort_order,status) VALUES
        (90,1,90,100,'A',1,1),(91,1,999,999,'MISSING',2,1),
        (92,1,10,200,'C',20,1),(93,2,10,200,'C',1,1),
        (94,1,10,300,'DELETED',21,1),(95,1,10,301,'INACTIVE',22,1),
        (96,1,10,NULL,NULL,NULL,1),(97,1,10,100,' a ',23,1);`, schema);
    const diagnosis = sql(details, schema).stdout;
    for (const reason of ['BOARD_INACTIVE', 'BOARD_NOT_FOUND', 'GOODS_NOT_FOUND', 'GOODS_STORE_MISMATCH',
        'BOARD_STORE_MISMATCH', 'GOODS_DELETED', 'GOODS_INACTIVE', 'MISSING_GOODS_ID', 'MISSING_SKU',
        'MISSING_SORT', 'DUPLICATE_GOODS_ID', 'DUPLICATE_SKU', 'GOODS_SKU_MISMATCH',
        'STORE_NOT_FOUND', 'EMPTY_PUBLISHED_BOARD']) {
        assert(diagnosis.includes(reason), 'Missing diagnostic reason: ' + reason);
    }
    assert.equal(sql('SELECT COUNT(*) FROM dodo_theme_top_goods_ref WHERE id >= 90 AND status=1;', schema).stdout.trim(), '8');
    sql(`DELETE FROM dodo_theme_top_goods_ref WHERE id >= 90;
        DELETE FROM dodo_theme_top_list WHERE id >= 90;
        DELETE FROM dodo_goods WHERE goods_id IN (300,301);`, schema);
    assert.equal(sql(preflight, schema).stdout.trim(), '');
    assert.equal(sql(details, schema).stdout.trim(), '');
    // 同时复现重复排序、失效商品、软删除商品和历史失效关系，校验新旧两套名次均不变化。
    sql(`UPDATE dodo_theme_top_goods_ref SET sort_order=5 WHERE id=2;
        UPDATE dodo_goods SET status=0 WHERE goods_id=101;
        INSERT INTO dodo_goods (goods_id,store_id,goods_sn,status,is_delete)
        VALUES (300,1,'DELETED',1,1),(301,1,'VISIBLE',1,0);
        INSERT INTO dodo_theme_top_goods_ref (id,store_id,parent_id,goods_id,goods_sn,sort_order,status)
        VALUES (4,1,10,300,'DELETED',6,1),(5,1,10,301,'VISIBLE',10,1),(6,1,10,999,'HISTORY',5,0);`, schema);
    assert.equal(sql(preflight, schema).stdout.trim(), '');
    const info = sql(details, schema).stdout;
    assert(!info.includes('\tBLOCK\t'), 'Historical visibility and duplicate sorting are informational');
    assert.match(info, /GOODS_INACTIVE/);
    assert.match(info, /GOODS_DELETED/);
    const previewRows = sql(preview, schema).stdout.trim().split(/\r?\n/).map(line => line.split('\t'));
    const previewSorts = previewRows.map(row => row[3] + ':' + row[7]);
    assert.deepEqual(previewSorts, ['6:1', '2:2', '1:3', '4:4', '5:5']);
    const relationIdentity = 'SELECT id,store_id,parent_id,goods_id,goods_sn,status,goods_name FROM dodo_theme_top_goods_ref ORDER BY id;';
    const identityBefore = sql(relationIdentity, schema).stdout;
    const productsBefore = sql('SELECT * FROM dodo_goods ORDER BY goods_id;', schema).stdout;
    const ranks = `SELECT id,board_rank FROM (SELECT id,store_id,parent_id,
        ROW_NUMBER() OVER (PARTITION BY store_id,parent_id ORDER BY sort_order,id DESC) AS board_rank
        FROM dodo_theme_top_goods_ref WHERE status=1) ranked ORDER BY store_id,parent_id,board_rank;`;
    const ranksBefore = sql(ranks, schema).stdout;
    const legacyRanks = `SELECT id,board_rank FROM (SELECT id,store_id,parent_id,
        ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY sort_order,id DESC) AS board_rank
        FROM dodo_theme_top_goods_ref) ranked ORDER BY store_id,parent_id,board_rank;`;
    const legacyRanksBefore = sql(legacyRanks, schema).stdout;
    sql(migration, schema);
    assert.equal(sql(relationIdentity, schema).stdout, identityBefore, 'Migration must preserve every historical relationship');
    assert.equal(sql('SELECT * FROM dodo_goods ORDER BY goods_id;', schema).stdout, productsBefore, 'Migration must never reactivate/delete products');
    assert.equal(sql(ranks, schema).stdout, ranksBefore, 'Normalizing duplicate sorting must preserve all original ranks');
    assert.equal(sql(legacyRanks, schema).stdout, legacyRanksBefore, 'Old API ranks over the complete relation history must remain unchanged');
    for (const row of previewRows) {
        assert.equal(sql('SELECT sort_order FROM dodo_theme_top_goods_ref WHERE id=' + Number(row[3]) + ';', schema).stdout.trim(), row[7]);
    }
    assert.equal(sql('SELECT sort_order FROM dodo_theme_top_goods_ref WHERE id=6;', schema).stdout.trim(), '1');
    assert.equal(sql('SELECT sort_order FROM dodo_theme_top_goods_ref WHERE id=3;', schema).stdout.trim(), '1');
    assert.equal(sql(preflight, schema).stdout.trim(), '');
    assert(!sql(details, schema).stdout.includes('DUPLICATE_SORT'));
    assert.equal(sql(preview, schema).stdout.trim(), '');
    assert.equal(sql(`SELECT GROUP_CONCAT(board_rank ORDER BY board_rank) FROM (
        SELECT r.*,ROW_NUMBER() OVER (PARTITION BY store_id,parent_id ORDER BY sort_order,id DESC) AS board_rank
        FROM dodo_theme_top_goods_ref r WHERE status=1 AND store_id=1
        ) ranked JOIN dodo_goods g ON g.goods_id=ranked.goods_id AND g.store_id=ranked.store_id
        AND g.goods_sn=ranked.goods_sn AND g.status=1 AND g.is_delete=0
        WHERE ranked.parent_id=10;`, schema).stdout.trim(), '2,4', 'Hidden products must leave their original ranking gaps');
    assert.equal(sql('SELECT COUNT(*) FROM dodo_rank_config WHERE version=0;', schema).stdout.trim(), '3');
    assert.equal(sql("SELECT COUNT(*) FROM dodo_rank_tag WHERE name='综合' AND enabled=1;", schema).stdout.trim(), '3');
    assert.equal(sql('SELECT COUNT(*) FROM dodo_rank_tag_board;', schema).stdout.trim(), '3');
    assert.equal(sql('SELECT GROUP_CONCAT(id ORDER BY sort_order) FROM dodo_theme_top_list WHERE store_id=1;', schema).stdout.trim(), '11,10');
    assert.equal(sql('SELECT COUNT(*) FROM dodo_theme_top_list WHERE recommend_position IS NOT NULL;', schema).stdout.trim(), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM (SELECT store_id FROM dodo_theme_top_list GROUP BY store_id
        HAVING COUNT(*) != COUNT(DISTINCT association_priority) OR MIN(association_priority) <= 0) duplicates;`, schema).stdout.trim(), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND (TABLE_NAME LIKE 'dodo_rank_%' OR (TABLE_NAME='dodo_theme_top_list'
        AND COLUMN_NAME IN ('association_priority','recommend_position'))) AND COLUMN_COMMENT='';`, schema).stdout.trim(), '0');
    assert.equal(sql(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME LIKE 'dodo_rank_%' AND (TABLE_COMMENT='' OR ENGINE!='InnoDB');`, schema).stdout.trim(), '0');
    // 重复更新不能覆盖已配置的推荐位、背景图、乐观锁版本或菜单 ID。
    sql("UPDATE dodo_theme_top_list SET recommend_position=1,association_priority=50,sort_order=99 WHERE id=10; UPDATE dodo_rank_config SET version=7,homepage_background_image='keep.png';", schema);
    const beforeRepeat = sql('SELECT * FROM dodo_theme_top_list ORDER BY id; SELECT * FROM dodo_rank_config ORDER BY store_id; SELECT * FROM dodo_rank_tag ORDER BY id; SELECT * FROM dodo_menu ORDER BY id;', schema).stdout;
    sql(migration, schema);
    assert.equal(sql('SELECT * FROM dodo_theme_top_list ORDER BY id; SELECT * FROM dodo_rank_config ORDER BY store_id; SELECT * FROM dodo_rank_tag ORDER BY id; SELECT * FROM dodo_menu ORDER BY id;', schema).stdout, beforeRepeat);
    assert.equal(sql('SELECT COUNT(*) FROM dodo_role_menu_ref;', schema).stdout.trim(), '9');
    assert.match(sql(migration.replace("SET @rank_action = 'migrate';", "SET @rank_action = 'status';"), schema).stdout, /association_priority/);
    assert.equal(sql(migration.replace("SET @rank_action = 'migrate';", "SET @rank_action = 'check';"), schema).status, 0);
    assert.equal(sql(migration.replace("SET @rank_action = 'migrate';", "SET @rank_action = 'preview';"), schema).status, 0);
    // 从无榜单表的新环境安装，必须一次建立完整五表及全字段中文注释。
    sql('DROP TABLE dodo_rank_tag_board,dodo_rank_tag,dodo_rank_config,dodo_theme_top_goods_ref,dodo_theme_top_list;', schema);
    sql(migration, schema);
    assert.equal(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('dodo_rank_config','dodo_rank_tag','dodo_rank_tag_board','dodo_theme_top_list','dodo_theme_top_goods_ref');", schema).stdout.trim(), '5');
    assert.equal(sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name LIKE 'dodo_rank_%' OR table_name LIKE 'dodo_theme_top_%') AND column_comment='';", schema).stdout.trim(), '0');
    sql(migration, schema);
    console.log('MySQL A migration passed: blockers preserved; inactive/deleted goods and all relationships retained; duplicate sorting normalized without rank changes; preview matched; Chinese comments verified.');
} finally {
    sql('DROP DATABASE IF EXISTS ' + schema + ';');
}
