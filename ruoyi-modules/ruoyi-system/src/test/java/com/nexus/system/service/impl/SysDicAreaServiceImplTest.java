package com.nexus.system.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.constant.CacheNames;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.bo.SysDicAreaBo;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysDicAreaServiceImpl} 单元测试。
 * <p><b>必须保留 {@code @Tag}：</b>根 pom 的 surefire 配置为 {@code <groups>${profiles.active}</groups>}
 * （默认 local），只有带对应 {@code @Tag} 的测试才会被执行。缺少标签时 surefire 会报
 * "Tests run: 0" 并 BUILD SUCCESS —— 即静默假绿。</p>
 *
 * <p><b>Task 6 起还依赖 {@code RedisUtils} 静态桩</b>：全量树缓存是编程式的（{@code RedisUtils}
 * 直接调用），因此每个用例都在 {@code @BeforeEach} 里装一层内存版 Redis（见
 * {@link #stubRedisWithInMemoryBucket()}）。不装桩时 {@code RedisUtils.CLIENT} 为 null，
 * 服务里的缓存读写会直接 NPE。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SysDicAreaServiceImplTest {

    @Mock
    private SysDicAreaMapper baseMapper;

    @InjectMocks
    private SysDicAreaServiceImpl service;

    /** 内存版 Redis bucket：key -> value，仅用于驱动缓存读/写/删三条路径 */
    private final Map<String, Object> fakeRedis = new HashMap<>();

    private MockedStatic<RedisUtils> redisMock;

    /**
     * 预热依赖 Spring 容器的静态工具类。
     * <p><b>为什么必须做</b>：{@code MapstructUtils.CONVERTER = SpringUtils.getBean(Converter.class)}
     * 是「静态 final 字段 + Spring 容器」写法，纯 Mockito 单测没有容器，首次触碰
     * {@code MapstructUtils.convert(...)} 即抛 {@code ExceptionInInitializerError}
     * （{@code No ConfigurableListableBeanFactory or ApplicationContext injected}）；
     * 而 JVM 对失败的 {@code <clinit>} 是<b>粘性</b>的，同一 fork 内后续所有测试都会退化为
     * {@code NoClassDefFoundError}（仓库内 ruoyi-erp / ruoyi-mes 的 SpringStaticBootstrap 同因）。</p>
     * <p>做法：在 {@code SpringUtil} 静态桩窗口内主动触发该类的静态初始化，注入<b>真实的</b>
     * mapstruct-plus {@code Converter}——它会在构造时扫描 classpath 上的 {@code *MapperImpl}，
     * 因此 {@code SysDicAreaBo -> SysDicArea} 走的是编译期由 {@code @AutoMapper} 生成的
     * {@code SysDicAreaBoToSysDicAreaMapperImpl}，不是测试替身。</p>
     * <p>{@code RedisUtils.CLIENT = SpringUtils.getBean(RedissonClient.class)} 同理依赖容器：
     * 若任其在「无容器」状态下首次初始化，{@code <clinit>} 失败后
     * {@code mockStatic(RedisUtils.class)} 会报
     * "Cannot instrument class ... because it or one of its supertypes could not be initialized"，
     * 故在同一个静态桩窗口内用桩 Client 让它安全完成初始化。</p>
     */
    @BeforeAll
    static void warmUpSpringDependentUtils() {
        try (MockedStatic<SpringUtil> springMock = mockStatic(SpringUtil.class)) {
            springMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter());
            springMock.when(() -> SpringUtil.getBean(RedissonClient.class))
                .thenReturn(mock(RedissonClient.class));
            // 类字面量不触发 <clinit>，必须显式初始化；且必须在静态桩窗口内完成
            initialize("com.nexus.common.core.utils.MapstructUtils");
            initialize("com.nexus.common.redis.utils.RedisUtils");
        }
    }

    /** 触发指定类的静态初始化；类不在测试类路径上时立即失败（说明依赖缺失，不能静默放过） */
    private static void initialize(String className) {
        try {
            Class.forName(className, true, SysDicAreaServiceImplTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(className + " 不在测试类路径上，无法预热静态初始化", e);
        }
    }

    /**
     * 把 {@code RedisUtils} 换成内存实现。
     * <p>缓存是进程级单例（真实环境是 Redis），必须逐用例隔离：每个用例一份新的
     * {@link #fakeRedis}，否则用例执行顺序会改变结果。这里刻意<b>不</b>伪造语义——
     * 服务调用的是真实的 {@code getCacheObject/setCacheObject/deleteObject}，
     * 只是这三条被路由到 Map 上，因此读穿透、命中、失效都是真的在被执行。</p>
     */
    @BeforeEach
    void stubRedisWithInMemoryBucket() {
        redisMock = mockStatic(RedisUtils.class);
        redisMock.when(() -> RedisUtils.getCacheObject(anyString()))
            .thenAnswer(inv -> fakeRedis.get(inv.getArgument(0)));
        redisMock.when(() -> RedisUtils.setCacheObject(anyString(), any(), any(Duration.class)))
            .thenAnswer(inv -> {
                fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            });
        redisMock.when(() -> RedisUtils.deleteObject(anyString()))
            .thenAnswer(inv -> fakeRedis.remove(inv.getArgument(0)) != null);
    }

    @AfterEach
    void closeRedisMock() {
        if (redisMock != null) {
            redisMock.close();
        }
    }

    private static SysDicAreaVo vo(Long pkid, Long areaId, Long parentAreaId, Long layered, String name) {
        SysDicAreaVo v = new SysDicAreaVo();
        v.setPkid(pkid);
        v.setAreaId(areaId);
        v.setParentAreaId(parentAreaId);
        v.setLayered(layered);
        v.setAreaName(name);
        return v;
    }

    /** 构造写入用 BO；areaName/sort 给默认值，避免测试里重复样板 */
    private static SysDicAreaBo bo(Long pkid, Long areaId, Long parentAreaId, Long layered) {
        SysDicAreaBo b = new SysDicAreaBo();
        b.setPkid(pkid);
        b.setAreaId(areaId);
        b.setParentAreaId(parentAreaId);
        b.setLayered(layered);
        b.setAreaName("测试节点");
        b.setSort(1L);
        return b;
    }

    /** 构造库中已有行的实体（selectOne / selectById 的返回值） */
    private static SysDicArea area(Long pkid, Long areaId, Long layered, String name) {
        SysDicArea a = new SysDicArea();
        a.setPkid(pkid);
        a.setAreaId(areaId);
        a.setLayered(layered);
        a.setAreaName(name);
        return a;
    }

    /** 递归统计节点总数；若树中仍存在环，此方法会栈溢出——因此它同时是"无环"的断言 */
    private static int totalNodes(List<SysDicAreaVo> nodes) {
        int n = 0;
        for (SysDicAreaVo node : nodes) {
            n += 1 + totalNodes(node.getChildren());
        }
        return n;
    }

    /** 收集所有节点（同样会因成环而栈溢出） */
    private static void collectAreaIds(List<SysDicAreaVo> nodes, List<Long> acc) {
        for (SysDicAreaVo node : nodes) {
            acc.add(node.getAreaId());
            collectAreaIds(node.getChildren(), acc);
        }
    }

    /** 过滤条件命中非省级节点时，该节点必须作为根返回，不能被丢弃 */
    @Test
    void treeListKeepsMatchedNonRootNodes() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(3L, 330102L, 330100L, 3L, "上城区")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(1, tree.size(), "命中节点应作为根返回");
        assertEquals(330102L, tree.get(0).getAreaId());
    }

    /** 父节点不在结果集中的节点应升级为根（孤儿不再不可见） */
    @Test
    void treeListPromotesOrphansToRoot() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330100L, 330000L, 2L, "杭州市"),
            vo(2L, 330102L, 330100L, 3L, "上城区")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(1, tree.size());
        assertEquals(330100L, tree.get(0).getAreaId());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals(330102L, tree.get(0).getChildren().get(0).getAreaId());
    }

    /** 脏数据成环时不得死循环 */
    @Test
    void treeListToleratesSelfReferencingParent() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 100L, 100L, 1L, "自环节点")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(1, tree.size());
        assertEquals(100L, tree.get(0).getAreaId());
    }

    /** 混合脏数据：孤儿 + 层级不连续 + 三级链，必须一个节点都不丢 */
    @Test
    void treeListPreservesAllNodesForMixedDirtyData() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),    // 父 156 不存在 → 根
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区"),  // 三级链
            vo(4L, 331051L, 331999L, 3L, "经济开发区"), // 父 331999 不存在 → 根
            vo(5L, 330999L, 330100L, 4L, "层级不连续节点") // 父存在但层级跳级
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(5, totalNodes(tree), "不得丢失任何节点");
        List<Long> ids = new ArrayList<>();
        collectAreaIds(tree, ids);
        assertEquals(5, ids.size());
        assertTrue(ids.containsAll(List.of(330000L, 330100L, 330102L, 331051L, 330999L)));
        assertEquals(2, tree.size(), "两个孤儿应各自成为根");
    }

    /**
     * 排除必须是"整棵子树"，而不是只排除该节点。
     * <p>回归防护：若只排除自身，其后代会因父缺失被 buildTree 提升为根，
     * 从而仍然出现在"选择上级"下拉里，防环就失效了。</p>
     */
    @Test
    void treeExcludeSubtreeRemovesWholeSubtreeAtAnyDepth() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区"),
            vo(4L, 330102001L, 330102L, 4L, "清波街道"),   // 曾孙级
            vo(5L, 330102001001L, 330102001L, 5L, "某社区"), // 玄孙级
            vo(6L, 330200L, 330000L, 2L, "宁波市")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(330100L);

        List<Long> ids = new ArrayList<>();
        collectAreaIds(tree, ids);
        assertEquals(2, ids.size(), "只应剩下浙江省与宁波市，杭州市及其所有后代都必须被排除");
        assertTrue(ids.containsAll(List.of(330000L, 330200L)));
        assertFalse(ids.contains(330102L), "区县不得出现");
        assertFalse(ids.contains(330102001L), "街道不得作为顶级选项出现");
        assertFalse(ids.contains(330102001001L), "更深层后代同样不得出现");
    }

    /** 二元环：不得返回空树，也不得死循环 */
    @Test
    void treeListHandlesTwoNodeCycle() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330100L, 330102L, 2L, "杭州市"),
            vo(2L, 330102L, 330100L, 3L, "上城区")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(2, totalNodes(tree), "环上节点必须全部保留，不能被丢弃");
        assertEquals(2, tree.size(), "环上节点应被提升为根");
    }

    /** 三元环 */
    @Test
    void treeListHandlesThreeNodeCycle() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 1L, 3L, 1L, "A"),
            vo(2L, 2L, 1L, 2L, "B"),
            vo(3L, 3L, 2L, 3L, "C")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(3, totalNodes(tree));
        assertEquals(3, tree.size());
    }

    /** 深层次嵌套（4 级）必须完整重建，而非只挂一层 */
    @Test
    void treeListBuildsDeepNesting() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区"),
            vo(4L, 330102001L, 330102L, 4L, "清波街道")
        ));

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertEquals(1, tree.size());
        assertEquals(4, totalNodes(tree));
        SysDicAreaVo province = tree.get(0);
        SysDicAreaVo city = province.getChildren().get(0);
        SysDicAreaVo district = city.getChildren().get(0);
        SysDicAreaVo street = district.getChildren().get(0);
        assertEquals(330000L, province.getAreaId());
        assertEquals(330100L, city.getAreaId());
        assertEquals(330102L, district.getAreaId());
        assertEquals(330102001L, street.getAreaId());
        assertTrue(street.getChildren().isEmpty());
    }

    /** 空输入不得抛异常 */
    @Test
    void treeListReturnsEmptyForEmptyInput() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of());

        List<SysDicAreaVo> tree = service.selectSysDicAreaTreeList(new SysDicArea());

        assertTrue(tree.isEmpty());
    }

    /** 排除指定节点及其整棵子树 */
    @Test
    void treeExcludeSubtreeRemovesSelfAndDescendants() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区"),
            vo(4L, 330200L, 330000L, 2L, "宁波市")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(330100L);

        assertEquals(1, tree.size());
        assertEquals(330000L, tree.get(0).getAreaId());
        assertEquals(1, tree.get(0).getChildren().size(), "杭州市及其子树上城区都应被排除");
        assertEquals(330200L, tree.get(0).getChildren().get(0).getAreaId());
    }

    /** areaId 为空时返回全树 */
    @Test
    void treeExcludeSubtreeWithNullReturnsFullTree() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(null);

        assertEquals(1, tree.size());
    }

    /**
     * 回归防护：确认按 areaId 匹配而非 pkid。
     * <p>杭州市(areaId=330100)的父节点就是浙江省(areaId=330000)，因此排除 330000 时
     * 整棵树应被排除干净，返回空列表。</p>
     * <p>若实现误按 pkid 匹配：pkid 只有 1、2，均不等于 330000，查不到 → 返回全树
     * {浙江省→杭州市}（size=1），本断言即失败。</p>
     */
    @Test
    void treeExcludeSubtreeMatchesByAreaIdNotPkid() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(330000L);

        assertTrue(tree.isEmpty(), "按 areaId 匹配应排除浙江省及其子树杭州市，结果为全空");
    }

    /** 排除叶子节点：父节点应保留，且其余兄弟不受影响 */
    @Test
    void treeExcludeSubtreeRemovesLeafOnly() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区"),
            vo(4L, 330103L, 330100L, 3L, "下城区")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(330102L);

        assertEquals(1, tree.size());
        assertEquals(330000L, tree.get(0).getAreaId());
        List<SysDicAreaVo> cities = tree.get(0).getChildren();
        assertEquals(1, cities.size());
        assertEquals(330100L, cities.get(0).getAreaId());
        List<SysDicAreaVo> districts = cities.get(0).getChildren();
        assertEquals(1, districts.size(), "仅上城区被排除，下城区应保留");
        assertEquals(330103L, districts.get(0).getAreaId());
    }

    /** 被排除的子树内部成环时不得死循环（visited 守卫） */
    @Test
    void treeExcludeSubtreeTerminatesOnCycleInsideExcludedSubtree() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 100L, 200L, 1L, "A"),
            vo(2L, 200L, 100L, 2L, "B"),
            vo(3L, 300L, 100L, 3L, "C")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(200L);

        assertTrue(tree.isEmpty(), "A/B/C 均应在被排除的子树上");
    }

    /** areaId 不存在时静默返回全树（既有行为，此处固定下来） */
    @Test
    void treeExcludeSubtreeWithUnknownAreaIdReturnsFullTree() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市")
        ));

        List<SysDicAreaVo> tree = service.selectTreeExcludeSubtree(999999L);

        assertEquals(1, tree.size());
        assertEquals(330000L, tree.get(0).getAreaId());
        assertEquals(1, tree.get(0).getChildren().size());
    }

    /** 有下级时禁止删除，且不得调用删除 */
    @Test
    void deleteRejectsParentWithChildren() {
        SysDicArea self = new SysDicArea();
        self.setPkid(1L);
        self.setAreaId(330100L);
        self.setAreaName("杭州市");
        when(baseMapper.selectById(1L)).thenReturn(self);
        when(baseMapper.exists(any())).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteSysDicAreaByPkids(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("存在下级行政区划"));
        verify(baseMapper, never()).deleteByIds(any());
    }

    /** 无下级时批量删除 */
    @Test
    void deleteRemovesLeafNodes() {
        SysDicArea self = new SysDicArea();
        self.setPkid(3L);
        self.setAreaId(330102L);
        self.setAreaName("上城区");
        when(baseMapper.selectById(3L)).thenReturn(self);
        when(baseMapper.exists(any())).thenReturn(false);
        when(baseMapper.deleteByIds(any())).thenReturn(1);

        assertEquals(1, service.deleteSysDicAreaByPkids(new Long[]{3L}));
        verify(baseMapper).deleteByIds(eq(List.of(3L)));
    }

    /** 空数组直接返回 0，不查库 */
    @Test
    void deleteWithEmptyArrayReturnsZero() {
        assertEquals(0, service.deleteSysDicAreaByPkids(new Long[0]));
        verify(baseMapper, never()).selectById(any());
    }

    /** null 入参同样直接返回 0 */
    @Test
    void deleteWithNullReturnsZero() {
        assertEquals(0, service.deleteSysDicAreaByPkids(null));
        verify(baseMapper, never()).selectById(any());
    }

    /** 主键不存在时跳过校验（不查下级），不抛异常 */
    @Test
    void deleteSkipsUnknownPkid() {
        when(baseMapper.selectById(99L)).thenReturn(null);
        when(baseMapper.deleteByIds(any())).thenReturn(0);

        assertEquals(0, service.deleteSysDicAreaByPkids(new Long[]{99L}));
        verify(baseMapper, never()).exists(any());
    }

    /** 批量中任一项有下级 → 整体拒绝，且一行都不删 */
    @Test
    void deleteRejectsWholeBatchWhenAnyNodeHasChildren() {
        SysDicArea leaf = new SysDicArea();
        leaf.setPkid(3L);
        leaf.setAreaId(330102L);
        leaf.setAreaName("上城区");
        SysDicArea parent = new SysDicArea();
        parent.setPkid(1L);
        parent.setAreaId(330100L);
        parent.setAreaName("杭州市");
        when(baseMapper.selectById(3L)).thenReturn(leaf);
        when(baseMapper.selectById(1L)).thenReturn(parent);
        // 第一次 exists（叶子 3L）为 false → 通过校验；第二次（父节点 1L）为 true → 触发整体拒绝。
        // 必须按序返回：若恒为 true，服务会在第一个元素就抛出，1L 的 selectById 桩永不执行
        // （MockitoExtension 默认 STRICT_STUBS 会判 UnnecessaryStubbing），且"批内任一项有下级"未被真正覆盖。
        when(baseMapper.exists(any())).thenReturn(false, true);

        assertThrows(ServiceException.class,
            () -> service.deleteSysDicAreaByPkids(new Long[]{3L, 1L}));
        verify(baseMapper, never()).deleteByIds(any());
    }

    // ------------------------------------------------------------------
    // 写入校验（Task 4）：代码唯一 / 父级存在 / 层级连续 / 防环
    // ------------------------------------------------------------------

    /**
     * area_id 已被占用时拒绝新增。
     * <p>占用判定走 {@code countByAreaIdIncludeDeleted}（含已逻辑删除的行），
     * 新增时排除主键传 -1：因此断言该计数方法确实以 -1 调用，
     * 防止回归成"只查未删除行"从而漏过"删除后重建同码"。</p>
     */
    @Test
    void insertRejectsDuplicateAreaId() {
        when(baseMapper.countByAreaIdIncludeDeleted(330100L, -1L)).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330100L, 330000L, 2L)));

        assertTrue(ex.getMessage().contains("行政区划代码已存在"), "异常信息应指明代码已存在：" + ex.getMessage());
        // 必须用带类型的 any(SysDicArea.class)：BaseMapper 同时有 insert(T) 与 insert(Collection<T>)，
        // 裸 any() 会让编译器无法在两个重载间选择（编译期歧义）
        verify(baseMapper, never()).insert(any(SysDicArea.class));
    }

    /** 层级必须为父级 +1：父 1 层而子填 3 层（跳级）应拒绝 */
    @Test
    void insertRejectsNonConsecutiveLevel() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(1L, 330000L, 1L, "浙江省"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330102L, 330000L, 3L)));

        assertTrue(ex.getMessage().contains("层级必须为上级层级 + 1"), "异常信息应指明层级不连续：" + ex.getMessage());
        // 必须用带类型的 any(SysDicArea.class)：BaseMapper 同时有 insert(T) 与 insert(Collection<T>)，
        // 裸 any() 会让编译器无法在两个重载间选择（编译期歧义）
        verify(baseMapper, never()).insert(any(SysDicArea.class));
    }

    /** 父级不存在时应拒绝，而不是落一条悬空 parent_area_id（历史 9 条孤儿的成因） */
    @Test
    void insertRejectsMissingParent() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330102L, 330000L, 2L)));

        assertTrue(ex.getMessage().contains("上级行政区划不存在"), "异常信息应指明父级不存在：" + ex.getMessage());
        // 必须用带类型的 any(SysDicArea.class)：BaseMapper 同时有 insert(T) 与 insert(Collection<T>)，
        // 裸 any() 会让编译器无法在两个重载间选择（编译期歧义）
        verify(baseMapper, never()).insert(any(SysDicArea.class));
    }

    /** 顶级节点（parentAreaId=0）层级必须为 1，且无需查父级 */
    @Test
    void insertRejectsTopLevelWithNonOneLayered() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330000L, 0L, 2L)));

        assertTrue(ex.getMessage().contains("顶级行政区划的层级必须为 1"), "异常信息应指明顶级层级：" + ex.getMessage());
        verify(baseMapper, never()).selectOne(any());
        // 必须用带类型的 any(SysDicArea.class)：BaseMapper 同时有 insert(T) 与 insert(Collection<T>)，
        // 裸 any() 会让编译器无法在两个重载间选择（编译期歧义）
        verify(baseMapper, never()).insert(any(SysDicArea.class));
    }

    /**
     * 合法子节点：父级 1 层、自身 2 层 → 落库一次。
     * <p>同时断言落库实体的字段来自 BO 且 <b>不含</b> tenantId/delFlag：
     * 这两个字段 BO 未承接，越权写入的通道即被关闭。</p>
     */
    @Test
    void insertAcceptsValidChild() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(1L, 330000L, 1L, "浙江省"));
        when(baseMapper.insert(any(SysDicArea.class))).thenReturn(1);

        assertEquals(1, service.insertSysDicArea(bo(null, 330100L, 330000L, 2L)));

        ArgumentCaptor<SysDicArea> captor = ArgumentCaptor.forClass(SysDicArea.class);
        verify(baseMapper).insert(captor.capture());
        SysDicArea saved = captor.getValue();
        assertEquals(330100L, saved.getAreaId());
        assertEquals(330000L, saved.getParentAreaId());
        assertEquals(2L, saved.getLayered());
        assertEquals("测试节点", saved.getAreaName());
        assertNull(saved.getTenantId(), "BO 不承接 tenantId，越权字段必须为 null");
        assertNull(saved.getDelFlag(), "BO 不承接 delFlag，越权字段必须为 null");
    }

    /**
     * 修改时父级不能是自己。
     * <p>断言用整串相等而非 {@code contains("不能是自己")}：后者会被"不能是自己的下级"
     * 这条同样含该子串的兄弟分支满足，无法区分两条校验。</p>
     */
    @Test
    void updateRejectsSelfAsParent() {
        SysDicArea self = area(5L, 330100L, 2L, "杭州市");
        when(baseMapper.countByAreaIdIncludeDeleted(330100L, 5L)).thenReturn(0L);
        // 父级查询命中的就是自己这一行（parentAreaId == 自身 areaId）
        when(baseMapper.selectOne(any())).thenReturn(self);
        when(baseMapper.selectById(5L)).thenReturn(self);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateSysDicArea(bo(5L, 330100L, 330100L, 3L)));

        assertEquals("上级行政区划不能是自己", ex.getMessage());
        verify(baseMapper, never()).updateById(any(SysDicArea.class));
    }

    /**
     * 修改时父级不能是自己的下级（防环）。
     * <p>夹具：330102 的父级是 330100（即 330102 在 330100 的子树内）。
     * 于是 {@code collectSubtreeIds(330100, ...)} 反向索引
     * {@code {156:[330000], 330000:[330100], 330100:[330102]}} 下推后得到
     * {@code {330100, 330102}}，包含待设置的父级 330102 → 命中"不能是自己的下级"。</p>
     */
    @Test
    void updateRejectsDescendantAsParent() {
        SysDicArea self = area(2L, 330100L, 2L, "杭州市");
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(3L, 330102L, 3L, "上城区"));
        when(baseMapper.selectById(2L)).thenReturn(self);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区")
        ));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateSysDicArea(bo(2L, 330100L, 330102L, 4L)));

        assertTrue(ex.getMessage().contains("不能是自己的下级"), "异常信息应指明父级是自身下级：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(SysDicArea.class));
    }

    /**
     * 合法改挂：330102 改挂到 330100（2 层）之下、自身 3 层，且 330100 不在自己子树内 → 更新一次。
     * <p>夹具只含 {@code 330000 -> 330100}，330102 无下级，
     * 故 {@code collectSubtreeIds(330102, ...)} 只得到 {@code {330102}}，不含 330100。</p>
     */
    @Test
    void updateAllowsValidReparent() {
        SysDicArea self = area(3L, 330102L, 3L, "上城区");
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(2L, 330100L, 2L, "杭州市"));
        when(baseMapper.selectById(3L)).thenReturn(self);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市")
        ));
        when(baseMapper.updateById(any(SysDicArea.class))).thenReturn(1);

        assertEquals(1, service.updateSysDicArea(bo(3L, 330102L, 330100L, 3L)));

        ArgumentCaptor<SysDicArea> captor = ArgumentCaptor.forClass(SysDicArea.class);
        verify(baseMapper).updateById(captor.capture());
        SysDicArea saved = captor.getValue();
        assertEquals(3L, saved.getPkid());
        assertEquals(330102L, saved.getAreaId());
        assertEquals(330100L, saved.getParentAreaId());
        assertEquals(3L, saved.getLayered());
    }

    /**
     * 存量脏数据兼容：省级行 parent_area_id=156（该 area_id 不存在），
     * 未改动上级的编辑必须放行，否则 34 个省级行将完全无法编辑。
     */
    @Test
    void updateAllowsUnchangedDanglingParent() {
        SysDicArea self = area(1L, 330000L, 1L, "浙江省");
        self.setParentAreaId(156L);
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectById(1L)).thenReturn(self);
        when(baseMapper.selectOne(any())).thenReturn(null); // 156 不存在
        when(baseMapper.updateById(any(SysDicArea.class))).thenReturn(1);

        assertEquals(1, service.updateSysDicArea(bo(1L, 330000L, 156L, 1L)));
        verify(baseMapper).updateById(any(SysDicArea.class));
    }

    /** 但把上级改成另一个不存在的代码仍必须拒绝 */
    @Test
    void updateRejectsChangedParentToMissingNode() {
        SysDicArea self = area(1L, 330000L, 1L, "浙江省");
        self.setParentAreaId(156L);
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectById(1L)).thenReturn(self);
        when(baseMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateSysDicArea(bo(1L, 330000L, 999999L, 1L)));
        assertTrue(ex.getMessage().contains("上级行政区划不存在"));
        verify(baseMapper, never()).updateById(any(SysDicArea.class));
    }

    /** layered 为 null 应给业务异常而非 NPE */
    @Test
    void insertRejectsNullLayeredWithBusinessError() {
        SysDicAreaBo b = bo(null, 330100L, 330000L, null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertSysDicArea(b));
        assertTrue(ex.getMessage().contains("层级不能为空"));
        verify(baseMapper, never()).insert(any(SysDicArea.class));
    }

    /** 上级存在但自身层级缺失时，应明确报错而不是静默跳过连续性校验 */
    @Test
    void insertRejectsWhenParentLayeredMissing() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(1L, 330000L, null, "浙江省"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330100L, 330000L, 2L)));
        assertTrue(ex.getMessage().contains("上级行政区划层级缺失"));
    }

    // ------------------------------------------------------------------
    // 导出契约（Task 5）：children 必须排除、业务字段必须有中文表头、
    // createTime 必须存在（前端列表"创建时间"列依赖它）
    // ------------------------------------------------------------------

    /** 导出契约：children 必须被 ExcelIgnore 排除，否则 FastExcel 无法可靠写出 List 字段 */
    @Test
    void areaVoExcludesChildrenFromExcel() {
        try {
            java.lang.reflect.Field children = SysDicAreaVo.class.getDeclaredField("children");
            assertNotNull(children.getAnnotation(cn.idev.excel.annotation.ExcelIgnore.class),
                "children 必须标注 @ExcelIgnore");
        } catch (NoSuchFieldException e) {
            fail("SysDicAreaVo 缺少 children 字段");
        }
    }

    /** 导出契约：业务字段必须有中文表头 */
    @Test
    void areaVoHasChineseExcelHeaders() {
        String[] expected = {"areaId", "parentAreaId", "areaName", "layered", "sort", "address", "lat", "lng", "createTime", "updateTime"};
        for (String name : expected) {
            try {
                java.lang.reflect.Field f = SysDicAreaVo.class.getDeclaredField(name);
                cn.idev.excel.annotation.ExcelProperty p =
                    f.getAnnotation(cn.idev.excel.annotation.ExcelProperty.class);
                assertNotNull(p, name + " 必须标注 @ExcelProperty");
                assertFalse(p.value()[0].isBlank(), name + " 的表头不能为空");
            } catch (NoSuchFieldException e) {
                fail("SysDicAreaVo 缺少字段: " + name);
            }
        }
    }

    /** 前端列表"创建时间"列依赖 createTime 字段存在 */
    @Test
    void areaVoExposesCreateTime() {
        try {
            java.lang.reflect.Field f = SysDicAreaVo.class.getDeclaredField("createTime");
            assertEquals(java.util.Date.class, f.getType());
        } catch (NoSuchFieldException e) {
            fail("SysDicAreaVo 缺少 createTime，前端创建时间列将恒为空");
        }
    }

    // ------------------------------------------------------------------
    // 缓存（Task 6）：全量树单键 TTL 缓存 + 无条件下推 + 写路径失效
    // ------------------------------------------------------------------

    /**
     * 全量树命中缓存后不再查库。
     * <p>{@code assertSame} 把"命中缓存返回的是同一个实例"这一契约固定下来——正因为是同一实例，
     * 接口 javadoc 才要求调用方只读（否则会污染后续请求）。</p>
     */
    @Test
    void fullTreeQueriesDatabaseOnlyOnceWhenCached() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省")
        ));

        List<SysDicAreaVo> first = service.selectFullTree();
        List<SysDicAreaVo> second = service.selectFullTree();

        verify(baseMapper, times(1)).selectVoList(any());
        assertSame(first, second, "第二次必须直接返回缓存实例");
    }

    /**
     * 缓存写入必须带有限 TTL。
     * <p>兄弟项目 nexus-shop 2026-09-21 的事故根因就是 {@code city_list_tree} 写成永久缓存：
     * 包重命名后缓存里的 Jackson 载荷再也反序列化不出来，端点持续 500 直到人工 flush。
     * 本断言锁住"确实带了有限 TTL"，防止回归成永久缓存。</p>
     */
    @Test
    void fullTreeCacheWriteCarriesBoundedTtl() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省")
        ));

        service.selectFullTree();

        redisMock.verify(() -> RedisUtils.setCacheObject(
            eq(CacheNames.SYS_DIC_AREA_TREE),
            any(),
            argThat(ttl -> ttl != null && ttl.compareTo(Duration.ZERO) > 0
                && ttl.compareTo(Duration.ofDays(1)) <= 0)));
    }

    /** 无条件查询走全量树缓存：两次调用只查库一次 */
    @Test
    void treeListWithoutConditionUsesCachedFullTree() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省"),
            vo(2L, 330100L, 330000L, 2L, "杭州市")
        ));

        List<SysDicAreaVo> first = service.selectSysDicAreaTreeList(new SysDicArea());
        List<SysDicAreaVo> second = service.selectSysDicAreaTreeList(new SysDicArea());

        verify(baseMapper, times(1)).selectVoList(any());
        assertSame(first, second, "无条件查询应命中同一份缓存树");
        assertEquals(1, first.size());
        assertEquals(330000L, first.get(0).getAreaId());
    }

    /**
     * 带条件的查询每次都要查库，且<b>不得</b>写进全量树那个 key。
     * <p>把过滤结果也缓存到同一个 key 会让不同条件互相污染：后一次请求会读到前一次的结果。</p>
     */
    @Test
    void treeListWithConditionDoesNotUseFullTreeCache() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(2L, 330100L, 330000L, 2L, "杭州市")
        ));

        SysDicArea query = new SysDicArea();
        query.setAreaName("杭州");
        service.selectSysDicAreaTreeList(query);
        service.selectSysDicAreaTreeList(query);

        verify(baseMapper, times(2)).selectVoList(any());
        redisMock.verify(() -> RedisUtils.setCacheObject(
            eq(CacheNames.SYS_DIC_AREA_TREE), any(), any(Duration.class)), never());
    }

    /**
     * 反向污染防护：缓存已填充时，带条件查询仍必须查库并返回库侧过滤后的结果。
     * <p>夹具让两次查询返回不同结果：无条件查询是"浙江省 → 杭州市"，带条件查询只有"杭州市"。
     * 若实现从全量树缓存里取，根节点会是 330000；若实现把过滤结果写回同一个 key，
     * 断言也会因调用次数不符而失败。</p>
     */
    @Test
    void treeListWithConditionIsNotServedFromFullTreeCache() {
        when(baseMapper.selectVoList(any()))
            .thenReturn(List.of(
                vo(1L, 330000L, 156L, 1L, "浙江省"),
                vo(2L, 330100L, 330000L, 2L, "杭州市")
            ))
            .thenReturn(List.of(
                vo(2L, 330100L, 330000L, 2L, "杭州市")
            ));

        List<SysDicAreaVo> full = service.selectSysDicAreaTreeList(new SysDicArea());

        SysDicArea query = new SysDicArea();
        query.setAreaName("杭州");
        List<SysDicAreaVo> filtered = service.selectSysDicAreaTreeList(query);

        verify(baseMapper, times(2)).selectVoList(any());
        assertEquals(330000L, full.get(0).getAreaId());
        assertEquals(1, filtered.size());
        assertEquals(330100L, filtered.get(0).getAreaId(), "带条件查询必须走库，不能吃全量树缓存");
        assertNotSame(full, filtered);
    }

    /**
     * 新增成功后必须清缓存。
     * <p>计数说明：{@code insert} 的 {@code validate} 不会查 {@code selectVoList}
     * （新增时 {@code pkid} 为空，防环分支不进入），故这里恰好 2 次——缓存填充 1 次 + 失效后重查 1 次。</p>
     */
    @Test
    void insertEvictsFullTreeCache() {
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(1L, 330000L, 1L, "浙江省"));
        when(baseMapper.insert(any(SysDicArea.class))).thenReturn(1);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省")
        ));

        service.selectFullTree(); // 填充缓存
        assertEquals(1, service.insertSysDicArea(bo(null, 330100L, 330000L, 2L)));
        service.selectFullTree(); // 缓存已被清 → 必须重新查库

        verify(baseMapper, times(2)).selectVoList(any());
        redisMock.verify(() -> RedisUtils.deleteObject(CacheNames.SYS_DIC_AREA_TREE));
    }

    /**
     * 更新成功后必须清缓存。
     * <p>计数说明：{@code update} 的 {@code validate} 会为防环检查查一次 {@code selectVoList}
     * （与缓存无关），故总共 3 次——缓存填充 1 次 + 防环 1 次 + 失效后重查 1 次。</p>
     */
    @Test
    void updateEvictsFullTreeCache() {
        SysDicArea self = area(3L, 330102L, 3L, "上城区");
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(0L);
        when(baseMapper.selectOne(any())).thenReturn(area(2L, 330100L, 2L, "杭州市"));
        when(baseMapper.selectById(3L)).thenReturn(self);
        when(baseMapper.updateById(any(SysDicArea.class))).thenReturn(1);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(2L, 330100L, 330000L, 2L, "杭州市"),
            vo(3L, 330102L, 330100L, 3L, "上城区")
        ));

        service.selectFullTree(); // 填充缓存
        assertEquals(1, service.updateSysDicArea(bo(3L, 330102L, 330100L, 3L)));
        service.selectFullTree(); // 缓存已被清 → 必须重新查库

        verify(baseMapper, times(3)).selectVoList(any());
        redisMock.verify(() -> RedisUtils.deleteObject(CacheNames.SYS_DIC_AREA_TREE));
    }

    /** 删除成功后必须清缓存 */
    @Test
    void deleteEvictsFullTreeCache() {
        SysDicArea self = new SysDicArea();
        self.setPkid(3L);
        self.setAreaId(330102L);
        self.setAreaName("上城区");
        when(baseMapper.selectById(3L)).thenReturn(self);
        when(baseMapper.exists(any())).thenReturn(false);
        when(baseMapper.deleteByIds(any())).thenReturn(1);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(3L, 330102L, 330100L, 3L, "上城区")
        ));

        service.selectFullTree(); // 填充缓存
        assertEquals(1, service.deleteSysDicAreaByPkids(new Long[]{3L}));
        service.selectFullTree(); // 缓存已被清 → 必须重新查库

        verify(baseMapper, times(2)).selectVoList(any());
        redisMock.verify(() -> RedisUtils.deleteObject(CacheNames.SYS_DIC_AREA_TREE));
    }

    /**
     * 校验失败（抛 {@code ServiceException}）时缓存必须原样保留。
     * <p>失效动作放在写库调用<b>之后</b>，因此"代码重复"这类校验失败不会把缓存无谓清空；
     * 否则一次失败的编辑就会让后续所有请求都重新查 1661 行。</p>
     */
    @Test
    void failedInsertKeepsFullTreeCache() {
        when(baseMapper.selectVoList(any())).thenReturn(List.of(
            vo(1L, 330000L, 156L, 1L, "浙江省")
        ));
        when(baseMapper.countByAreaIdIncludeDeleted(anyLong(), anyLong())).thenReturn(1L);

        service.selectFullTree(); // 填充缓存

        assertThrows(ServiceException.class,
            () -> service.insertSysDicArea(bo(null, 330100L, 330000L, 2L)));

        service.selectFullTree(); // 仍应命中缓存，不再查库

        verify(baseMapper, times(1)).selectVoList(any());
        redisMock.verify(() -> RedisUtils.deleteObject(CacheNames.SYS_DIC_AREA_TREE), never());
    }
}
