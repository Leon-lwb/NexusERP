package com.nexus.mes.flash.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.mapper.MesResourceItemMapper;
import com.nexus.mes.flash.mapper.MesResourcePoolMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MesResourcePoolServiceImpl 单元测试 — MES-FLASH-06 资源回收/消耗回归
 * <p>覆盖: release CAS(ALLOCATED→AVAILABLE) + 清绑定 + used_count 减 1、幂等(0 行只告警不抛)、
 * applyFlashResult(PASS 置 USED 消耗 / FAIL 回收)。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class MesResourcePoolServiceImplTest {

    private static final Long POOL_ID = 7L;
    private static final Long ITEM_ID = 555L;

    @Mock
    private MesResourcePoolMapper baseMapper;
    @Mock
    private MesResourceItemMapper resourceItemMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private MesResourcePoolServiceImpl service;

    @Captor
    private ArgumentCaptor<Wrapper<MesResourceItem>> itemUpdateCaptor;
    @Captor
    private ArgumentCaptor<Wrapper<MesResourcePool>> poolUpdateCaptor;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MesResourcePool.class);
        TableInfoHelper.initTableInfo(assistant, MesResourceItem.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(MesResourcePool.class);
        TableInfoHelper.remove(MesResourceItem.class);
    }

    private MesResourceItem item(String status) {
        MesResourceItem item = new MesResourceItem();
        item.setItemId(ITEM_ID);
        item.setPoolId(POOL_ID);
        item.setResourceValue("00:1A:2B:3C:4D:5E");
        item.setStatus(status);
        item.setBoundSnId(1L);
        return item;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesResourceItem> captureItemUpdate() {
        verify(resourceItemMapper).update(isNull(), itemUpdateCaptor.capture());
        return (LambdaUpdateWrapper<MesResourceItem>) itemUpdateCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesResourcePool> capturePoolUpdate() {
        verify(baseMapper).update(isNull(), poolUpdateCaptor.capture());
        return (LambdaUpdateWrapper<MesResourcePool>) poolUpdateCaptor.getValue();
    }

    // ==================== release ====================

    @Test
    void release_movesAllocatedToAvailableClearsBindingAndDecrementsUsedCount() {
        when(resourceItemMapper.selectById(ITEM_ID)).thenReturn(item("ALLOCATED"));
        when(resourceItemMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(service.release(ITEM_ID, "刷写FAIL回收"));

        LambdaUpdateWrapper<MesResourceItem> itemUpdate = captureItemUpdate();
        String setSql = itemUpdate.getSqlSet();
        assertTrue(setSql.contains("status"), setSql);
        assertTrue(setSql.contains("bound_sn_id"), setSql);
        assertTrue(setSql.contains("bound_time"), setSql);
        assertTrue(itemUpdate.getParamNameValuePairs().containsValue("AVAILABLE"),
            "状态必须回写 AVAILABLE: " + itemUpdate.getParamNameValuePairs());

        LambdaUpdateWrapper<MesResourcePool> poolUpdate = capturePoolUpdate();
        assertTrue(poolUpdate.getSqlSet().contains("used_count"), poolUpdate.getSqlSet());
        assertTrue(poolUpdate.getSqlSet().contains("- 1"), poolUpdate.getSqlSet());
    }

    @Test
    void release_isIdempotentWhenItemIsNotAllocated() {
        when(resourceItemMapper.selectById(ITEM_ID)).thenReturn(item("USED"));
        when(resourceItemMapper.update(isNull(), any())).thenReturn(0);

        assertFalse(service.release(ITEM_ID, "重复回收"), "非 ALLOCATED 状态必须幂等跳过(只告警不抛异常)");
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    void release_skipsWhenItemMissing() {
        when(resourceItemMapper.selectById(ITEM_ID)).thenReturn(null);

        assertFalse(service.release(ITEM_ID, "资源不存在"));
        verify(resourceItemMapper, never()).update(isNull(), any());
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    void release_skipsWhenItemIdIsNull() {
        assertFalse(service.release(null, "空ID"));
        verify(resourceItemMapper, never()).selectById(any());
    }

    // ==================== applyFlashResult ====================

    @Test
    void applyFlashResult_passMarksResourceUsed() {
        when(baseMapper.selectList(any())).thenReturn(List.of(pool()));
        when(resourceItemMapper.selectList(any())).thenReturn(List.of(item("ALLOCATED")));
        when(resourceItemMapper.update(isNull(), any())).thenReturn(1);

        assertEquals(1, service.applyFlashResult(1L, "MAC", true, "刷写PASS"));

        LambdaUpdateWrapper<MesResourceItem> itemUpdate = captureItemUpdate();
        assertTrue(itemUpdate.getParamNameValuePairs().containsValue("USED"),
            "PASS 后资源项须置 USED(使 USED 成为可达状态): " + itemUpdate.getParamNameValuePairs());
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    void applyFlashResult_failReleasesResourceAndDecrementsUsedCount() {
        when(baseMapper.selectList(any())).thenReturn(List.of(pool()));
        when(resourceItemMapper.selectList(any())).thenReturn(List.of(item("ALLOCATED")));
        when(resourceItemMapper.selectById(ITEM_ID)).thenReturn(item("ALLOCATED"));
        when(resourceItemMapper.update(isNull(), any())).thenReturn(1);

        assertEquals(1, service.applyFlashResult(1L, "MAC", false, "刷写FAIL回收"));

        LambdaUpdateWrapper<MesResourceItem> itemUpdate = captureItemUpdate();
        assertTrue(itemUpdate.getParamNameValuePairs().containsValue("AVAILABLE"),
            "FAIL 必须回收为 AVAILABLE: " + itemUpdate.getParamNameValuePairs());
        assertTrue(capturePoolUpdate().getSqlSet().contains("used_count"));
    }

    @Test
    void applyFlashResult_skipsQuietlyWhenSnHasNoAllocatedResource() {
        when(baseMapper.selectList(any())).thenReturn(List.of(pool()));
        when(resourceItemMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, service.applyFlashResult(1L, "MAC", false, "无绑定资源"));
        verify(resourceItemMapper, never()).update(isNull(), any());
    }

    @Test
    void applyFlashResult_skipsWhenResourceTypeBlank() {
        assertEquals(0, service.applyFlashResult(1L, " ", true, "无类型"));
        verify(baseMapper, never()).selectList(any());
    }

    private MesResourcePool pool() {
        MesResourcePool pool = new MesResourcePool();
        pool.setPoolId(POOL_ID);
        pool.setResourceType("MAC");
        pool.setStatus("ENABLED");
        pool.setUsedCount(1);
        return pool;
    }

    @Test
    void allocate_keepsUsedAndAllocatedIdempotentGuard() {
        // 该 SN 已有 USED 资源(已烧录)时不得重复分配新资源
        MesResourceItem used = item("USED");
        when(resourceItemMapper.selectOne(any())).thenReturn(used);

        assertEquals(used, service.allocate(POOL_ID, 1L, "SN-1"));
        verify(resourceItemMapper, never()).update(isNull(), any());
    }

    @Test
    void release_reasonIsMergedIntoRemarkWithoutLosingOriginal() {
        MesResourceItem origin = item("ALLOCATED");
        origin.setRemark("原备注");
        when(resourceItemMapper.selectById(ITEM_ID)).thenReturn(origin);
        when(resourceItemMapper.update(isNull(), any())).thenReturn(1);

        service.release(ITEM_ID, "刷写FAIL");

        LambdaUpdateWrapper<MesResourceItem> itemUpdate = captureItemUpdate();
        assertTrue(itemUpdate.getParamNameValuePairs().values().stream()
                .anyMatch(v -> v != null && v.toString().contains("原备注")),
            "回收原因应并入备注且不丢原备注: " + itemUpdate.getParamNameValuePairs());
    }
}
