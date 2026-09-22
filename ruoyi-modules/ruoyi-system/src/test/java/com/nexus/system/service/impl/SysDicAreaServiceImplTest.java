package com.nexus.system.service.impl;

import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    private static SysDicAreaVo vo(Long pkid, Long areaId, Long parentAreaId, Long layered, String name) {
        SysDicAreaVo v = new SysDicAreaVo();
        v.setPkid(pkid);
        v.setAreaId(areaId);
        v.setParentAreaId(parentAreaId);
        v.setLayered(layered);
        v.setAreaName(name);
        return v;
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
}
