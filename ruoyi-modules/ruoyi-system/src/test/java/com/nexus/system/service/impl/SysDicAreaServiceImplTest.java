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
}
