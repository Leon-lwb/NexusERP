package com.nexus.system.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.bo.SysDicAreaBo;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysDicAreaServiceImpl} 单元测试。
 * <p><b>必须保留 {@code @Tag}：</b>根 pom 的 surefire 配置为 {@code <groups>${profiles.active}</groups>}
 * （默认 local），只有带对应 {@code @Tag} 的测试才会被执行。缺少标签时 surefire 会报
 * "Tests run: 0" 并 BUILD SUCCESS —— 即静默假绿。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SysDicAreaServiceImplTest {

    @Mock
    private SysDicAreaMapper baseMapper;

    @InjectMocks
    private SysDicAreaServiceImpl service;

    /**
     * 预热 {@code MapstructUtils} 的静态初始化。
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
     */
    @BeforeAll
    static void warmUpMapstructUtils() {
        try (MockedStatic<SpringUtil> springMock = mockStatic(SpringUtil.class)) {
            springMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter());
            // 类字面量不触发 <clinit>，必须显式初始化；且必须在静态桩窗口内完成
            Class.forName("com.nexus.common.core.utils.MapstructUtils", true,
                SysDicAreaServiceImplTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MapstructUtils 不在测试类路径上，无法预热静态初始化", e);
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
}
