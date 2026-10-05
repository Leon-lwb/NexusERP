package com.nexus.mes.pro.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
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
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IMesMbomService;
import com.nexus.mes.pro.domain.bo.UpdateStepFirmwareBo;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.service.IQcIpqcService;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.domain.vo.ReleasePkgBomItemVo;
import com.nexus.mes.release.domain.vo.ReleasePkgFirmwareVo;
import com.nexus.mes.release.service.IReleasePackageService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * + MES-PRO-03(审批通过下达生成工序与快照) / QC-03(完工关键工序IPQC门禁) / MES-PRO-11(取消/关闭)
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
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
        TableInfoHelper.initTableInfo(assistant, com.nexus.mes.pro.domain.ProWorkOrderBomLine.class);
        TableInfoHelper.initTableInfo(assistant, com.nexus.mes.pro.domain.ProFlowCard.class);
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
    @Mock
    private ProFlowCardMapper flowCardMapper;
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

    @ParameterizedTest(name="原生JDBC布尔关键标志{0}必须保留")
    @ValueSource(booleans={false,true})
    void createFromErpMessage_preservesNativeJdbcBooleanCriticalProcess(boolean nativeFlag) {
        // 相同Connector/J实际将ERP TINYINT(1)返回Boolean；不得解析失败后落库为非关键默认值。
        Map<String,Object> step=routingStep(54L,20,54L,"QC","质检",null);
        step.put("is_key_process",nativeFlag);
        stubBridgeLookups(List.of(step));
        service.createFromErpMessage(buildMessage());
        ArgumentCaptor<ProWorkOrderStep> captor=ArgumentCaptor.forClass(ProWorkOrderStep.class);
        verify(stepMapper).insert(captor.capture());
        assertEquals(nativeFlag?1:0,captor.getValue().getIsKeyProcess());
        assertEquals("PENDING",captor.getValue().getStepStatus());
        assertEquals(BigDecimal.TEN,captor.getValue().getPlannedQty());
    }
    @ParameterizedTest(name="原生整型关键标志{0}保持兼容")
    @ValueSource(ints={0,1})
    void createFromErpMessage_preservesNumericCriticalProcess(int nativeFlag) {
        Map<String,Object> step=routingStep(54L,20,54L,"QC","质检",null);
        step.put("is_key_process",nativeFlag);
        stubBridgeLookups(List.of(step));
        service.createFromErpMessage(buildMessage());
        ArgumentCaptor<ProWorkOrderStep> captor=ArgumentCaptor.forClass(ProWorkOrderStep.class);
        verify(stepMapper).insert(captor.capture());
        assertEquals(nativeFlag,captor.getValue().getIsKeyProcess());
    }
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

    // ==================== MES-PRO-03 审批通过下达 ====================

    /**
     * 审批通过必须与 release() 同路径: 生成工序 + MBOM 快照(原实现只改状态, 工序数为 0)
     */
    @Test
    void afterFlowHandle_pass_generatesStepsAndBomSnapshot() {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("1");
        business.setActStatus(ActStatus.pass);

        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoNo("WO-001");
        wo.setWoStatus("APPROVING");
        wo.setSourceType("ERP");
        wo.setRoutingId(9L);
        wo.setBomId(20L);
        wo.setMaterialId(100L);
        when(baseMapper.selectById(1L)).thenReturn(wo);
        when(mesErpBridgeService.getRoutingSteps(9L))
            .thenReturn(List.of(routingStep(12L, 20, 4L, "OP20_ASM", "装配", null)));

        MesMbomVo mbom = new MesMbomVo();
        MesMbomLineVo line = new MesMbomLineVo();
        line.setMbomLineId(1L);
        line.setChildMaterialId(201L);
        line.setChildMaterialCode("SSD-256");
        line.setChildMaterialName("SSD 256G");
        line.setQuantityPerParent(BigDecimal.ONE);
        line.setSortOrder(1);
        mbom.setLines(new java.util.ArrayList<>(List.of(line)));
        when(mesMbomService.selectByBomId(20L)).thenReturn(mbom);

        when(stepMapper.selectCount(any())).thenReturn(0L);
        when(woBomLineMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.afterFlowHandle(business);

        // 工序已生成(原实现审批通过后 pro_work_order_step 为空)
        ArgumentCaptor<ProWorkOrderStep> stepCaptor = ArgumentCaptor.forClass(ProWorkOrderStep.class);
        verify(stepMapper).insert(stepCaptor.capture());
        assertEquals(1L, stepCaptor.getValue().getMesWoId());
        assertEquals("OP20_ASM", stepCaptor.getValue().getProcessCode());
        // MBOM 快照已落库
        verify(woBomLineMapper).insert(any(com.nexus.mes.pro.domain.ProWorkOrderBomLine.class));
        // CAS 置 RELEASED
        verify(baseMapper).update(any(), any());
    }

    /**
     * 无工序步骤 + 手工来源 -> 拒绝下达(原实现对 0 工序直接放行)
     */
    @Test
    void release_rejectsManualWorkOrderWithoutSteps() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoStatus("CREATED");
        wo.setSourceType("MANUAL");
        when(baseMapper.selectById(1L)).thenReturn(wo);
        when(stepMapper.selectCount(any())).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.release(1L));
        assertTrue(ex.getMessage().contains("无工序步骤"));
        verify(baseMapper, never()).update(any(), any());
    }

    // ==================== QC-03 完工关键工序 IPQC 门禁 ====================

    private ProWorkOrder inProgressWo() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoNo("WO-001");
        wo.setWoStatus("IN_PROGRESS");
        wo.setSourceType("ERP");
        return wo;
    }

    private ProWorkOrderStep keyStep(long stepId, long processId, String processName) {
        ProWorkOrderStep step = new ProWorkOrderStep();
        step.setStepId(stepId);
        step.setMesWoId(1L);
        step.setProcessId(processId);
        step.setProcessName(processName);
        step.setIsKeyProcess(1);
        return step;
    }

    /**
     * 关键工序无 IPQC 单 -> 完工被拦截(报工侧自动建单失败后的唯一兜底)
     */
    @Test
    void completeWo_blocksWhenKeyProcessIpqcMissing() {
        when(baseMapper.selectById(1L)).thenReturn(inProgressWo());
        when(stepMapper.selectList(any())).thenReturn(List.of(keyStep(10L, 3L, "BIOS刷写")));
        when(qcIpqcMapper.selectList(any())).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.completeWo(1L));
        assertTrue(ex.getMessage().contains("尚未完成IPQC"));
        assertTrue(ex.getMessage().contains("BIOS刷写"));
        assertTrue(ex.getMessage().contains("尚无IPQC单"));
        verify(baseMapper, never()).update(any(), any());
    }

    /**
     * 关键工序 IPQC 合格 -> 完工放行
     */
    @Test
    void completeWo_passesWhenKeyProcessIpqcQualified() {
        when(baseMapper.selectById(1L)).thenReturn(inProgressWo());
        when(stepMapper.selectList(any())).thenReturn(List.of(keyStep(10L, 3L, "BIOS刷写")));
        QcIpqc qualified = new QcIpqc();
        qualified.setProcessId(3L);
        qualified.setIpqcNo("IPQC-001");
        qualified.setInspectResult("QUALIFIED");
        when(qcIpqcMapper.selectList(any())).thenReturn(List.of(qualified));
        when(baseMapper.update(any(), any())).thenReturn(1);

        // completeWo 会注册 afterCommit 同步(通知 ERP), 单测需激活事务同步
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> service.completeWo(1L));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(baseMapper).update(any(), any());
    }

    /**
     * 门禁优先复用 qc 模块只读接口 IQcIpqcService.selectByMesWoId(此前实现存在但全仓无调用者)
     */
    @Test
    void completeWo_ipqcGateUsesQcIpqcServiceWhenAvailable() {
        when(baseMapper.selectById(1L)).thenReturn(inProgressWo());
        when(stepMapper.selectList(any())).thenReturn(List.of(keyStep(10L, 3L, "BIOS刷写")));

        IQcIpqcService ipqcService = mock(IQcIpqcService.class);
        QcIpqc unqualified = new QcIpqc();
        unqualified.setProcessId(3L);
        unqualified.setIpqcNo("IPQC-009");
        unqualified.setInspectResult("UNQUALIFIED");
        when(ipqcService.selectByMesWoId(1L)).thenReturn(List.of(unqualified));

        try (MockedStatic<SpringUtil> springUtilMock = mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(IQcIpqcService.class)).thenReturn(ipqcService);
            ServiceException ex = assertThrows(ServiceException.class, () -> service.completeWo(1L));
            assertTrue(ex.getMessage().contains("IPQC-009"));
            assertTrue(ex.getMessage().contains("不合格"));
        }
        // 接口可用时不再直查 Mapper
        verify(qcIpqcMapper, never()).selectList(any());
    }

    // ==================== MES-PRO-11 取消/关闭 ====================

    private ProWorkOrder wo(String status) {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(1L);
        wo.setWoNo("WO-001");
        wo.setWoStatus(status);
        wo.setSourceType("ERP");
        return wo;
    }

    @Test
    void cancel_rejectsWorkOrderWithActiveFlowCards() {
        when(baseMapper.selectById(1L)).thenReturn(wo("RELEASED"));
        when(flowCardMapper.selectCount(any())).thenReturn(2L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(1L, "客户撤单"));
        assertTrue(ex.getMessage().contains("在制流转卡"));
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void cancel_rejectsInProgressWorkOrder() {
        when(baseMapper.selectById(1L)).thenReturn(wo("IN_PROGRESS"));
        when(flowCardMapper.selectCount(any())).thenReturn(0L);
        // 状态不在可取消集合内 -> CAS 影响 0 行
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(1L, null));
        assertTrue(ex.getMessage().contains("仅新建/已下达/审批中状态可取消"));
    }

    @Test
    void cancel_releasedWorkOrder_succeeds() {
        when(baseMapper.selectById(1L)).thenReturn(wo("RELEASED"));
        when(flowCardMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.cancel(1L, "客户撤单"));
        verify(baseMapper).update(any(), any());
    }

    @Test
    void cancel_throwsWhenCasAffectsNoRow() {
        when(baseMapper.selectById(1L)).thenReturn(wo("CREATED"));
        when(flowCardMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(1L, "重复取消"));
        assertTrue(ex.getMessage().contains("工单状态已变更"));
    }

    @Test
    void close_completedWorkOrder_succeeds() {
        when(baseMapper.selectById(1L)).thenReturn(wo("COMPLETED"));
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.close(1L));
        verify(baseMapper).update(any(), any());
    }

    @Test
    void close_rejectsUnfinishedWorkOrder() {
        when(baseMapper.selectById(1L)).thenReturn(wo("IN_PROGRESS"));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.close(1L));
        assertTrue(ex.getMessage().contains("仅已完工/部分完工状态可关闭"));
    }
}
