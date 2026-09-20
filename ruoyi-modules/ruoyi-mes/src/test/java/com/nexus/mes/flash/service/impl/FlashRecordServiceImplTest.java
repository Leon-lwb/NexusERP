package com.nexus.mes.flash.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlashRecordServiceImpl 单元测试 — 免登录设备上报通道修复回归
 * <p>覆盖: MES-FLASH-09 免登录通道不信任客户端 operatorId/operatorName(防冒名留痕)、
 * MES-FLASH-03 落库 content_ref_id 取 Provider 解析的"工序配置内容源ID"(非资源项ID)。</p>
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

    @Test
    void deviceChannelDoesNotTrustClientOperatorAndKeepsStepContentRefId() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setSnStatus("IN_PROGRESS");
        // 无工单/流转卡上下文 -> 跳过工序绑定校验(聚焦本次断言)
        sn.setMesWoId(null);
        sn.setCardId(null);
        when(proSnMapper.selectOne(any())).thenReturn(sn);

        MesFlashType type = new MesFlashType();
        type.setFlashTypeId(9L);
        type.setTypeCode("mac");
        when(mesFlashTypeMapper.selectOne(any())).thenReturn(type);

        FlashTypeProvider provider = mock(FlashTypeProvider.class);
        when(flashTypeRegistry.getProvider("mac")).thenReturn(provider);
        when(provider.resolveContent(any())).thenReturn(FlashContent.builder()
            .contentRefType("MAC_POOL").contentRefId(456L).contentPayload("{\"mac\":\"00:1A:2B:3C:4D:5E\"}").build());
        when(provider.verify(any(), any(), any())).thenReturn(FlashVerifyResult.builder().pass(true).build());
        when(autoCodeUtil.genSerialCode(eq("MES_FLASH_NO"), any())).thenReturn("FL-1");

        FlashRecordBo bo = new FlashRecordBo();
        bo.setSnCode("SN-1");
        bo.setFlashTypeCode("mac");
        bo.setContentRefId(456L);
        bo.setResult("PASS");
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
}
