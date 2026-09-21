package com.nexus.mes.flash.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.domain.MesFlashType;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.mapper.MesContentFileMapper;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import com.nexus.mes.flash.provider.FlashContent;
import com.nexus.mes.flash.provider.FlashTypeProvider;
import com.nexus.mes.flash.provider.FlashTypeRegistry;
import com.nexus.mes.flash.provider.FlashVerifyResult;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlashRecordServiceImpl 单元测试 — 刷写域修复回归
 * <p>覆盖: MES-FLASH-09 免登录通道不信任客户端 operatorId/operatorName(防冒名留痕)、
 * MES-FLASH-03 落库 content_ref_id 取 Provider 解析的"工序配置内容源ID"(非资源项ID)、
 * MES-FLASH-04 retry_count 服务端计算 + 超限拒绝 + PASS 后再 FAIL 须给原因、
 * MES-FLASH-05 DISABLED 类型被拒、MES-FLASH-06 池类结果回写资源项(PASS消耗/FAIL回收)。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class FlashRecordServiceImplTest {

    @Mock
    private FlashRecordMapper baseMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private FlashTypeRegistry flashTypeRegistry;
    @Mock
    private MesFlashTypeMapper mesFlashTypeMapper;
    @Mock
    private FirmwareMapper firmwareMapper;
    @Mock
    private MesContentFileMapper contentFileMapper;
    @Mock
    private ProWorkOrderStepMapper workOrderStepMapper;
    @Mock
    private ProFlowCardStepMapper flowCardStepMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private IMesResourcePoolService resourcePoolService;

    @InjectMocks
    private FlashRecordServiceImpl service;

    @Captor
    private ArgumentCaptor<FlashRecord> recordCaptor;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlashRecord.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
        TableInfoHelper.initTableInfo(assistant, MesFlashType.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(FlashRecord.class);
        TableInfoHelper.remove(ProSn.class);
        TableInfoHelper.remove(MesFlashType.class);
    }

    // ==================== 辅助 ====================

    private ProSn sn() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setSnStatus("IN_PROGRESS");
        // 无工单/流转卡上下文 -> 跳过工序绑定校验(聚焦本类断言)
        sn.setMesWoId(null);
        sn.setCardId(null);
        return sn;
    }

    private FlashRecordBo bo(String result) {
        FlashRecordBo bo = new FlashRecordBo();
        bo.setSnCode("SN-1");
        bo.setFlashTypeCode("mac");
        bo.setContentRefId(456L);
        bo.setResult(result);
        return bo;
    }

    /** 打桩确认/上报的公共路径(全部 lenient: 部分用例会在更早的分支抛错) */
    private FlashTypeProvider stubHappyPath(String typeCode, String poolResourceType) {
        lenient().when(proSnMapper.selectOne(any())).thenReturn(sn());
        MesFlashType type = new MesFlashType();
        type.setFlashTypeId(9L);
        type.setTypeCode(typeCode);
        type.setStatus("ENABLED");
        lenient().when(mesFlashTypeMapper.selectOne(any())).thenReturn(type);
        FlashTypeProvider provider = mock(FlashTypeProvider.class);
        lenient().when(flashTypeRegistry.getProvider(typeCode)).thenReturn(provider);
        lenient().when(provider.resolveContent(any())).thenReturn(FlashContent.builder()
            .contentRefType("MAC_POOL").contentRefId(456L).contentPayload("{\"mac\":\"00:1A:2B:3C:4D:5E\"}").build());
        lenient().when(provider.verify(any(), any(), any())).thenReturn(FlashVerifyResult.builder().pass(true).build());
        lenient().when(provider.getPoolResourceType()).thenReturn(poolResourceType);
        lenient().when(autoCodeUtil.genSerialCode(eq("MES_FLASH_NO"), any())).thenReturn("FL-1");
        return provider;
    }

    // ==================== MES-FLASH-09 设备通道身份 ====================

    @Test
    void deviceChannelDoesNotTrustClientOperatorAndKeepsStepContentRefId() {
        stubHappyPath("mac", null);

        FlashRecordBo bo = bo("PASS");
        bo.setOperatorId(999L);
        bo.setOperatorName("张三");
        bo.setOperatorCode("E1001");

        try (MockedStatic<LoginHelper> loginMock = mockStatic(LoginHelper.class)) {
            loginMock.when(LoginHelper::isLogin).thenReturn(false);
            Map<String, Object> result = service.reportFlashBatch(List.of(bo));
            assertEquals(1, result.get("success"), result.toString());
        }

        verify(baseMapper).insert(recordCaptor.capture());
        FlashRecord inserted = recordCaptor.getValue();
        assertNull(inserted.getOperatorId(), "免登录设备通道不得落客户端传入的 operatorId(可冒名)");
        assertEquals("设备上报:E1001(未验证)", inserted.getOperatorName(), "客户端姓名不得直接作为可信操作人落库");
        assertEquals(456L, inserted.getContentRefId(), "content_ref_id 必须落 Provider 解析的工序配置内容源ID");
        assertTrue(inserted.getFlashMode().equals("AUTO"));
    }

    // ==================== MES-FLASH-04 retry_count 服务端计算 ====================

    @Test
    void retryCountIsComputedByServerAndIgnoresClientValue() {
        stubHappyPath("mac", null);
        // 历史已有 2 条 -> 服务端 retry_count = 2, 客户端自报 99 必须被忽略
        when(baseMapper.selectCount(any())).thenReturn(2L);
        FlashRecordBo bo = bo("PASS");
        bo.setRetryCount(99);

        service.confirmFlash(bo);

        verify(baseMapper).insert(recordCaptor.capture());
        assertEquals(2, recordCaptor.getValue().getRetryCount(),
            "retry_count 必须由服务端按历史条数计算, 不得采信客户端自报");
    }

    @Test
    void rejectsFlashWhenRetryCountReachedLimit() {
        stubHappyPath("mac", null);
        // 默认阈值 3: 历史 3 条即拒绝(须走 NCR/返工)
        when(baseMapper.selectCount(any())).thenReturn(3L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirmFlash(bo("PASS")));
        assertTrue(ex.getMessage().contains("上限"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NCR"), ex.getMessage());
        verify(baseMapper, never()).insert(any(FlashRecord.class));
    }

    @Test
    void failAfterPassWithoutReasonIsRejected() {
        stubHappyPath("mac", null);
        when(baseMapper.selectCount(any())).thenReturn(1L);
        FlashRecord lastPass = new FlashRecord();
        lastPass.setFlashNo("FL-PASS-1");
        lastPass.setResult("PASS");
        when(baseMapper.selectOne(any())).thenReturn(lastPass);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirmFlash(bo("FAIL")));
        assertTrue(ex.getMessage().contains("必须填写失败原因"), ex.getMessage());
        verify(baseMapper, never()).insert(any(FlashRecord.class));
    }

    @Test
    void failAfterPassWithReasonIsAcceptedAndKeepsLatestSemantics() {
        stubHappyPath("mac", null);
        when(baseMapper.selectCount(any())).thenReturn(1L);
        FlashRecord lastPass = new FlashRecord();
        lastPass.setFlashNo("FL-PASS-1");
        lastPass.setResult("PASS");
        when(baseMapper.selectOne(any())).thenReturn(lastPass);

        FlashRecordBo bo = bo("FAIL");
        bo.setFailReason("设备校验失败, 待返工");
        service.confirmFlash(bo);

        verify(baseMapper).insert(recordCaptor.capture());
        FlashRecord inserted = recordCaptor.getValue();
        assertEquals("FAIL", inserted.getResult());
        assertEquals("设备校验失败, 待返工", inserted.getFailReason());
        assertEquals(1, inserted.getRetryCount(), "retry_count 仍按历史条数累加");
    }

    // ==================== MES-FLASH-05 停用类型不生效 ====================

    @Test
    void disabledOrMissingFlashTypeIsRejected() {
        stubHappyPath("mac", null);
        // 类型查询带 status=ENABLED 条件, DISABLED 类型查不到 -> 视为已停用
        when(mesFlashTypeMapper.selectOne(any())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirmFlash(bo("PASS")));
        assertTrue(ex.getMessage().contains("已停用或不存在"), ex.getMessage());
        verify(baseMapper, never()).insert(any(FlashRecord.class));
    }

    // ==================== MES-FLASH-06 池类结果回写资源项 ====================

    @Test
    void poolFlashFailReleasesAllocatedResource() {
        stubHappyPath("mac", "MAC");
        when(baseMapper.selectCount(any())).thenReturn(1L);

        FlashRecordBo bo = bo("FAIL");
        bo.setFailReason("MAC 校验不一致");
        service.confirmFlash(bo);

        verify(resourcePoolService).applyFlashResult(eq(1L), eq("MAC"), eq(false), anyString());
    }

    @Test
    void poolFlashPassMarksResourceUsed() {
        stubHappyPath("mac", "MAC");
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.confirmFlash(bo("PASS"));

        verify(resourcePoolService).applyFlashResult(eq(1L), eq("MAC"), eq(true), anyString());
    }

    @Test
    void nonPoolFlashTypeDoesNotTouchResourcePool() {
        stubHappyPath("firmware_file", null);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        FlashRecordBo bo = bo("PASS");
        bo.setFlashTypeCode("firmware_file");

        service.confirmFlash(bo);

        verify(resourcePoolService, never()).applyFlashResult(any(), anyString(), anyBoolean(), anyString());
    }
}
