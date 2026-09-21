package com.nexus.mes.flash.provider;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.MesResourcePoolMapper;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MacPoolFlashProvider 单元测试 — 池类刷写 P0 修复回归
 * <p>覆盖: MES-FLASH-03 content_ref_id 语义统一为"工序配置的内容源ID"(否则完工门禁永不通过)、
 * MES-FLASH-02 落库设备上报的实刷值并比对池分配值。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class MacPoolFlashProviderTest {

    private static final Long POOL_ID = 77L;
    private static final Long ITEM_ID = 555L;
    private static final Long STEP_CONTENT_REF_ID = 456L;
    private static final String ALLOCATED_MAC = "00:1A:2B:3C:4D:5E";

    @Mock
    private IMesResourcePoolService resourcePoolService;
    @Mock
    private MesResourcePoolMapper resourcePoolMapper;

    @InjectMocks
    private MacPoolFlashProvider provider;

    @Captor
    private ArgumentCaptor<Wrapper<MesResourcePool>> poolQueryCaptor;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesResourcePool.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(MesResourcePool.class);
    }

    @BeforeEach
    void setUp() {
        MesResourcePool pool = new MesResourcePool();
        pool.setPoolId(POOL_ID);
        pool.setResourceType("MAC");
        pool.setStatus("ENABLED");
        lenient().when(resourcePoolMapper.selectOne(any())).thenReturn(pool);

        MesResourceItem item = new MesResourceItem();
        item.setItemId(ITEM_ID);
        item.setPoolId(POOL_ID);
        item.setResourceValue(ALLOCATED_MAC);
        item.setStatus("ALLOCATED");
        lenient().when(resourcePoolService.allocate(POOL_ID, 1L, "SN-1")).thenReturn(item);
    }

    private FlashContext ctx() {
        return FlashContext.builder().snId(1L).snCode("SN-1").flashTypeCode("mac").build();
    }

    private FlashContent expected() {
        return FlashContent.builder()
            .contentRefType("MAC_POOL")
            .contentRefId(STEP_CONTENT_REF_ID)
            .contentPayload("{\"mac\":\"" + ALLOCATED_MAC + "\"}")
            .build();
    }

    // ==================== MES-FLASH-03 content_ref_id 语义 ====================

    @Test
    void resolveContent_storesStepConfiguredContentRefIdNotItemId() {
        FlashContext ctx = FlashContext.builder().snId(1L).snCode("SN-1")
            .flashTypeCode("mac").contentRefId(STEP_CONTENT_REF_ID).build();
        FlashContent content = provider.resolveContent(ctx);

        assertEquals(STEP_CONTENT_REF_ID, content.getContentRefId(),
            "content_ref_id 必须是工序配置的内容源ID(门禁按该值反查), 落资源项ID会导致门禁永不通过");
        assertEquals("MAC_POOL", content.getContentRefType());
        assertEquals("{\"mac\":\"" + ALLOCATED_MAC + "\"}", content.getContentPayload());
    }

    @Test
    void resolveContent_fallsBackToItemIdWhenStepHasNoContentRef() {
        assertEquals(ITEM_ID, provider.resolveContent(ctx()).getContentRefId(),
            "工序未配置 contentRefId 时退回资源项ID仅作留痕");
    }

    // ==================== MES-FLASH-02 实刷值落库 ====================

    @Test
    void verify_recordsActualBurnedValue() {
        FlashRecordBo actual = new FlashRecordBo();
        actual.setContentPayload("{\"mac\":\"" + ALLOCATED_MAC + "\"}");

        FlashVerifyResult result = provider.verify(ctx(), expected(), actual);

        assertTrue(result.isPass());
        assertEquals("{\"mac\":\"" + ALLOCATED_MAC + "\"}", result.getContentPayload(),
            "落库内容必须是设备上报的实刷值, 而非池分配值");
    }

    @Test
    void verify_failsWhenActualDiffersFromAllocatedValue() {
        FlashRecordBo actual = new FlashRecordBo();
        actual.setContentPayload("{\"mac\":\"11:22:33:44:55:66\"}");

        FlashVerifyResult result = provider.verify(ctx(), expected(), actual);

        assertFalse(result.isPass(), "实刷值与池分配值不一致必须判不通过(修复前无条件 pass)");
        assertTrue(result.getFailReason().contains("不一致"), result.getFailReason());
        assertEquals("{\"mac\":\"11:22:33:44:55:66\"}", result.getContentPayload(), "实刷值仍须落库供追溯");
    }

    @Test
    void verify_failsClosedWhenActualPayloadLacksMacField() {
        FlashRecordBo actual = new FlashRecordBo();
        actual.setContentPayload("{\"value\":\"whatever\"}");

        FlashVerifyResult result = provider.verify(ctx(), expected(), actual);

        assertFalse(result.isPass(), "上报内容缺 mac 字段无法比对, 必须 fail-closed");
        assertTrue(result.getFailReason().contains("缺少 mac"), result.getFailReason());
    }

    @Test
    void verify_fallsBackToAllocatedValueWhenDeviceReportsNothing() {
        FlashVerifyResult result = provider.verify(ctx(), expected(), new FlashRecordBo());

        assertTrue(result.isPass());
        assertEquals("{\"mac\":\"" + ALLOCATED_MAC + "\"}", result.getContentPayload(),
            "旧客户端未上报实刷值时退回池分配值");
    }

    // ==================== MES-FLASH-06 池选择 ====================

    @Test
    @SuppressWarnings("unchecked")
    void resolveContent_fallbackPoolSelectionIsOrdered() {
        provider.resolveContent(ctx());

        verify(resourcePoolMapper).selectOne(poolQueryCaptor.capture());
        String sqlSegment = poolQueryCaptor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sqlSegment.contains("ORDER BY"), "池选择必须带排序(修复前是无排序 LIMIT 1): " + sqlSegment);
        assertTrue(sqlSegment.contains("CREATE_TIME"), "应按 create_time ASC 稳定取首个启用池: " + sqlSegment);
    }

    @Test
    void resolveContent_prefersContentRefIdAsPoolWhenItPointsToEnabledPool() {
        MesResourcePool preferred = new MesResourcePool();
        preferred.setPoolId(STEP_CONTENT_REF_ID);
        preferred.setResourceType("MAC");
        preferred.setStatus("ENABLED");
        when(resourcePoolMapper.selectById(STEP_CONTENT_REF_ID)).thenReturn(preferred);
        MesResourceItem item = new MesResourceItem();
        item.setItemId(ITEM_ID);
        item.setResourceValue(ALLOCATED_MAC);
        item.setStatus("ALLOCATED");
        lenient().when(resourcePoolService.allocate(STEP_CONTENT_REF_ID, 1L, "SN-1")).thenReturn(item);

        FlashContent content = provider.resolveContent(FlashContext.builder()
            .snId(1L).snCode("SN-1").flashTypeCode("mac").contentRefId(STEP_CONTENT_REF_ID).build());

        verify(resourcePoolService).allocate(STEP_CONTENT_REF_ID, 1L, "SN-1");
        verify(resourcePoolMapper, never()).selectOne(any());
        assertEquals(STEP_CONTENT_REF_ID, content.getContentRefId());
    }

    @Test
    void resolveContent_ignoresContentRefIdThatIsNotAnEnabledPoolOfSameType() {
        // contentRefId 指向固件(非资源池)时必须退化到首个启用池, 不得拿固件ID当池ID
        when(resourcePoolMapper.selectById(STEP_CONTENT_REF_ID)).thenReturn(null);

        provider.resolveContent(FlashContext.builder()
            .snId(1L).snCode("SN-1").flashTypeCode("mac").contentRefId(STEP_CONTENT_REF_ID).build());

        verify(resourcePoolService).allocate(POOL_ID, 1L, "SN-1");
    }

    @Test
    void getPoolResourceType_isMac() {
        assertEquals("MAC", provider.getPoolResourceType());
    }
}
