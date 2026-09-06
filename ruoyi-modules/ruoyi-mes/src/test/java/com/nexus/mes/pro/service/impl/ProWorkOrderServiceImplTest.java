package com.nexus.mes.pro.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.flash.config.FwProperties;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.mapper.NcrReworkOrderMapper;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IMesMbomService;
import com.nexus.mes.pro.domain.bo.UpdateStepFirmwareBo;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.domain.vo.ReleasePkgBomItemVo;
import com.nexus.mes.release.domain.vo.ReleasePkgFirmwareVo;
import com.nexus.mes.release.service.IReleasePackageService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProWorkOrderServiceImpl 发布包集成单元测试:
 * 固件覆写优先级 / fail-loudly / BOM选配 / FLASH下达门禁 / 工序固件更改
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class ProWorkOrderServiceImplTest {

    @BeforeAll
    static void initStatics() {
        // JsonUtils 静态初始化依赖 SpringUtils, 先激活静态桩(rewriteFlashConfigFirmware 真实走 Jackson)
        SpringStaticBootstrap.ensureActive();
        // lambdaUpdate.set(...) 立即解析实体列缓存, 单测环境须手动初始化表元数据
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, ProWorkOrderStep.class);
    }

    private MockedStatic<SpringUtils> aopProxyMock;

    @BeforeEach
    void stubAopProxy() {
        // M2: createFromErpMessage 经 SpringUtils.getAopProxy 调用事务方法, 纯单测无容器——
        // lenient 桩为返回当前普通实例(仅触及代理调用的测试用例生效, 其余不受严格桩约束)
        aopProxyMock = mockStatic(SpringUtils.class, withSettings().lenient());
        aopProxyMock.when(() -> SpringUtils.getAopProxy(any())).thenReturn(service);
    }

    @AfterEach
    void closeAopProxyMock() {
        if (aopProxyMock != null) {
            aopProxyMock.close();
        }
    }

    @Mock
    private ProWorkOrderMapper baseMapper;
    @Mock
    private ProWorkOrderStepMapper stepMapper;
    @Mock
    private ProWorkOrderBomLineMapper woBomLineMapper;
    @Mock
    private IMesErpBridgeService mesErpBridgeService;
    @Mock
    private IMesMbomService mesMbomService;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private QcIpqcMapper qcIpqcMapper;
    @Mock
    private NcrReworkOrderMapper ncrReworkOrderMapper;
    @Mock
    private NcrOrderMapper ncrOrderMapper;
    @Spy
    private FwProperties fwProperties = new FwProperties();
    @Mock
    private FirmwareMapper firmwareMapper;
    @Mock
    private IReleasePackageService releasePackageService;
    @InjectMocks
    private ProWorkOrderServiceImpl service;

    private WorkOrderReleaseMessage buildMessage() {
        WorkOrderReleaseMessage msg = new WorkOrderReleaseMessage();
        msg.setWoId(1L);
        msg.setWoNo("WO-001");
        msg.setMaterialId(100L);
        msg.setMaterialCode("M-100");
        msg.setMaterialName("智能网关");
        msg.setPlannedQty(BigDecimal.TEN);
        return msg;
    }

    private Map<String, Object> routingStep(long stepId, int seq, long processId, String processCode, String processName, Long firmwareId) {
        Map<String, Object> step = new HashMap<>();
        step.put("step_id", stepId);
        step.put("sequence_num", seq);
        step.put("process_id", processId);
        step.put("process_code", processCode);
        step.put("process_name", processName);
        step.put("is_key_process", 0);
        if (firmwareId != null) {
            step.put("firmware_id", firmwareId);
        }
        return step;
    }

    private Firmware releasedFirmware(long firmwareId, long materialId) {
        Firmware fw = new Firmware();
        fw.setFirmwareId(firmwareId);
        fw.setFirmwareCode("FW-" + firmwareId);
        fw.setMaterialId(materialId);
        fw.setStatus("RELEASED");
        return fw;
    }

    /** 桩: 工单不存在(幂等通过) + 物料/单位/默认路线/路由步骤 */
    private void stubBridgeLookups(List<Map<String, Object>> routingSteps) {
        when(baseMapper.selectVoOne(any(), anyBoolean())).thenReturn(null);
        Map<String, Object> material = new HashMap<>();
        material.put("base_uom_id", 26L);
        when(mesErpBridgeService.getMaterial(100L)).thenReturn(material);
        when(mesErpBridgeService.getUnitName(26L)).thenReturn("件");
        when(mesErpBridgeService.getDefaultRoutingId(100L)).thenReturn(9L);
        when(mesErpBridgeService.getRoutingSteps(9L)).thenReturn(routingSteps);
    }

    // ==================== 发布包固件覆写 ====================

    @Test
    void createFromErpMessage_packageMappingOverridesRoutingFirmware() {
        WorkOrderReleaseMessage msg = buildMessage();
        msg.setClientId(5L);
        stubBridgeLookups(List.of(
            routingStep(11L, 10, 3L, "OP10_BIOS_FLASH", "BIOS刷写", 999L),
            routingStep(12L, 20, 4L, "OP20_ASM", "装配", null)));

        ReleasePackageVo pkg = new ReleasePackageVo();
        pkg.setPackageId(500L);
        pkg.setPackageCode("RP-001");
        pkg.setVersion("2.1");
        ReleasePkgFirmwareVo mapping = new ReleasePkgFirmwareVo();
        mapping.setRoutingStepId(11L);
        mapping.setProcessId(3L);
        mapping.setFirmwareId(777L);
        pkg.setFirmwareMappings(List.of(mapping));
        when(releasePackageService.matchPackage(5L, 100L, null)).thenReturn(pkg);
        when(firmwareMapper.selectById(777L)).thenReturn(releasedFirmware(777L, 100L));

        service.createFromErpMessage(msg);

        // 工单落发布包快照字段
        ArgumentCaptor<ProWorkOrder> woCaptor = ArgumentCaptor.forClass(ProWorkOrder.class);
        verify(baseMapper).insert(woCaptor.capture());
        assertEquals(500L, woCaptor.getValue().getReleasePackageId());
        assertEquals("RP-001", woCaptor.getValue().getReleasePackageCode());
        assertEquals("2.1", woCaptor.getValue().getReleasePackageVer());

        // FLASH 工序固件被发布包覆写: 777(包映射) 而非 999(工艺路线); 来源标记 PACKAGE
        ArgumentCaptor<ProWorkOrderStep> stepCaptor = ArgumentCaptor.forClass(ProWorkOrderStep.class);
        verify(stepMapper, times(2)).insert(stepCaptor.capture());
        List<ProWorkOrderStep> steps = stepCaptor.getAllValues();
        assertEquals("FLASH", steps.get(0).getStepType());
        assertEquals(777L, steps.get(0).getContentRefId());
        assertEquals("FIRMWARE", steps.get(0).getContentRefType());
        assertEquals("PACKAGE", steps.get(0).getFirmwareSource());
        // 非FLASH工序不受影响
        assertNull(steps.get(1).getContentRefId());
    }

    @Test
    void createFromErpMessage_processFallbackMatchesWhenRoutingStepIdDiffers() {
        WorkOrderReleaseMessage msg = buildMessage();
        msg.setClientId(5L);
        // 路线升版后 step_id 变为 900, 但工序ID仍为 3
        stubBridgeLookups(List.of(routingStep(900L, 10, 3L, "OP10_BIOS_FLASH", "BIOS刷写", null)));

        ReleasePackageVo pkg = new ReleasePackageVo();
        pkg.setPackageId(500L);
        pkg.setPackageCode("RP-001");
        pkg.setVersion("2.1");
        ReleasePkgFirmwareVo mapping = new ReleasePkgFirmwareVo();
        mapping.setRoutingStepId(11L);
        mapping.setProcessId(3L);
        mapping.setFirmwareId(777L);
        pkg.setFirmwareMappings(List.of(mapping));
        when(releasePackageService.matchPackage(5L, 100L, null)).thenReturn(pkg);
        when(firmwareMapper.selectById(777L)).thenReturn(releasedFirmware(777L, 100L));

        service.createFromErpMessage(msg);

        ArgumentCaptor<ProWorkOrderStep> stepCaptor = ArgumentCaptor.forClass(ProWorkOrderStep.class);
        verify(stepMapper).insert(stepCaptor.capture());
        assertEquals(777L, stepCaptor.getValue().getContentRefId());
        assertEquals("PACKAGE", stepCaptor.getValue().getFirmwareSource());
    }

    @Test
    void createFromErpMessage_archivedPackageFirmwareFailsLoudly() {
        WorkOrderReleaseMessage msg = buildMessage();
        msg.setClientId(5L);
        stubBridgeLookups(List.of(routingStep(11L, 10, 3L, "OP10_BIOS_FLASH", "BIOS刷写", null)));

        ReleasePackageVo pkg = new ReleasePackageVo();
        pkg.setPackageId(500L);
        pkg.setPackageCode("RP-001");
        pkg.setVersion("2.1");
        ReleasePkgFirmwareVo mapping = new ReleasePkgFirmwareVo();
        mapping.setRoutingStepId(11L);
        mapping.setProcessId(3L);
        mapping.setFirmwareId(777L);
        pkg.setFirmwareMappings(List.of(mapping));
        when(releasePackageService.matchPackage(5L, 100L, null)).thenReturn(pkg);

        Firmware archived = releasedFirmware(777L, 100L);
        archived.setStatus("ARCHIVED");
        when(firmwareMapper.selectById(777L)).thenReturn(archived);

        // fail-loudly: 定制固件失效时拒绝静默回退到默认固件
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createFromErpMessage(msg));
        assertTrue(ex.getMessage().contains("非已发布"));
        verify(stepMapper, never()).insert(any(ProWorkOrderStep.class));
    }

    // ==================== BOM选配 ====================

    @Test
    void createFromErpMessage_appliesBomItemsToSnapshot() {
        WorkOrderReleaseMessage msg = buildMessage();
        msg.setClientId(5L);
        msg.setBomId(20L);
        stubBridgeLookups(List.of(routingStep(12L, 20, 4L, "OP20_ASM", "装配", null)));

        MesMbomVo mbom = new MesMbomVo();
        mbom.setBomId(20L);
        MesMbomLineVo ssd256 = new MesMbomLineVo();
        ssd256.setMbomLineId(1L);
        ssd256.setBomDetailId(2L);
        ssd256.setChildMaterialId(201L);
        ssd256.setChildMaterialCode("SSD-256");
        ssd256.setChildMaterialName("SSD 256G");
        ssd256.setQuantityPerParent(BigDecimal.ONE);
        ssd256.setSortOrder(1);
        MesMbomLineVo screw = new MesMbomLineVo();
        screw.setMbomLineId(2L);
        screw.setChildMaterialId(202L);
        screw.setChildMaterialCode("SCREW-M3");
        screw.setChildMaterialName("螺丝");
        screw.setQuantityPerParent(BigDecimal.valueOf(4));
        screw.setSortOrder(2);
        mbom.setLines(new java.util.ArrayList<>(List.of(ssd256, screw)));
        when(mesMbomService.selectByBomId(20L)).thenReturn(mbom);

        ReleasePackageVo pkg = new ReleasePackageVo();
        pkg.setPackageId(500L);
        pkg.setPackageCode("RP-001");
        pkg.setVersion("2.1");
        // REPLACE: SSD-256 -> SSD-512; ADD: 散热片; REMOVE: 螺丝
        ReleasePkgBomItemVo replace = new ReleasePkgBomItemVo();
        replace.setActionType("REPLACE");
        replace.setChildMaterialId(201L);
        replace.setChildMaterialCode("SSD-256");
        replace.setTargetMaterialId(301L);
        replace.setTargetMaterialCode("SSD-512");
        replace.setTargetMaterialName("SSD 512G");
        ReleasePkgBomItemVo add = new ReleasePkgBomItemVo();
        add.setActionType("ADD");
        add.setTargetMaterialId(401L);
        add.setTargetMaterialCode("HEAT-SINK");
        add.setTargetMaterialName("散热片");
        add.setQuantityPerParent(BigDecimal.ONE);
        ReleasePkgBomItemVo remove = new ReleasePkgBomItemVo();
        remove.setActionType("REMOVE");
        remove.setChildMaterialId(202L);
        remove.setChildMaterialCode("SCREW-M3");
        pkg.setBomItems(List.of(replace, add, remove));
        when(releasePackageService.matchPackage(5L, 100L, null)).thenReturn(pkg);

        service.createFromErpMessage(msg);

        // 快照: SSD-512(替换) + 散热片(新增) = 2行; 螺丝被移除; MBOM主数据不受影响
        ArgumentCaptor<com.nexus.mes.pro.domain.ProWorkOrderBomLine> bomCaptor =
            ArgumentCaptor.forClass(com.nexus.mes.pro.domain.ProWorkOrderBomLine.class);
        verify(woBomLineMapper, times(2)).insert(bomCaptor.capture());
        List<com.nexus.mes.pro.domain.ProWorkOrderBomLine> snapshots = bomCaptor.getAllValues();
        assertEquals(301L, snapshots.get(0).getChildMaterialId());
        assertEquals("SSD-512", snapshots.get(0).getChildMaterialCode());
        assertNotNull(snapshots.get(0).getRemark());
        assertTrue(snapshots.get(0).getRemark().contains("发布包选配"));
        assertEquals(401L, snapshots.get(1).getChildMaterialId());
        // MBOM 原行未被修改(在副本上覆写)
        assertEquals(201L, mbom.getLines().get(0).getChildMaterialId());
        assertEquals(2, mbom.getLines().size());
    }

    // ==================== FLASH 下达门禁 ====================

    @Test
    void release_blockedWhenFlashStepUnbound() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("CREATED");
        when(baseMapper.selectById(1L)).thenReturn(wo);
        when(stepMapper.selectCount(any())).thenReturn(1L);
        ProWorkOrderStep flashStep = new ProWorkOrderStep();
        flashStep.setStepType("FLASH");
        flashStep.setSequenceNum(10);
        flashStep.setProcessName("BIOS刷写");
        when(stepMapper.selectList(any())).thenReturn(List.of(flashStep));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.release(1L));
        assertTrue(ex.getMessage().contains("未绑定刷写内容"));
        // 门禁先于 CAS: 状态冻结不发生
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void release_passesWhenFlashStepBound() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("CREATED");
        when(baseMapper.selectById(1L)).thenReturn(wo);
        when(stepMapper.selectCount(any())).thenReturn(1L);
        ProWorkOrderStep flashStep = new ProWorkOrderStep();
        flashStep.setStepType("FLASH");
        flashStep.setProcessName("BIOS刷写");
        flashStep.setContentRefId(777L);
        flashStep.setContentRefType("FIRMWARE");
        when(stepMapper.selectList(any())).thenReturn(List.of(flashStep));
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.release(1L));
        verify(baseMapper).update(any(), any());
    }

    // ==================== 工序固件更改 ====================

    @Test
    void updateStepFirmware_rejectsNonCreatedWorkOrder() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("RELEASED");
        when(baseMapper.selectById(1L)).thenReturn(wo);

        UpdateStepFirmwareBo bo = new UpdateStepFirmwareBo();
        bo.setStepId(10L);
        bo.setFirmwareId(777L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateStepFirmware(1L, bo));
        assertTrue(ex.getMessage().contains("仅新建状态"));
    }

    @Test
    void updateStepFirmware_rejectsNonReleasedFirmware() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("CREATED");
        wo.setMaterialId(100L);
        when(baseMapper.selectById(1L)).thenReturn(wo);
        ProWorkOrderStep step = new ProWorkOrderStep();
        step.setStepId(10L);
        step.setMesWoId(1L);
        step.setStepType("FLASH");
        when(stepMapper.selectById(10L)).thenReturn(step);
        Firmware draft = releasedFirmware(777L, 100L);
        draft.setStatus("DRAFT");
        when(firmwareMapper.selectById(777L)).thenReturn(draft);

        UpdateStepFirmwareBo bo = new UpdateStepFirmwareBo();
        bo.setStepId(10L);
        bo.setFirmwareId(777L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateStepFirmware(1L, bo));
        assertTrue(ex.getMessage().contains("仅已发布"));
    }

    @Test
    void updateStepFirmware_rewritesMultiTypeFlashConfig() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("CREATED");
        wo.setMaterialId(100L);
        when(baseMapper.selectById(1L)).thenReturn(wo);
        ProWorkOrderStep step = new ProWorkOrderStep();
        step.setStepId(10L);
        step.setMesWoId(1L);
        step.setStepType("FLASH");
        // 已有多类型绑定: 固件999 + mac池456
        step.setStepFlashConfig("[{\"flashTypeCode\":\"firmware_file\",\"contentRefId\":999},{\"flashTypeCode\":\"mac\",\"contentRefId\":456}]");
        when(stepMapper.selectById(10L)).thenReturn(step);
        when(firmwareMapper.selectById(777L)).thenReturn(releasedFirmware(777L, 100L));
        when(stepMapper.update(any(), any())).thenReturn(1);

        UpdateStepFirmwareBo bo = new UpdateStepFirmwareBo();
        bo.setStepId(10L);
        bo.setFirmwareId(777L);
        service.updateStepFirmware(1L, bo);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ProWorkOrderStep>> wrapperCaptor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(stepMapper).update(isNull(), wrapperCaptor.capture());
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProWorkOrderStep> lw =
            (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProWorkOrderStep>) wrapperCaptor.getValue();
        java.util.Collection<Object> params = lw.getParamNameValuePairs().values();
        // 单类型绑定改为 777 + 来源 MANUAL
        assertTrue(params.contains(777L));
        assertTrue(params.contains("MANUAL"));
        assertTrue(params.contains("FIRMWARE"));
        // 多类型JSON固件项同步改写(777)且保留 mac 项(456)
        assertTrue(params.stream().anyMatch(v -> v instanceof String s && s.contains("mac") && s.contains("777")));
        assertTrue(params.stream().anyMatch(v -> v instanceof String s && s.contains("mac") && s.contains("456")));
    }
}
