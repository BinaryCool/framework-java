package com.dodosupermarket.rank;

import com.dodosupermarket.rank.bean.dto.RankGoodsIssue;
import com.dodosupermarket.rank.bean.dto.RankSettings;
import com.dodosupermarket.rank.bean.po.RankBoard;
import com.dodosupermarket.rank.bean.po.RankGoodsRef;
import com.dodosupermarket.rank.bean.po.RankTag;
import com.dodosupermarket.rank.dao.RankDAO;
import com.dodosupermarket.rank.service.RankTagService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 共享榜单读写入口；写操作按门店串行，校验失败与业务变更一起回滚。 */
@Service
public class RankService {
    private final RankDAO dao;
    private final RankTagService tagService;
    private final TransactionTemplate transactions;

    public RankService(RankDAO dao, RankTagService tagService,
                       @Qualifier("transactionManager") PlatformTransactionManager manager) {
        this.dao = dao;
        this.tagService = tagService;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public RankSettings settings(long storeId) {
        // 同一事务内取得配置与成员，防止读取到保存过程中的混合快照。
        return primary(() -> transactions.execute(status -> {
            // 迁移后新增门店按需补齐初始配置；Mapper 仅从真实门店插入，不为未知门店建记录。
            dao.ensureConfig(storeId);
            RankSettings result = dao.lockConfig(storeId);
            if (result == null) {
                throw error("NOT_FOUND", "门店不存在");
            }
            result.setBoards(boards(storeId, false));
            result.setTags(tags(storeId, false));
            return result;
        }));
    }

    public long revision(long storeId) {
        RankSettings result = primary(() -> dao.config(storeId));
        // 旧接口共用此缓存版本：尚未初始化配置的门店使用初始版本，数据库异常仍直接抛出。
        return result == null ? 0L : result.getVersion();
    }

    /** 返回与配置版本一致的问题清单，不从客户端错误文案解析待删除对象。 */
    public Map<String, Object> goodsIssues(long storeId) {
        return primary(() -> transactions.execute(status -> {
            RankSettings config = settings(storeId);
            return Map.of("version", config.getVersion(), "issues", dao.goodsIssues(storeId));
        }));
    }

    /** 只软删除用户已查看且仍失效的关系；并发恢复、版本冲突和发布校验失败均整批回滚。 */
    public Map<String, Object> removeGoodsIssues(long storeId, Long version, List<Long> refIds) {
        if (version == null || refIds == null || refIds.isEmpty() || refIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(refIds).size() != refIds.size()) {
            throw error("INVALID_ARGUMENT", "必须提交配置版本和不重复的问题关系编号");
        }
        return mutate(storeId, version, () -> {
            Map<Long, RankGoodsIssue> current = dao.goodsIssues(storeId).stream()
                    .collect(Collectors.toMap(RankGoodsIssue::getRefId, Function.identity()));
            if (!current.keySet().containsAll(refIds)) {
                throw error("ISSUES_CHANGED", "问题商品已变化或恢复有效，请重新检查后移除");
            }
            Set<Long> affectedBoards = new HashSet<>();
            for (Long refId : refIds) {
                if (dao.removeInvalidGoods(storeId, refId) != 1) {
                    throw error("ISSUES_CHANGED", "问题商品已变化或恢复有效，请重新检查后移除");
                }
                affectedBoards.add(current.get(refId).getBoardId());
            }
            Set<Long> offlineBoards = new HashSet<>();
            for (Long id : affectedBoards) {
                dao.refreshGoodsCount(storeId, id);
                RankBoard board = requireBoard(storeId, id);
                if (Boolean.TRUE.equals(board.getIsProd()) && dao.rankedGoods(storeId, id).isEmpty()) {
                    // 清空后的榜单不能继续发布，通过门店隔离的具名 Mapper 解除推荐绑定。
                    dao.offlineEmptyBoard(storeId, id);
                    offlineBoards.add(id);
                }
            }
            RankSettings result = settings(storeId);
            result.setVersion(version + 1);
            return Map.of("settings", result, "removedCount", refIds.size(), "offlineBoardIds", offlineBoards);
        });
    }

    /** 用户端只读取背景和版本，不复用管理端补配置、加写锁的完整设置快照。 */
    public RankSettings readConfig(long storeId) {
        RankSettings result = primary(() -> dao.config(storeId));
        if (result == null) {
            result = new RankSettings();
            result.setVersion(0L);
        }
        return result;
    }

    public List<RankBoard> boards(long storeId, boolean publishedOnly) {
        return primary(() -> dao.boards(storeId, publishedOnly));
    }

    public List<RankTag> tags(long storeId, boolean enabledOnly) {
        return primary(() -> dao.tags(storeId, enabledOnly));
    }

    public List<RankGoodsRef> rankedGoods(long storeId, long boardId) {
        requireBoard(storeId, boardId);
        return primary(() -> dao.rankedGoods(storeId, boardId));
    }

    /** 合并读取页面内已发布榜单的原始前三名；商品可见性过滤必须在排名之后。 */
    public List<RankGoodsRef> previewGoods(long storeId, List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Collections.emptyList();
        }
        return primary(() -> dao.previewGoods(storeId, boardIds));
    }

    public List<RankGoodsRef> associations(long storeId, List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (goodsIds.stream().anyMatch(Objects::isNull)) {
            throw error("INVALID_ARGUMENT", "商品编号不能为空");
        }
        return primary(() -> dao.associations(storeId, goodsIds));
    }

    public RankBoard requireBoard(long storeId, long id) {
        return boards(storeId, false).stream().filter(board -> Objects.equals(board.getId(), id))
                .findFirst().orElseThrow(() -> error("NOT_FOUND", "榜单不存在或不属于当前门店"));
    }

    /** 全量覆盖标签及其关系，仅更新榜单配置白名单字段。 */
    public RankSettings saveSettings(long storeId, RankSettings snapshot) {
        if (snapshot == null || snapshot.getVersion() == null || snapshot.getBoards() == null || snapshot.getTags() == null) {
            throw error("INVALID_SETTINGS", "必须提交完整榜单和标签快照");
        }
        return mutate(storeId, snapshot.getVersion(), () -> {
            List<RankBoard> existing = boards(storeId, false);
            Set<Long> ids = existing.stream().map(RankBoard::getId).collect(Collectors.toSet());
            Set<Long> submitted = new HashSet<>();
            int order = 1;
            for (RankBoard board : snapshot.getBoards()) {
                if (board == null || board.getId() == null || !ids.contains(board.getId())
                        || !submitted.add(board.getId()) || board.getAssociationPriority() == null
                        || board.getAssociationPriority() <= 0
                        || (board.getStoreId() != null && board.getStoreId() != storeId)) {
                    throw error("INVALID_SETTINGS", "榜单编号或关联优先级不合法");
                }
                board.setSortOrder(order++);
                dao.boardConfig(storeId, board);
            }
            if (!ids.equals(submitted)) {
                throw error("INVALID_SETTINGS", "榜单快照必须包含当前门店全部榜单");
            }
            Map<Long, RankTag> oldTags = tags(storeId, false).stream()
                    .collect(Collectors.toMap(RankTag::getId, tag -> tag));
            Set<Long> retained = new HashSet<>();
            Set<String> names = new HashSet<>();
            dao.clearTagBoards(storeId);
            order = 1;
            for (RankTag tag : snapshot.getTags()) {
                if (tag == null || tag.getName() == null || tag.getName().trim().isEmpty()
                        || tag.getName().trim().length() > 100
                        || tag.getEnabled() == null || tag.getBoardIds() == null
                        || !names.add(tag.getName().trim())
                        || (tag.getStoreId() != null && tag.getStoreId() != storeId)) {
                    throw error("INVALID_SETTINGS", "标签名称、启用状态和榜单关系必填且名称不能重复");
                }
                Set<Long> memberships = new HashSet<>(tag.getBoardIds());
                if (memberships.size() != tag.getBoardIds().size() || !ids.containsAll(memberships)) {
                    throw error("INVALID_SETTINGS", "标签不能绑定其他门店、未知或重复榜单");
                }
                tag.setStoreId(storeId);
                tag.setSortOrder(order++);
                tag.setName(tag.getName().trim());
                if (tag.getId() == null || tag.getId() == 0) {
                    tag.setId(null);
                    tagService.add(tag);
                } else {
                    if (!oldTags.containsKey(tag.getId()) || !retained.add(tag.getId())) {
                        throw error("INVALID_SETTINGS", "标签编号重复或不属于当前门店");
                    }
                    tagService.update(tag);
                }
                for (Long boardId : memberships) {
                    dao.link(storeId, tag.getId(), boardId);
                }
            }
            for (Long oldId : oldTags.keySet()) {
                if (!retained.contains(oldId)) {
                    dao.deleteTag(storeId, oldId);
                }
            }
            dao.background(storeId, snapshot.getHomepageBackgroundImage());
            // mutate 在回调结束后递增版本，返回的对象需反映最终提交版本。
            RankSettings result = settings(storeId);
            result.setVersion(snapshot.getVersion() + 1);
            return result;
        });
    }

    /** 行锁先于版本检查；并发请求等待后重新读取版本，避免覆盖其他管理员变更。 */
    public <T> T mutate(long storeId, Long expectedVersion, Supplier<T> action) {
        if (storeId <= 0 || (expectedVersion != null && expectedVersion < 0) || action == null) {
            throw error("INVALID_ARGUMENT", "门店、配置版本与操作不能为空");
        }
        return primary(() -> transactions.execute(status -> {
            dao.ensureConfig(storeId);
            RankSettings config = dao.lockConfig(storeId);
            if (config == null) {
                throw error("NOT_FOUND", "门店不存在");
            }
            if (expectedVersion != null && !expectedVersion.equals(config.getVersion())) {
                throw error("VERSION_CONFLICT", "榜单配置已更新，请刷新后重试");
            }
            Map<Long, PublishedBoardState> before = snapshotPublished(storeId);
            T result = action.get();
            validatePublished(storeId, before);
            if (dao.bump(storeId, config.getVersion()) != 1) {
                throw error("VERSION_CONFLICT", "榜单配置版本已变化");
            }
            return result;
        }));
    }

    /**
     * 兼容旧创建接口：在 mutate 回调中创建原记录后调用，自动补齐优先级及综合分组。
     * 与旧业务创建同事务，初始化失败时原记录及版本一起回滚。
     */
    public RankBoard initializeBoard(long storeId, long boardId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw error("INVALID_ARGUMENT", "榜单初始化必须在 mutate 回调内执行");
        }
        // 与 mutate 使用相同行锁，即使外层显式事务调用也不会并发分配相同优先级。
        if (dao.lockConfig(storeId) == null) {
            throw error("NOT_FOUND", "门店榜单配置不存在");
        }
        RankBoard board = requireBoard(storeId, boardId);
        List<RankBoard> existing = boards(storeId, false);
        if (board.getAssociationPriority() == null || board.getAssociationPriority() <= 0) {
            int maximum = existing.stream().map(RankBoard::getAssociationPriority).filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(0);
            if (maximum == Integer.MAX_VALUE) {
                throw error("INVALID_SETTINGS", "榜单优先级已达到上限");
            }
            board.setAssociationPriority(maximum + 1);
        }
        if (board.getSortOrder() == null) {
            int maximum = existing.stream().map(RankBoard::getSortOrder).filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(0);
            board.setSortOrder(Math.addExact(maximum, 1));
        }
        dao.boardConfig(storeId, board);
        List<RankTag> storeTags = tags(storeId, false);
        List<RankTag> memberships = storeTags.stream().filter(tag -> tag.getBoardIds().contains(boardId))
                .collect(Collectors.toList());
        if (memberships.stream().anyMatch(tag -> Boolean.TRUE.equals(tag.getEnabled()))
                || (!memberships.isEmpty() && !Boolean.TRUE.equals(board.getIsProd()))) {
            return board;
        }
        RankTag general = storeTags.stream().filter(tag -> "综合".equals(tag.getName()))
                .findFirst().orElse(null);
        if (general == null) {
            general = new RankTag();
            general.setStoreId(storeId);
            general.setName("综合");
            general.setEnabled(true);
            general.setSortOrder(storeTags.stream().map(RankTag::getSortOrder).filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(0) + 1);
            tagService.add(general);
        } else if (!Boolean.TRUE.equals(general.getEnabled())) {
            // 只补充默认入口，不擅自开启管理员关闭的自定义标签。
            general.setEnabled(true);
            tagService.update(general);
        }
        if (!dao.tagBoards(storeId, general.getId()).contains(boardId)) {
            dao.link(storeId, general.getId(), boardId);
        }
        return board;
    }

    /** 商品主键、SKU 和排序均必须唯一；库存为零不影响榜单资格。 */
    public void validateGoods(List<RankGoodsRef> goods) {
        if (goods == null) {
            throw error("INVALID_GOODS", "榜单商品列表不能为空");
        }
        Set<Long> ids = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (RankGoodsRef ref : goods) {
            if (ref == null || ref.getGoodsId() == null || ref.getGoodsId() <= 0
                    || ref.getGoodsSn() == null || ref.getGoodsSn().trim().isEmpty()
                    || ref.getSortOrder() == null || !ids.add(ref.getGoodsId())
                    || !skus.add(ref.getGoodsSn().trim().toLowerCase(Locale.ROOT))
                    || !orders.add(ref.getSortOrder())) {
                throw error("INVALID_GOODS", "商品编号、SKU、排序必填且不能重复");
            }
        }
    }

    /**
     * 在门店行锁内冻结业务配置值，而非持有可变 PO 引用。
     * 商品目录由其他业务维护，目录状态变化不等于本次修改了榜单商品。
     */
    private Map<Long, PublishedBoardState> snapshotPublished(long storeId) {
        Map<Long, PublishedBoardState> result = new HashMap<>();
        for (RankBoard board : boards(storeId, true)) {
            result.put(board.getId(), new PublishedBoardState(boardConfiguration(board),
                    goodsConfiguration(dao.rankedGoods(storeId, board.getId()))));
        }
        return result;
    }

    private List<Object> boardConfiguration(RankBoard board) {
        return Arrays.asList(board.getStoreId(), board.getTitle(), board.getSortOrder(), board.getIsProd(),
                board.getAssociationPriority(), board.getRecommendPosition(), board.getTopUrl(),
                board.getBackgroundImage(), board.getBackgroundColor(), board.getGoodsCount());
    }

    private List<List<Object>> goodsConfiguration(List<RankGoodsRef> goods) {
        // 保留关系主键，删除后重新导入同样商品仍属于本次编辑，必须严格重新校验。
        return goods.stream().map(ref -> Arrays.<Object>asList(ref.getId(), ref.getStoreId(), ref.getParentId(),
                ref.getGoodsId(), ref.getGoodsSn(), ref.getSortOrder(), ref.getPromotePrice(), ref.getLimitPerOrder()))
                .collect(Collectors.toList());
    }

    private static final class PublishedBoardState {
        private final List<Object> configuration;
        private final List<List<Object>> goods;

        private PublishedBoardState(List<Object> configuration, List<List<Object>> goods) {
            this.configuration = configuration;
            this.goods = goods;
        }
    }

    /** 全局检查优先级与推荐位，仅对新发布或业务配置发生变化的已发布榜单严格检查商品和标签。 */
    private void validatePublished(long storeId, Map<Long, PublishedBoardState> before) {
        Set<Integer> priorities = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        Set<Long> tagged = tags(storeId, true).stream().flatMap(tag -> tag.getBoardIds().stream())
                .collect(Collectors.toSet());
        for (RankBoard board : boards(storeId, false)) {
            boolean published = Boolean.TRUE.equals(board.getIsProd());
            Integer position = board.getRecommendPosition();
            if (position != null && (!published || position < 1 || position > 3 || !positions.add(position))) {
                throw error("INVALID_PUBLISHED_BOARD", "推荐位只能绑定已发布榜单，且必须为不重复的 1 至 3");
            }
            // 关联优先级仅在已发布榜单之间唯一，草稿允许暂存相同优先级。
            Integer priority = board.getAssociationPriority();
            if ((published && priority == null)
                    || (priority != null && (priority <= 0 || (published && !priorities.add(priority))))) {
                throw error("INVALID_PUBLISHED_BOARD", "门店榜单关联优先级必须为唯一正整数");
            }
            if (!published) {
                continue;
            }
            List<RankGoodsRef> goods = dao.rankedGoods(storeId, board.getId());
            PublishedBoardState previous = before.get(board.getId());
            if (previous != null && previous.configuration.equals(boardConfiguration(board))
                    && previous.goods.equals(goodsConfiguration(goods))) {
                // 未编辑榜单中的失效商品只在展示时隐藏，不能阻塞背景、标签或其他榜单的修复。
                continue;
            }
            validateGoods(goods);
            if (goods.isEmpty() || !tagged.contains(board.getId())) {
                throw error("INVALID_PUBLISHED_BOARD", "已发布榜单必须含商品及至少一个启用标签");
            }
            for (RankGoodsRef ref : goods) {
                if (dao.validGoods(storeId, ref.getGoodsId(), ref.getGoodsSn()) != 1) {
                    throw error("INVALID_GOODS", "榜单「" + board.getTitle() + "」（ID=" + board.getId()
                            + "）的商品校验失败：商品ID=" + ref.getGoodsId() + "，SKU=" + ref.getGoodsSn()
                            + "；商品不存在、已停用、已删除、SKU不匹配或不属于当前门店");
                }
            }
        }
    }

    /** 在事务取得连接之前固定主库路由，结束后恢复调用方线程上下文。 */
    private <T> T primary(Supplier<T> work) {
        Boolean force = ConnectionHolder.FORCE_WRITE.get();
        String connection = ConnectionHolder.CURRENT_CONNECTION.get();
        String source = DataSourceHolder.CURRENT_DATASOURCE.get();
        ConnectionHolder.FORCE_WRITE.set(true);
        ConnectionHolder.CURRENT_CONNECTION.set(ConnectionHolder.WRITE);
        DataSourceHolder.CURRENT_DATASOURCE.set(DataSourceHolder.DEFAULT);
        try {
            return work.get();
        } finally {
            restore(ConnectionHolder.FORCE_WRITE, force);
            restore(ConnectionHolder.CURRENT_CONNECTION, connection);
            restore(DataSourceHolder.CURRENT_DATASOURCE, source);
        }
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) {
            local.remove();
        } else {
            local.set(previous);
        }
    }

    private static RankException error(String code, String message) {
        return new RankException(code, message);
    }
}
