package com.dodosupermarket.rank;

import com.dodosupermarket.rank.bean.dto.RankSettings;
import com.dodosupermarket.rank.bean.po.*;
import com.dodosupermarket.rank.dao.*;
import com.dodosupermarket.rank.service.logic.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 实际 Mapper、GenericService 与数据库事务集成，验证失败时不存在部分提交。 */
public class RankServiceTest {
    private JdbcTemplate db;
    private RankService service;
    private RankTagServiceImpl tags;
    private RankBoardServiceImpl boards;
    private RankGoodsRefServiceImpl goods;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE store (id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE dodo_goods (goods_id BIGINT PRIMARY KEY, store_id BIGINT, goods_sn VARCHAR(100), "
                + "status INT DEFAULT 1, is_delete INT DEFAULT 0, goods_number INT DEFAULT 0)");
        db.execute("CREATE TABLE dodo_theme_top_list (id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT, title VARCHAR(100), "
                + "sort_order INT, is_prod BOOLEAN, top_url VARCHAR(100), background_image VARCHAR(100), "
                + "background_color VARCHAR(100), goods_count DOUBLE, status INT DEFAULT 1)");
        db.execute("CREATE TABLE dodo_theme_top_goods_ref (id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT, "
                + "parent_id BIGINT, goods_id BIGINT, goods_sn VARCHAR(100), sort_order INT, promote_price DOUBLE, "
                + "limit_per_order DOUBLE, status INT DEFAULT 1)");
        String migration = resource("db/test/05-rank.sql");
        // 从统一 SQL 读取真实字段升级和新增配置表，H2 不执行过程或动态 SQL 包装。
        String tables = migration.substring(migration.indexOf("CREATE TABLE IF NOT EXISTS dodo_rank_config"),
                migration.indexOf("-- END RANK_TABLES"));
        java.util.regex.Matcher changes = java.util.regex.Pattern.compile("'((?:ALTER TABLE dodo_theme_top_list)(?:''|[^'])*)'")
                .matcher(migration);
        StringBuilder ddlBuilder = new StringBuilder(tables);
        while (changes.find()) {
            ddlBuilder.append(changes.group(1).replace("''", "'")).append(';');
        }
        String ddl = ddlBuilder.toString();
        new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(source);
        db.update("INSERT INTO store VALUES (1),(2),(3)");
        db.update("INSERT INTO dodo_rank_config(store_id,version) SELECT id,0 FROM store");
        db.update("INSERT INTO dodo_goods(goods_id,store_id,goods_sn) VALUES (100,1,'A'),(101,1,'B'),(200,2,'C')");
        db.update("INSERT INTO dodo_theme_top_list(id,store_id,title,sort_order,is_prod,association_priority) "
                + "VALUES (10,1,'原始标题',1,1,1),(11,1,'第二榜单',2,1,2),(20,2,'其他门店',1,1,1)");
        db.update("INSERT INTO dodo_theme_top_goods_ref(id,store_id,parent_id,goods_id,goods_sn,sort_order) "
                + "VALUES (1,1,10,100,'A',10),(2,1,10,101,'B',20),(3,1,11,101,'B',5),(4,2,20,200,'C',1)");
        db.update("INSERT INTO dodo_rank_tag(id,store_id,name,sort_order,enabled) VALUES (1,1,'综合',1,1),(2,2,'综合',1,1)");
        db.update("INSERT INTO dodo_rank_tag_board VALUES (1,1,10),(1,1,11),(2,2,20)");
        Configuration configuration = new Configuration(new Environment("rank", new SpringManagedTransactionFactory(), source));
        for (String name : List.of("RankBoard", "RankTag", "RankGoodsRef", "Rank")) {
            String resource = "com/dodosupermarket/rank/dao/" + name + "DAO.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(Objects.requireNonNull(input), configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate sessions = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        tags = new RankTagServiceImpl(sessions.getMapper(RankTagDAO.class));
        boards = new RankBoardServiceImpl(sessions.getMapper(RankBoardDAO.class));
        goods = new RankGoodsRefServiceImpl(sessions.getMapper(RankGoodsRefDAO.class));
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        for (Object crud : List.of(tags, boards, goods)) {
            org.springframework.test.util.ReflectionTestUtils.setField(crud, "transactionManager", manager);
        }
        service = new RankService(sessions.getMapper(RankDAO.class), tags, manager);
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void expect(String code, Runnable action) {
        assertEquals(code, assertThrows(RankException.class, action::run).code);
    }

    @Test
    void publicConfigReadDoesNotInitializeOrLockAdminSettings() {
        db.update("DELETE FROM dodo_rank_config WHERE store_id=3");
        assertEquals(0L, service.readConfig(3).getVersion());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_config WHERE store_id=3", Integer.class));
        db.update("UPDATE dodo_rank_config SET homepage_background_image='背景',version=5 WHERE store_id=1");
        assertEquals("背景", service.readConfig(1).getHomepageBackgroundImage());
        assertEquals(5L, service.readConfig(1).getVersion());
    }

    @Test
    void issueScanAndCleanupPreserveHealthyOutOfStockAndOtherStores() {
        db.update("UPDATE dodo_goods SET status=0 WHERE goods_id=100");
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=200");
        Map<String, Object> issues = service.goodsIssues(1);
        List<com.dodosupermarket.rank.bean.dto.RankGoodsIssue> list =
                (List<com.dodosupermarket.rank.bean.dto.RankGoodsIssue>) issues.get("issues");
        assertEquals(0L, issues.get("version"));
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getRefId());
        assertEquals("商品已停用", list.get(0).getReason());
        Map<String, Object> result = service.removeGoodsIssues(1, 0L, List.of(1L));
        assertEquals(1, result.get("removedCount"));
        assertEquals(1L, ((RankSettings) result.get("settings")).getVersion());
        assertEquals(List.of(101L), service.rankedGoods(1, 10).stream().map(RankGoodsRef::getGoodsId).collect(java.util.stream.Collectors.toList()));
        assertEquals(0, db.queryForObject("SELECT status FROM dodo_theme_top_goods_ref WHERE id=1", Integer.class));
        assertEquals(1, db.queryForObject("SELECT status FROM dodo_theme_top_goods_ref WHERE id=4", Integer.class));
        assertEquals(1D, service.requireBoard(1, 10).getGoodsCount());
    }

    @Test
    void cleanupOfflinesEmptyBoardsAndReleasesRecommendation() {
        service.mutate(1, 0L, () -> {
            db.update("UPDATE dodo_theme_top_list SET recommend_position=1 WHERE id=10");
            return null;
        });
        db.update("UPDATE dodo_goods SET status=0 WHERE store_id=1");
        Map<String, Object> result = service.removeGoodsIssues(1, 1L, List.of(1L, 2L, 3L));
        assertEquals(Set.of(10L, 11L), result.get("offlineBoardIds"));
        assertFalse(service.requireBoard(1, 10).getIsProd());
        assertNull(service.requireBoard(1, 10).getRecommendPosition());
        assertEquals(0D, service.requireBoard(1, 10).getGoodsCount());
    }

    @Test
    void cleanupRejectsStaleVersionForeignIdsAndRecoveredGoods() {
        db.update("UPDATE dodo_goods SET status=0 WHERE goods_id=100");
        expect("VERSION_CONFLICT", () -> service.removeGoodsIssues(1, 8L, List.of(1L)));
        expect("ISSUES_CHANGED", () -> service.removeGoodsIssues(1, 0L, List.of(1L, 4L)));
        assertEquals(1, db.queryForObject("SELECT status FROM dodo_theme_top_goods_ref WHERE id=1", Integer.class));
        db.update("UPDATE dodo_goods SET status=1 WHERE goods_id=100");
        expect("ISSUES_CHANGED", () -> service.removeGoodsIssues(1, 0L, List.of(1L)));
        assertEquals(0L, service.revision(1));
        expect("INVALID_ARGUMENT", () -> service.removeGoodsIssues(1, null, List.of(1L)));
        expect("INVALID_ARGUMENT", () -> service.removeGoodsIssues(1, 0L, List.of(1L, 1L)));
    }

    @Test
    void partialCleanupRollsBackWhenPublishedBoardStillInvalid() {
        db.update("UPDATE dodo_goods SET status=0 WHERE store_id=1");
        expect("INVALID_GOODS", () -> service.removeGoodsIssues(1, 0L, List.of(1L)));
        assertEquals(1, db.queryForObject("SELECT status FROM dodo_theme_top_goods_ref WHERE id=1", Integer.class));
        assertEquals(0L, service.revision(1));
    }

    @Test
    void batchPreviewsRespectStorePublicationAndOriginalRanks() {
        db.update("INSERT INTO dodo_theme_top_goods_ref(id,store_id,parent_id,goods_id,goods_sn,sort_order) "
                + "VALUES (5,1,10,999,'HIDDEN',0),(6,1,10,102,'FOURTH',30)");
        List<RankGoodsRef> refs = service.previewGoods(1, List.of(10L, 11L, 20L));
        assertEquals(4, refs.size());
        assertEquals(List.of(1, 2, 3, 1), refs.stream().map(RankGoodsRef::getBoardRank).collect(java.util.stream.Collectors.toList()));
        assertFalse(refs.stream().anyMatch(ref -> ref.getGoodsId() == 102L || ref.getStoreId() == 2L));
        db.update("UPDATE dodo_theme_top_list SET is_prod=0 WHERE id=10");
        assertEquals(1, service.previewGoods(1, List.of(10L, 11L)).size());
        assertTrue(service.previewGoods(1, Collections.emptyList()).isEmpty());
    }

    @Test
    void batchedTagMembershipsKeepEmptyTagsAndBoardOrder() {
        db.update("INSERT INTO dodo_rank_tag(id,store_id,name,sort_order,enabled) VALUES (3,1,'空标签',2,1),(4,1,'停用',3,0)");
        assertEquals(2, service.tags(1, true).size());
        assertEquals(List.of(10L, 11L), service.tags(1, true).get(0).getBoardIds());
        assertTrue(service.tags(1, true).get(1).getBoardIds().isEmpty());
        db.update("UPDATE dodo_theme_top_list SET status=0 WHERE id=10");
        assertEquals(List.of(11L), service.tags(1, true).get(0).getBoardIds());
        assertEquals(3, service.tags(1, false).size());
    }

    @Test
    void settingsSnapshotAndWhitelistAndNewTag() {
        RankSettings input = service.settings(1);
        Collections.reverse(input.getBoards());
        input.getBoards().get(0).setTitle("不能覆盖");
        input.getBoards().get(0).setIsProd(false);
        input.getBoards().get(0).setRecommendPosition(2);
        input.setHomepageBackgroundImage("首页图");
        RankTag added = new RankTag();
        added.setName("精选");
        added.setEnabled(true);
        added.setBoardIds(List.of(10L));
        input.setTags(new ArrayList<>(List.of(input.getTags().get(0), added)));
        RankSettings result = service.saveSettings(1, input);
        assertEquals(1L, result.getVersion());
        assertEquals(11L, result.getBoards().get(0).getId());
        assertEquals("第二榜单", result.getBoards().get(0).getTitle());
        assertTrue(result.getBoards().get(0).getIsProd());
        assertEquals(2, result.getBoards().get(0).getRecommendPosition());
        assertEquals(2, result.getTags().size());
        assertNotNull(result.getTags().get(1).getId());
        assertEquals(List.of(10L), result.getTags().get(1).getBoardIds());
        assertEquals("首页图", result.getHomepageBackgroundImage());
        RankSettings clear = service.settings(1);
        clear.getBoards().forEach(board -> board.setRecommendPosition(null));
        service.saveSettings(1, clear);
        assertNull(service.requireBoard(1, 11).getRecommendPosition());
    }

    @Test
    void staleVersionDoesNotInvokeAction() {
        service.mutate(1, 0L, () -> null);
        AtomicInteger invoked = new AtomicInteger();
        expect("VERSION_CONFLICT", () -> service.mutate(1, 0L, invoked::incrementAndGet));
        assertEquals(0, invoked.get());
        assertEquals(1L, service.revision(1));
        service.mutate(1, null, invoked::incrementAndGet);
        assertEquals(1, invoked.get());
        assertEquals(2, service.revision(1));
        RankSettings missingVersion = service.settings(1);
        missingVersion.setVersion(null);
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, missingVersion));
    }

    @Test
    void callbackFailureRollsBackAndRestoresRouting() {
        assertThrows(IllegalStateException.class, () -> service.mutate(1, 0L, () -> {
            RankBoard changed = service.requireBoard(1, 10);
            changed.setTitle("临时");
            boards.update(changed);
            throw new IllegalStateException("失败");
        }));
        assertEquals("原始标题", service.requireBoard(1, 10).getTitle());
        assertEquals(0, service.revision(1));
        assertNull(pers.binaryhunter.db.mybatis.datasource.ConnectionHolder.FORCE_WRITE.get());
    }

    @Test
    void crossStoreReadsAndTagMembershipWritesAreRejected() {
        expect("NOT_FOUND", () -> service.requireBoard(1, 20));
        expect("NOT_FOUND", () -> service.rankedGoods(1, 20));
        assertTrue(service.associations(1, List.of(200L)).isEmpty());
        RankSettings input = service.settings(1);
        input.getTags().get(0).setBoardIds(List.of(20L));
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, input));
        assertEquals(List.of(10L, 11L), service.tags(1, true).get(0).getBoardIds());
        assertEquals(0, service.revision(1));
    }

    @Test
    void actualRankSurvivesGoodsFilterAndSoldOut() {
        List<RankGoodsRef> refs = service.associations(1, List.of(101L));
        assertEquals(2, refs.size());
        assertEquals(10L, refs.get(0).getParentId());
        assertEquals(2, refs.get(0).getBoardRank());
        assertEquals(1, refs.get(1).getBoardRank());
        assertEquals(2, service.rankedGoods(1, 10).size());
        db.update("UPDATE dodo_theme_top_list SET is_prod=0 WHERE id=10");
        assertEquals(1, service.associations(1, List.of(101L)).size());
        db.update("UPDATE dodo_rank_tag SET enabled=0 WHERE id=1");
        assertEquals(1, service.associations(1, List.of(101L)).size());
    }

    @Test
    void storeFilterPrecedesWindowAndTiesUseDescendingId() {
        db.update("INSERT INTO dodo_theme_top_goods_ref(id,store_id,parent_id,goods_id,goods_sn,sort_order) VALUES (50,2,10,200,'C',0)");
        assertEquals(1, service.rankedGoods(1, 10).get(0).getBoardRank());
        db.update("UPDATE dodo_theme_top_goods_ref SET sort_order=10 WHERE id=2");
        assertEquals(2L, service.rankedGoods(1, 10).get(0).getId());
    }

    @Test
    void publishedValidationRollsBackDeletionAndCrossStoreGoods() {
        expect("INVALID_PUBLISHED_BOARD", () -> service.mutate(1, 0L, () -> {
            db.update("DELETE FROM dodo_theme_top_goods_ref WHERE parent_id=10");
            return null;
        }));
        assertEquals(2, service.rankedGoods(1, 10).size());
        expect("INVALID_GOODS", () -> service.mutate(1, 0L, () -> {
            db.update("UPDATE dodo_theme_top_goods_ref SET goods_id=200,goods_sn='C' WHERE id=1");
            return null;
        }));
        assertEquals(100L, service.rankedGoods(1, 10).get(0).getGoodsId());
        assertEquals(0, service.revision(1));
    }

    @Test
    void recommendationAndPriorityCollisionsRollBackFullSettings() {
        RankSettings input = service.settings(1);
        input.getBoards().forEach(board -> board.setRecommendPosition(1));
        input.setHomepageBackgroundImage("不能保存");
        expect("INVALID_PUBLISHED_BOARD", () -> service.saveSettings(1, input));
        assertNull(service.settings(1).getHomepageBackgroundImage());
        input.getBoards().forEach(board -> {
            board.setRecommendPosition(null);
            board.setAssociationPriority(1);
        });
        expect("INVALID_PUBLISHED_BOARD", () -> service.saveSettings(1, input));
        assertEquals(2, service.requireBoard(1, 11).getAssociationPriority());
        assertEquals(0, service.revision(1));
    }

    @Test
    void unpublishedOrOutOfRangeRecommendationRejected() {
        expect("INVALID_PUBLISHED_BOARD", () -> service.mutate(1, 0L, () -> {
            db.update("UPDATE dodo_theme_top_list SET is_prod=0,recommend_position=1 WHERE id=10");
            return null;
        }));
        RankSettings input = service.settings(1);
        input.getBoards().get(0).setRecommendPosition(4);
        expect("INVALID_PUBLISHED_BOARD", () -> service.saveSettings(1, input));
    }

    @Test
    void missingSnapshotFieldsAndBoardIdsRejected() {
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, new RankSettings()));
        RankSettings input = service.settings(1);
        input.setBoards(input.getBoards().subList(0, 1));
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, input));
        RankSettings missing = service.settings(1);
        missing.getTags().get(0).setEnabled(null);
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, missing));
        RankSettings absentPriority = service.settings(1);
        absentPriority.getBoards().get(0).setAssociationPriority(null);
        expect("INVALID_SETTINGS", () -> service.saveSettings(1, absentPriority));
        assertEquals(0, service.revision(1));
    }

    @Test
    void disablingLastTagOnlyHidesGroupingEntry() {
        RankSettings input = service.settings(1);
        input.getTags().get(0).setEnabled(false);
        service.saveSettings(1, input);
        assertTrue(service.tags(1, true).isEmpty());
        assertEquals(2, service.boards(1, true).size());
        assertEquals(2, service.associations(1, List.of(101L)).size());
    }

    @Test
    void validateGoodsRejectsMissingAndDuplicateEachKey() {
        RankGoodsRef a = service.rankedGoods(1, 10).get(0);
        RankGoodsRef b = service.rankedGoods(1, 10).get(1);
        expect("INVALID_GOODS", () -> service.validateGoods(null));
        expect("INVALID_GOODS", () -> service.validateGoods(Arrays.asList(a, null)));
        b.setGoodsId(a.getGoodsId());
        expect("INVALID_GOODS", () -> service.validateGoods(List.of(a, b)));
        b.setGoodsId(101L);
        b.setGoodsSn(" a ");
        expect("INVALID_GOODS", () -> service.validateGoods(List.of(a, b)));
        b.setGoodsSn("B");
        b.setSortOrder(a.getSortOrder());
        expect("INVALID_GOODS", () -> service.validateGoods(List.of(a, b)));
        b.setSortOrder(null);
        expect("INVALID_GOODS", () -> service.validateGoods(List.of(b)));
    }

    @Test
    void concurrentSameVersionHasExactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Callable<String> work = () -> {
            start.await();
            try {
                service.mutate(1, 0L, calls::incrementAndGet);
                return "OK";
            } catch (RankException error) {
                return error.code;
            }
        };
        try {
            Future<String> first = pool.submit(work);
            Future<String> second = pool.submit(work);
            start.countDown();
            List<String> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertTrue(results.contains("OK"));
            assertTrue(results.contains("VERSION_CONFLICT"));
            assertEquals(1, calls.get());
            assertEquals(1, service.revision(1));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void genericCrudAndStoreConstrainedUpdates() {
        RankTag tag = new RankTag();
        tag.setStoreId(1L);
        tag.setName("测试");
        tag.setEnabled(true);
        tag.setSortOrder(2);
        tags.add(tag);
        assertNotNull(tag.getId());
        tag.setStoreId(2L);
        tag.setName("不可跨店更新");
        tags.update(tag);
        assertEquals("测试", tags.queryFirst("id", tag.getId(), "storeId", 1L).getName());
        RankBoard board = new RankBoard();
        board.setStoreId(3L);
        board.setTitle("草稿");
        board.setIsProd(false);
        board.setSortOrder(1);
        board.setAssociationPriority(1);
        boards.add(board);
        assertNotNull(board.getId());
        RankGoodsRef ref = new RankGoodsRef();
        ref.setStoreId(3L);
        ref.setParentId(board.getId());
        ref.setGoodsId(300L);
        ref.setGoodsSn("草稿");
        ref.setSortOrder(1);
        goods.add(ref);
        assertNotNull(ref.getId());
        assertEquals(1, service.rankedGoods(3, board.getId()).size());
    }

    @Test
    void fullTagReplacementWithZeroIdDeletesOldMemberships() {
        RankSettings input = service.settings(1);
        RankTag replacement = new RankTag();
        replacement.setId(0L);
        replacement.setName("替换标签");
        replacement.setEnabled(true);
        replacement.setBoardIds(List.of(11L, 10L));
        input.setTags(List.of(replacement));
        RankSettings saved = service.saveSettings(1, input);
        assertEquals(1, saved.getTags().size());
        assertEquals("替换标签", saved.getTags().get(0).getName());
        assertNotEquals(1L, saved.getTags().get(0).getId());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_tag_board WHERE tag_id=1", Integer.class));
        assertEquals(1, service.tags(2, true).size());
    }

    @Test
    void genericBatchQueriesAndDeletesBindIds() {
        RankTag first = new RankTag();
        first.setStoreId(3L);
        first.setName("甲");
        first.setEnabled(true);
        first.setSortOrder(1);
        RankTag second = new RankTag();
        second.setStoreId(3L);
        second.setName("乙");
        second.setEnabled(true);
        second.setSortOrder(2);
        service.mutate(3, 0L, () -> {
            tags.addBatch(List.of(first, second));
            return null;
        });
        assertEquals(2, tags.queryByIds(List.of(first.getId(), second.getId())).size());
        first.setName("更新甲");
        second.setName("更新乙");
        service.mutate(3, 1L, () -> {
            tags.updateBatch(List.of(first, second));
            return null;
        });
        assertEquals("更新甲", tags.getById(first.getId()).getName());
        service.mutate(3, 2L, () -> {
            tags.deleteByArgs("storeId", 3L, "id", first.getId());
            tags.deleteById(second.getId());
            return null;
        });
        assertTrue(service.tags(3, false).isEmpty());
    }

    @Test
    void deletedCandidateIsHiddenWithoutChangingOtherRanksOrBoardVisibility() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        List<RankGoodsRef> remaining = service.associations(1, List.of(100L, 101L));
        assertEquals(2, remaining.size());
        assertEquals(101L, remaining.get(0).getGoodsId());
        assertEquals(2, remaining.get(0).getBoardRank());
        assertEquals(10L, remaining.get(0).getParentId());
        db.update("UPDATE dodo_rank_tag SET enabled=0 WHERE id=1");
        assertEquals(2, service.associations(1, List.of(101L)).size());
    }

    @Test
    void tagChangesRemainAllowedAndPublishingStillRequiresEnabledTag() {
        db.update("UPDATE dodo_theme_top_list SET is_prod=0 WHERE id=10");
        RankSettings input = service.settings(1);
        input.getTags().get(0).setEnabled(false);
        service.saveSettings(1, input);
        expect("INVALID_PUBLISHED_BOARD", () -> service.mutate(1, 1L, () -> {
            db.update("UPDATE dodo_theme_top_list SET is_prod=1 WHERE id=10");
            return null;
        }));
        assertFalse(service.requireBoard(1, 10).getIsProd());
        assertEquals(1, service.revision(1));
        service.mutate(1, 1L, () -> {
            db.update("UPDATE dodo_rank_tag SET enabled=1 WHERE id=1");
            db.update("UPDATE dodo_theme_top_list SET is_prod=1 WHERE id=10");
            return null;
        });
        RankSettings removeGroups = service.settings(1);
        removeGroups.setTags(List.of());
        service.saveSettings(1, removeGroups);
        assertTrue(service.tags(1, false).isEmpty());
        assertEquals(2, service.associations(1, List.of(101L)).size());
    }

    @Test
    void legacyCreateInitializesPriorityAndGeneralMembershipWithoutNewFields() {
        RankBoard board = new RankBoard();
        board.setStoreId(1L);
        board.setTitle("旧接口创建");
        board.setIsProd(true);
        service.mutate(1, 0L, () -> {
            boards.add(board);
            RankGoodsRef relation = new RankGoodsRef();
            relation.setStoreId(1L);
            relation.setParentId(board.getId());
            relation.setGoodsId(100L);
            relation.setGoodsSn("A");
            relation.setSortOrder(1);
            goods.add(relation);
            return service.initializeBoard(1, board.getId());
        });
        RankBoard saved = service.requireBoard(1, board.getId());
        assertEquals(3, saved.getAssociationPriority());
        assertEquals(3, saved.getSortOrder());
        assertTrue(service.tags(1, true).get(0).getBoardIds().contains(saved.getId()));
        assertEquals(1, service.revision(1));
        expect("INVALID_ARGUMENT", () -> service.initializeBoard(1, saved.getId()));
    }

    @Test
    void legacyNullVersionSerializesAndUsesLockedRevision() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> work = () -> {
            start.await();
            return service.mutate(1, null, () -> null);
        };
        try {
            Future<Void> first = pool.submit(work);
            Future<Void> second = pool.submit(work);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertEquals(2, service.revision(1));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void draftPrioritiesCanOverlapPublishedPrioritiesUntilPublishing() {
        service.mutate(1, 0L, () -> {
            db.update("UPDATE dodo_theme_top_list SET is_prod=0,association_priority=1 WHERE id=11");
            return null;
        });
        assertEquals(1, service.requireBoard(1, 11).getAssociationPriority());
        expect("INVALID_PUBLISHED_BOARD", () -> service.mutate(1, 1L, () -> {
            db.update("UPDATE dodo_theme_top_list SET is_prod=1 WHERE id=11");
            return null;
        }));
        assertEquals(1, service.revision(1));
        assertFalse(service.requireBoard(1, 11).getIsProd());
    }

    @Test
    void legacyInitializerPreservesEnabledCustomMembershipWithoutAddingGeneral() {
        db.update("INSERT INTO dodo_rank_tag(id,store_id,name,sort_order,enabled) VALUES (9,1,'自定义',2,1)");
        db.update("DELETE FROM dodo_rank_tag_board WHERE board_id=10 AND store_id=1");
        db.update("INSERT INTO dodo_rank_tag_board VALUES (1,9,10)");
        service.mutate(1, null, () -> service.initializeBoard(1, 10));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_tag_board WHERE tag_id=1 AND board_id=10", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_tag_board WHERE tag_id=9 AND board_id=10", Integer.class));
    }

    @Test
    void legacyPublishingAddsActiveGeneralWithoutEnablingCustomTag() {
        db.update("INSERT INTO dodo_rank_tag(id,store_id,name,sort_order,enabled) VALUES (9,1,'关闭的自定义',2,0)");
        db.update("DELETE FROM dodo_rank_tag_board WHERE board_id=10 AND store_id=1");
        db.update("INSERT INTO dodo_rank_tag_board VALUES (1,9,10)");
        db.update("UPDATE dodo_theme_top_list SET is_prod=0 WHERE id=10");
        db.update("UPDATE dodo_rank_tag SET enabled=0 WHERE id=1");
        service.mutate(1, null, () -> {
            db.update("UPDATE dodo_theme_top_list SET is_prod=1 WHERE id=10");
            return service.initializeBoard(1, 10);
        });
        assertEquals(0, db.queryForObject("SELECT enabled FROM dodo_rank_tag WHERE id=9", Integer.class));
        assertEquals(1, db.queryForObject("SELECT enabled FROM dodo_rank_tag WHERE id=1", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_tag_board WHERE board_id=10 AND store_id=1", Integer.class));
    }

    @Test
    void backgroundAndTagChangesIgnoreUnchangedDeletedProduct() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        RankSettings background = service.settings(1);
        background.setHomepageBackgroundImage("新背景");
        assertEquals(1, service.saveSettings(1, background).getVersion());
        assertEquals("新背景", service.settings(1).getHomepageBackgroundImage());
        RankSettings grouping = service.settings(1);
        grouping.getTags().get(0).setEnabled(false);
        service.saveSettings(1, grouping);
        assertTrue(service.tags(1, true).isEmpty());
        assertEquals(2, service.boards(1, true).size());
        assertEquals(2, service.associations(1, List.of(101L)).get(0).getBoardRank());
        assertEquals(2, service.revision(1));
    }

    @Test
    void editingOtherPublishedBoardDoesNotValidateUntouchedStaleBoard() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        service.mutate(1, 0L, () -> {
            RankBoard other = service.requireBoard(1, 11);
            other.setTitle("正常榜单修订");
            boards.update(other);
            return null;
        });
        assertEquals("正常榜单修订", service.requireBoard(1, 11).getTitle());
        assertEquals(2, service.rankedGoods(1, 10).size());
        assertEquals(1, service.revision(1));
    }

    @Test
    void editingPublishedBoardConfigurationWithStaleGoodsRollsBack() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        expect("INVALID_GOODS", () -> service.mutate(1, 0L, () -> {
            RankBoard changed = service.requireBoard(1, 10);
            changed.setTitle("不能保存");
            boards.update(changed);
            return null;
        }));
        assertEquals("原始标题", service.requireBoard(1, 10).getTitle());
        RankSettings settings = service.settings(1);
        settings.getBoards().get(0).setRecommendPosition(1);
        RankException invalidGoods = assertThrows(RankException.class, () -> service.saveSettings(1, settings));
        assertEquals("INVALID_GOODS", invalidGoods.getCode());
        assertTrue(invalidGoods.getMessage().contains("ID=10"));
        assertTrue(invalidGoods.getMessage().contains("商品ID=100"));
        assertTrue(invalidGoods.getMessage().contains("SKU="));
        assertNull(service.requireBoard(1, 10).getRecommendPosition());
        assertEquals(0, service.revision(1));
    }

    @Test
    void replacingOrEditingInvalidGoodsStillFailsStrictValidation() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        expect("INVALID_GOODS", () -> service.mutate(1, 0L, () -> {
            RankGoodsRef changed = service.rankedGoods(1, 10).get(0);
            changed.setPromotePrice(9.9);
            goods.update(changed);
            return null;
        }));
        assertNull(service.rankedGoods(1, 10).get(0).getPromotePrice());
        expect("INVALID_GOODS", () -> service.mutate(1, 0L, () -> {
            RankGoodsRef changed = service.rankedGoods(1, 10).get(0);
            changed.setGoodsId(200L);
            changed.setGoodsSn("C");
            goods.update(changed);
            return null;
        }));
        assertEquals(100L, service.rankedGoods(1, 10).get(0).getGoodsId());
        assertEquals(0, service.revision(1));
    }

    @Test
    void reimportingIdenticalInvalidGoodsWithNewRelationIdIsValidated() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        expect("INVALID_GOODS", () -> service.mutate(1, 0L, () -> {
            RankGoodsRef reimported = service.rankedGoods(1, 10).get(0);
            goods.deleteByArgs("storeId", 1L, "id", reimported.getId());
            reimported.setId(null);
            goods.add(reimported);
            return null;
        }));
        assertEquals(1L, service.rankedGoods(1, 10).get(0).getId());
        assertEquals(0, service.revision(1));
    }

    @Test
    void removingInvalidGoodsRepairsPublishedBoard() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        service.mutate(1, 0L, () -> {
            goods.deleteByArgs("storeId", 1L, "id", 1L);
            return null;
        });
        assertEquals(1, service.rankedGoods(1, 10).size());
        assertEquals(101L, service.rankedGoods(1, 10).get(0).getGoodsId());
        assertEquals(1, service.associations(1, List.of(101L)).get(0).getBoardRank());
        assertEquals(1, service.revision(1));
    }

    @Test
    void invalidBoardCanBeUnpublishedWithoutGoodsOrTagValidation() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        db.update("UPDATE dodo_rank_tag SET enabled=0 WHERE id=1");
        service.mutate(1, 0L, () -> {
            RankBoard bad = service.requireBoard(1, 10);
            bad.setIsProd(false);
            boards.update(bad);
            return null;
        });
        assertFalse(service.requireBoard(1, 10).getIsProd());
        assertEquals(1, service.boards(1, true).size());
        assertEquals(1, service.revision(1));
    }

    @Test
    void editingPublishedBoardStillRequiresEnabledTag() {
        RankSettings grouping = service.settings(1);
        grouping.setTags(List.of());
        service.saveSettings(1, grouping);
        expect("INVALID_PUBLISHED_BOARD", () -> service.mutate(1, 1L, () -> {
            RankBoard changed = service.requireBoard(1, 10);
            changed.setTitle("缺少标签");
            boards.update(changed);
            return null;
        }));
        assertEquals("原始标题", service.requireBoard(1, 10).getTitle());
        assertEquals(1, service.revision(1));
    }

    @Test
    void unchangedGlobalPriorityOrSlotCollisionsStillBlockBackgroundSave() {
        db.update("UPDATE dodo_goods SET is_delete=1 WHERE goods_id=100");
        db.update("UPDATE dodo_theme_top_list SET association_priority=1 WHERE id=11");
        RankSettings priorities = service.settings(1);
        priorities.setHomepageBackgroundImage("不能提交");
        expect("INVALID_PUBLISHED_BOARD", () -> service.saveSettings(1, priorities));
        db.update("UPDATE dodo_theme_top_list SET association_priority=2 WHERE id=11");
        db.update("UPDATE dodo_theme_top_list SET recommend_position=1 WHERE store_id=1");
        RankSettings slots = service.settings(1);
        slots.setHomepageBackgroundImage("不能提交");
        expect("INVALID_PUBLISHED_BOARD", () -> service.saveSettings(1, slots));
        assertNull(service.settings(1).getHomepageBackgroundImage());
        assertEquals(0, service.revision(1));
    }

    @Test
    void missingConfigRevisionIsZeroWithoutWritingOrResettingExistingVersion() {
        db.update("DELETE FROM dodo_rank_config WHERE store_id=3");
        assertEquals(0, service.revision(3));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_config WHERE store_id=3", Integer.class));
        service.mutate(1, 0L, () -> null);
        assertEquals(1, service.revision(1));
        assertEquals(0, service.revision(999));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_config WHERE store_id=999", Integer.class));
    }

    @Test
    void newlyCreatedStoreSettingsInitializeEmptyConfigurationAndCanSave() {
        db.update("INSERT INTO store VALUES (4)");
        assertEquals(0, service.revision(4));
        RankSettings initial = service.settings(4);
        assertEquals(0, initial.getVersion());
        assertNull(initial.getHomepageBackgroundImage());
        assertTrue(initial.getBoards().isEmpty());
        assertTrue(initial.getTags().isEmpty());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_config WHERE store_id=4", Integer.class));
        initial.setHomepageBackgroundImage("新门店背景");
        assertEquals(1, service.saveSettings(4, initial).getVersion());
        RankSettings saved = service.settings(4);
        assertEquals(1, saved.getVersion());
        assertEquals("新门店背景", saved.getHomepageBackgroundImage());
        assertEquals(1, service.revision(4));
        assertEquals(0, service.revision(1));
    }

    @Test
    void unknownStoreSettingsDoNotCreateConfiguration() {
        expect("NOT_FOUND", () -> service.settings(999));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dodo_rank_config WHERE store_id=999", Integer.class));
    }

    @Test
    void missingSchemaRemainsAnErrorForRevisionAndSettings() {
        db.execute("DROP TABLE dodo_rank_config");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.revision(1));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.settings(1));
    }

    @Test
    void realPreflightDetectsDuplicateSortAndSkuBeforeMigration() throws Exception {
        String migration = resource("db/test/05-rank.sql");
        String sql = migration.substring(migration.indexOf("-- BEGIN RANK_PREFLIGHT") + "-- BEGIN RANK_PREFLIGHT".length(),
                migration.indexOf("-- END RANK_PREFLIGHT"));
        assertTrue(db.queryForList(sql).isEmpty());
        db.update("UPDATE dodo_theme_top_goods_ref SET sort_order=10,goods_sn='A' WHERE id=2");
        assertFalse(db.queryForList(sql).isEmpty());
    }
}
