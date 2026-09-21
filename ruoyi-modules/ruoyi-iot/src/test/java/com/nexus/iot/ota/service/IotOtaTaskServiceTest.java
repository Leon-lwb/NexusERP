package com.nexus.iot.ota.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.command.domain.IotDeviceCommand;
import com.nexus.iot.command.domain.bo.IotDeviceCommandBo;
import com.nexus.iot.command.service.IIotDeviceCommandService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.group.service.IIotDeviceGroupService;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.domain.IotFirmwareStatus;
import com.nexus.iot.firmware.mapper.IotFirmwareMapper;
import com.nexus.iot.ota.domain.IotOtaStatus;
import com.nexus.iot.ota.domain.IotOtaTask;
import com.nexus.iot.ota.domain.bo.IotOtaBatchTaskBo;
import com.nexus.iot.ota.domain.bo.IotOtaReportBo;
import com.nexus.iot.ota.domain.bo.IotOtaTaskBo;
import com.nexus.iot.ota.domain.vo.IotOtaBatchResultVo;
import com.nexus.iot.ota.domain.vo.IotOtaVersionCheckVo;
import com.nexus.iot.ota.mapper.IotOtaTaskMapper;
import com.nexus.iot.ota.service.impl.IotOtaTaskServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IoT OTA 升级任务单元测试（OTA MVP）。
 * <p>
 * 覆盖任务要求的 6 项必测：
 * <ol>
 *   <li>固件发布状态机 —— 见 {@code IotFirmwareServiceTest}（本类覆盖"未发布固件不可下发"）；</li>
 *   <li>按分组批量建任务（含禁用设备跳过）；</li>
 *   <li>离线/禁用设备拒绝下发；</li>
 *   <li>下发失败落 FAILED（不伪造成功）；</li>
 *   <li>回执状态机（含非法流转被拒、CAS 冲突、进度单调）；</li>
 *   <li>超时任务被标记 TIMEOUT。</li>
 * </ol>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotOtaTaskServiceTest {

    private static final String TENANT = "000000";

    private IotOtaTaskMapper baseMapper;
    private IotFirmwareMapper firmwareMapper;
    private IotDeviceMapper deviceMapper;
    private IotProductMapper productMapper;
    private IIotDeviceService deviceService;
    private IIotDeviceGroupService deviceGroupService;
    private IIotDeviceCommandService commandService;
    private IotOtaTaskServiceImpl service;

    private MockedStatic<TenantHelper> tenantMock;
    private MockedStatic<LoginHelper> loginMock;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotOtaTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDevice.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotFirmware.class);
        baseMapper = mock(IotOtaTaskMapper.class);
        firmwareMapper = mock(IotFirmwareMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        productMapper = mock(IotProductMapper.class);
        deviceService = mock(IIotDeviceService.class);
        deviceGroupService = mock(IIotDeviceGroupService.class);
        commandService = mock(IIotDeviceCommandService.class);
        service = new IotOtaTaskServiceImpl(baseMapper, firmwareMapper, deviceMapper, productMapper,
            deviceService, deviceGroupService, commandService, new ObjectMapper());

        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
            .thenAnswer(inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return null;
            });
        loginMock = mockStatic(LoginHelper.class);
        loginMock.when(LoginHelper::getUserId).thenReturn(9L);
        loginMock.when(LoginHelper::getUsername).thenReturn("admin");
    }

    @AfterEach
    void tearDown() {
        loginMock.close();
        tenantMock.close();
    }

    // ------------------------------------------------------------------ fixtures

    private IotFirmware firmware(String status) {
        IotFirmware fw = new IotFirmware();
        fw.setFirmwareId(21L);
        fw.setFirmwareName("主控固件");
        fw.setVersion("2.0.0");
        fw.setProductKey("");
        fw.setFileOssId("oss-2001");
        fw.setFileName("fw-2.0.0.bin");
        fw.setFileSize(4096L);
        fw.setChecksumType("MD5");
        fw.setChecksum("abc123");
        fw.setStatus(status);
        return fw;
    }

    private IotDeviceVo deviceVo(Long id, String code, String status) {
        IotDeviceVo vo = new IotDeviceVo();
        vo.setDeviceId(id);
        vo.setDeviceCode(code);
        vo.setProductId(5L);
        vo.setFirmwareVersion("1.0.0");
        vo.setStatus(status);
        return vo;
    }

    private IotOtaTask task(String status, int progress) {
        IotOtaTask t = new IotOtaTask();
        t.setTaskId(100L);
        t.setDeviceId(1L);
        t.setDeviceCode("DEV0001");
        t.setFirmwareId(21L);
        t.setTargetVersion("2.0.0");
        t.setCurrentVersion("1.0.0");
        t.setStatus(status);
        t.setProgress(progress);
        return t;
    }

    private IotDevice deviceEntity() {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setDeviceCode("DEV0001");
        d.setTenantId(TENANT);
        d.setStatus(IotDeviceStatus.ACTIVE);
        d.setFirmwareVersion("1.0.0");
        return d;
    }

    private IotOtaTaskBo bo() {
        IotOtaTaskBo bo = new IotOtaTaskBo();
        bo.setDeviceId(1L);
        bo.setFirmwareId(21L);
        return bo;
    }

    private IotOtaReportBo reportBo(String status) {
        IotOtaReportBo bo = new IotOtaReportBo();
        bo.setDeviceId(1L);
        bo.setStatus(status);
        return bo;
    }

    private IotDeviceCommand sentCommand(long commandId) {
        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setCommandId(commandId);
        cmd.setStatus("SENT");
        return cmd;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<IotOtaTask> captureTaskUpdate() {
        ArgumentCaptor<Wrapper<IotOtaTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        return (LambdaUpdateWrapper<IotOtaTask>) captor.getValue();
    }

    // ------------------------------------------------------------------ 创建任务

    /** 单设备下发：先以 PENDING 留痕 → 复用下行指令通道（event/set + otaUpgrade）→ 回写 SENT。 */
    @Test
    void createTask_persistsPendingThenDispatches() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(IotOtaTask.class))).thenAnswer(inv -> {
            ((IotOtaTask) inv.getArgument(0)).setTaskId(100L);
            return 1;
        });
        when(commandService.sendServiceInvoke(any(IotDeviceCommandBo.class))).thenReturn(sentCommand(9001L));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));

        service.createTask(bo());

        ArgumentCaptor<IotOtaTask> insertCaptor = ArgumentCaptor.forClass(IotOtaTask.class);
        verify(baseMapper).insert(insertCaptor.capture());
        IotOtaTask saved = insertCaptor.getValue();
        assertEquals(IotOtaStatus.PENDING, saved.getStatus(), "必须先以 PENDING 留痕，再下发");
        assertEquals("1.0.0", saved.getCurrentVersion(), "记录下发时的设备当前版本");
        assertEquals("2.0.0", saved.getTargetVersion());
        assertEquals("admin", saved.getOperatorName());

        ArgumentCaptor<IotDeviceCommandBo> cmdCaptor = ArgumentCaptor.forClass(IotDeviceCommandBo.class);
        verify(commandService).sendServiceInvoke(cmdCaptor.capture());
        assertEquals(IotOtaTaskServiceImpl.SERVICE_OTA_UPGRADE, cmdCaptor.getValue().getServiceName());
        assertEquals("100", cmdCaptor.getValue().getParams().get("otaTaskId"), "载荷必须携带任务号供设备回执匹配");
        assertEquals("2.0.0", cmdCaptor.getValue().getParams().get("version"));
        assertEquals("abc123", cmdCaptor.getValue().getParams().get("checksum"), "校验值随指令下发");

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.SENT));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(9001L), "任务必须记录指令留痕ID");
    }

    /** 禁用设备（离线/停用）拒绝下发，不落任务、不下发指令。 */
    @Test
    void createTask_disabledDevice_rejected() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.INACTIVE));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createTask(bo()));
        assertTrue(ex.getMessage().contains("不可下发升级任务"));
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
        verify(commandService, never()).sendServiceInvoke(any());
    }

    /** 注销设备同样拒绝。 */
    @Test
    void createTask_decommissionedDevice_rejected() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.DECOMMISSIONED));

        assertThrows(ServiceException.class, () -> service.createTask(bo()));
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
    }

    /** 未发布固件不可下发。 */
    @Test
    void createTask_firmwareNotReleased_rejected() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.DRAFT));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createTask(bo()));
        assertTrue(ex.getMessage().contains("RELEASED"));
        verify(deviceService, never()).selectById(any());
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
    }

    /** 固件适用产品型号与设备不匹配 → 拒绝。 */
    @Test
    void createTask_productMismatch_rejected() {
        IotFirmware fw = firmware(IotFirmwareStatus.RELEASED);
        fw.setProductKey("PK-A");
        when(firmwareMapper.selectById(21L)).thenReturn(fw);
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        IotProduct product = new IotProduct();
        product.setProductId(5L);
        product.setProductCode("PK-B");
        when(productMapper.selectById(5L)).thenReturn(product);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createTask(bo()));
        assertTrue(ex.getMessage().contains("不匹配"));
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
    }

    /** 业务幂等：同设备 + 同固件已存在进行中任务 → 拒绝重复下发。 */
    @Test
    void createTask_duplicateActiveTask_rejected() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createTask(bo()));
        assertTrue(ex.getMessage().contains("进行中"));
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
        verify(commandService, never()).sendServiceInvoke(any());
    }

    /** 指令下发失败（Broker 未启用）→ 任务落 FAILED 并写明"仅登记未下发"，不伪造成功。 */
    @Test
    void createTask_dispatchFailure_marksFailed() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(IotOtaTask.class))).thenAnswer(inv -> {
            ((IotOtaTask) inv.getArgument(0)).setTaskId(101L);
            return 1;
        });
        IotDeviceCommand failed = new IotDeviceCommand();
        failed.setCommandId(9002L);
        failed.setStatus("FAILED");
        when(commandService.sendServiceInvoke(any(IotDeviceCommandBo.class))).thenReturn(failed);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(101L)).thenReturn(task(IotOtaStatus.FAILED, 0));

        service.createTask(bo());

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.FAILED));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(v -> v instanceof String s && s.contains("未下发")),
            "失败原因必须写明指令未下发，便于运维定位");
    }

    // ------------------------------------------------------------------ 批量下发

    /** 按分组批量下发：复用分组关系表，禁用设备跳过且不影响其余设备（部分成功语义）。 */
    @Test
    void createBatchTask_byGroup_skipsDisabledDevice() {
        when(deviceGroupService.selectDeviceIds(7L, Boolean.TRUE)).thenReturn(List.of(1L, 2L, 3L));
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        when(deviceService.selectById(2L)).thenReturn(deviceVo(2L, "DEV0002", IotDeviceStatus.INACTIVE));
        when(deviceService.selectById(3L)).thenReturn(deviceVo(3L, "DEV0003", IotDeviceStatus.ACTIVE));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        AtomicLong idSeq = new AtomicLong(200L);
        when(baseMapper.insert(any(IotOtaTask.class))).thenAnswer(inv -> {
            ((IotOtaTask) inv.getArgument(0)).setTaskId(idSeq.getAndIncrement());
            return 1;
        });
        when(commandService.sendServiceInvoke(any(IotDeviceCommandBo.class))).thenReturn(sentCommand(9100L));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(any())).thenReturn(task(IotOtaStatus.SENT, 0));

        IotOtaBatchTaskBo bo = new IotOtaBatchTaskBo();
        bo.setFirmwareId(21L);
        bo.setGroupId(7L);
        bo.setIncludeSubGroup(Boolean.TRUE);
        IotOtaBatchResultVo result = service.createBatchTask(bo);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getCreated(), "两台 ACTIVE 设备各建一条任务");
        assertEquals(1, result.getSkipped(), "禁用设备跳过");
        assertEquals(1, result.getMessages().size());
        assertTrue(result.getMessages().get(0).contains("DEV0002") || result.getMessages().get(0).contains("设备[2]"));
        assertNotNull(result.getBatchNo());

        ArgumentCaptor<IotOtaTask> insertCaptor = ArgumentCaptor.forClass(IotOtaTask.class);
        verify(baseMapper, times(2)).insert(insertCaptor.capture());
        for (IotOtaTask t : insertCaptor.getAllValues()) {
            assertEquals(7L, t.getGroupId(), "批量任务必须记录来源分组");
            assertEquals(result.getBatchNo(), t.getBatchNo(), "批量任务共用同一批次号");
        }
    }

    /** 分组下无设备 → 直接拒绝（不产生空批次）。 */
    @Test
    void createBatchTask_emptyGroup_rejected() {
        when(deviceGroupService.selectDeviceIds(7L, Boolean.FALSE)).thenReturn(List.of());
        IotOtaBatchTaskBo bo = new IotOtaBatchTaskBo();
        bo.setFirmwareId(21L);
        bo.setGroupId(7L);
        bo.setIncludeSubGroup(Boolean.FALSE);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createBatchTask(bo));
        assertTrue(ex.getMessage().contains("没有设备"));
        verify(firmwareMapper, never()).selectById(any());
        verify(baseMapper, never()).insert(any(IotOtaTask.class));
    }

    // ------------------------------------------------------------------ 回执状态机

    /** 回执进度：SENT → UPGRADING，写入进度与开始时间。 */
    @Test
    void report_sentToUpgrading_updatesProgress() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));
        when(baseMapper.update(any(), any())).thenReturn(1);
        IotOtaReportBo bo = reportBo("UPGRADING");
        bo.setTaskId(100L);
        bo.setProgress(40);

        service.report(bo);

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.UPGRADING));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(40));
    }

    /** 回执进度单调不回退：乱序回执不把进度改小。 */
    @Test
    void report_progressRegression_keptMonotonic() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.UPGRADING, 80));
        when(baseMapper.update(any(), any())).thenReturn(1);
        IotOtaReportBo bo = reportBo("UPGRADING");
        bo.setTaskId(100L);
        bo.setProgress(30);

        service.report(bo);

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(80), "进度必须保持 80 不被回退到 30");
        assertFalse(wrapper.getParamNameValuePairs().containsValue(30));
    }

    /** 回执成功：UPGRADING → SUCCESS，进度钉 100，并回写设备当前固件版本。 */
    @Test
    void report_upgradingToSuccess_writesDeviceFirmwareVersion() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.UPGRADING, 80));
        when(baseMapper.update(any(), any())).thenReturn(1);
        IotOtaReportBo bo = reportBo("SUCCESS");
        bo.setTaskId(100L);
        bo.setProgress(90);

        service.report(bo);

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.SUCCESS));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(100), "成功必须钉 100");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<IotDevice>> devCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deviceMapper).update(any(), devCaptor.capture());
        @SuppressWarnings("unchecked")
        LambdaUpdateWrapper<IotDevice> devWrapper = (LambdaUpdateWrapper<IotDevice>) devCaptor.getValue();
        assertTrue(devWrapper.getParamNameValuePairs().containsValue("2.0.0"),
            "升级成功必须把目标版本回写到 iot_device.firmware_version");
    }

    /** 回执失败：SENT → FAILED，写入失败原因。 */
    @Test
    void report_sentToFailed_writesReason() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));
        when(baseMapper.update(any(), any())).thenReturn(1);
        IotOtaReportBo bo = reportBo("FAILED");
        bo.setTaskId(100L);
        bo.setFailReason("校验值不一致");

        service.report(bo);

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.FAILED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("校验值不一致"));
    }

    /** 非法流转：终态任务（SUCCESS）不接受任何后续回执。 */
    @Test
    void report_illegalTransition_rejected() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SUCCESS, 100));
        IotOtaReportBo bo = reportBo("UPGRADING");
        bo.setTaskId(100L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.report(bo));
        assertTrue(ex.getMessage().contains("非法"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 非法回执状态值（设备不得自行声明 SENT/PENDING）被拒绝。 */
    @Test
    void report_unknownStatus_rejected() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        IotOtaReportBo bo = reportBo("SENT");
        bo.setTaskId(100L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.report(bo));
        assertTrue(ex.getMessage().contains("非法的回执状态"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** CAS 冲突（并发回执）→ 抛异常，不静默成功。 */
    @Test
    void report_casConflict_throws() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));
        when(baseMapper.update(any(), any())).thenReturn(0);
        IotOtaReportBo bo = reportBo("UPGRADING");
        bo.setTaskId(100L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.report(bo));
        assertTrue(ex.getMessage().contains("并发变更"));
    }

    /** 未带 taskId → 回落"该设备最近一条进行中任务"。 */
    @Test
    void report_withoutTaskId_matchesLatestActiveTask() {
        when(deviceMapper.selectById(1L)).thenReturn(deviceEntity());
        when(baseMapper.selectOne(any())).thenReturn(task(IotOtaStatus.SENT, 0));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));

        service.report(reportBo("UPGRADING"));

        verify(baseMapper).selectOne(any());
        assertTrue(captureTaskUpdate().getParamNameValuePairs().containsValue(IotOtaStatus.UPGRADING));
    }

    /** 设备不存在 → 拒绝（不静默丢弃回执）。 */
    @Test
    void report_unknownDevice_rejected() {
        when(deviceMapper.selectById(1L)).thenReturn(null);
        IotOtaReportBo bo = reportBo("UPGRADING");
        bo.setTaskId(100L);

        assertThrows(ServiceException.class, () -> service.report(bo));
        verify(baseMapper, never()).update(any(), any());
    }

    // ------------------------------------------------------------------ 取消 / 重试

    /** 取消：SENT → CANCELLED。 */
    @Test
    void cancel_sentTask_cancelled() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 10));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.cancel(100L, "现场取消");

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.CANCELLED));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
            .anyMatch(v -> v instanceof String s && s.contains("现场取消")));
    }

    /** 已成功任务不可取消（终态）。 */
    @Test
    void cancel_successTask_rejected() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SUCCESS, 100));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(100L, null));
        assertTrue(ex.getMessage().contains("可取消"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 重试：FAILED → SENT（CAS + retry_count+1）并重新下发一条新指令。 */
    @Test
    void retry_failedTask_dispatchesNewCommand() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.FAILED, 60));
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.ACTIVE));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(commandService.sendServiceInvoke(any(IotDeviceCommandBo.class))).thenReturn(sentCommand(9003L));

        service.retry(100L);

        verify(commandService).sendServiceInvoke(any(IotDeviceCommandBo.class));
        ArgumentCaptor<Wrapper<IotOtaTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper, times(2)).update(any(), captor.capture());
        @SuppressWarnings("unchecked")
        LambdaUpdateWrapper<IotOtaTask> first = (LambdaUpdateWrapper<IotOtaTask>) captor.getAllValues().get(0);
        assertTrue(first.getParamNameValuePairs().containsValue(IotOtaStatus.SENT));
        assertNotNull(first.getSqlSet(), "重试必须累加 retry_count");
        assertTrue(first.getSqlSet().contains("retry_count"), "重试必须累加 retry_count");
    }

    /** 进行中任务不可重试（避免重复下发）。 */
    @Test
    void retry_upgradingTask_rejected() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.UPGRADING, 50));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.retry(100L));
        assertTrue(ex.getMessage().contains("可重试"));
        verify(commandService, never()).sendServiceInvoke(any());
    }

    /** 重试时设备已被禁用 → 拒绝。 */
    @Test
    void retry_disabledDevice_rejected() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.FAILED, 60));
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        when(deviceService.selectById(1L)).thenReturn(deviceVo(1L, "DEV0001", IotDeviceStatus.INACTIVE));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.retry(100L));
        assertTrue(ex.getMessage().contains("不可重试升级"));
        verify(commandService, never()).sendServiceInvoke(any());
    }

    // ------------------------------------------------------------------ 超时 / 版本 / 事件旁路

    /** 超时兜底：只把 SENT/UPGRADING 且下发时间早于 deadline 的任务置 TIMEOUT。 */
    @Test
    void markTimeout_marksActiveTasksTimeout() {
        when(baseMapper.update(any(), any())).thenReturn(3);
        Date deadline = new Date();

        assertEquals(3, service.markTimeout(deadline));

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.TIMEOUT));
        // 断言"只扫描 SENT/UPGRADING"：兼容 MyBatis-Plus 对 in(...) 的两种参数存放形态
        // （整集合一个占位符 / 逐元素占位符），并以 SQL 片段兜底。
        java.util.Collection<Object> values = wrapper.getParamNameValuePairs().values();
        String sql = String.valueOf(wrapper.getSqlSegment()).toLowerCase();
        boolean scoped = values.stream().anyMatch(v -> v instanceof java.util.Collection<?> c
                && c.contains(IotOtaStatus.SENT) && c.contains(IotOtaStatus.UPGRADING))
            || (values.contains(IotOtaStatus.SENT) && values.contains(IotOtaStatus.UPGRADING))
            || (sql.contains("status in") && sql.contains("sent_time <"));
        assertTrue(scoped, "只扫描 SENT/UPGRADING 且按 sent_time < deadline：params=" + values + ", sql=" + sql);
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
            .anyMatch(v -> v instanceof String s && s.contains("超时")));
    }

    /** 版本一致性核对：返回不一致设备，并标记落后（分段数值比较）。 */
    @Test
    void checkVersion_returnsMismatchedDevices() {
        when(firmwareMapper.selectById(21L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        IotDevice older = deviceEntity();
        older.setFirmwareVersion("1.9.0");
        IotDevice newer = deviceEntity();
        newer.setDeviceId(2L);
        newer.setDeviceCode("DEV0002");
        newer.setFirmwareVersion("3.0.0");
        when(deviceMapper.selectList(any())).thenReturn(List.of(older, newer));

        List<IotOtaVersionCheckVo> all = service.checkVersion(21L, null);
        assertEquals(2, all.size());
        assertTrue(all.get(0).getBehind(), "1.9.0 落后于 2.0.0");
        assertFalse(all.get(1).getBehind(), "3.0.0 超前不算落后");
        assertEquals("2.0.0", all.get(0).getTargetVersion());

        List<IotOtaVersionCheckVo> behindOnly = service.checkVersion(21L, Boolean.TRUE);
        assertEquals(1, behindOnly.size());
        assertEquals("DEV0001", behindOnly.get(0).getDeviceCode());
    }

    /** 版本落后判定：分段数值比较（1.10.0 > 1.9.0），空版本视为落后。 */
    @Test
    void isBehind_numericSegments() {
        assertTrue(IotOtaTaskServiceImpl.isBehind("1.9.0", "1.10.0"));
        assertFalse(IotOtaTaskServiceImpl.isBehind("2.0.0", "2.0.0"));
        assertFalse(IotOtaTaskServiceImpl.isBehind("2.1.0", "2.0.0"));
        assertTrue(IotOtaTaskServiceImpl.isBehind(null, "2.0.0"));
        assertTrue(IotOtaTaskServiceImpl.isBehind("", "2.0.0"));
    }

    /** 事件旁路：非 OTA 事件直接忽略（不触碰任务表）。 */
    @Test
    void handleOtaEvent_nonOtaEvent_ignored() {
        Map<Object, Object> entry = new HashMap<>();
        entry.put("event_code", "TEMP_HIGH");
        entry.put("payload", "{\"value\":90}");

        service.handleOtaEvent(deviceEntity(), entry, "1758326401-1");

        verify(baseMapper, never()).update(any(), any());
        verify(baseMapper, never()).selectOne(any());
    }

    /** 事件旁路：OTA_RESULT 成功事件推进任务为 SUCCESS。 */
    @Test
    void handleOtaEvent_otaResult_success_updatesTask() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.UPGRADING, 80));
        when(baseMapper.update(any(), any())).thenReturn(1);
        Map<Object, Object> entry = new HashMap<>();
        entry.put("event_code", "OTA_RESULT");
        entry.put("payload", "{\"otaTaskId\":\"100\",\"success\":true,\"progress\":100}");

        service.handleOtaEvent(deviceEntity(), entry, "1758326401-9");

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.SUCCESS));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("1758326401-9"), "回执 Stream id 落库便于取证");
    }

    /** 事件旁路：OTA_PROGRESS 事件推进为 UPGRADING。 */
    @Test
    void handleOtaEvent_otaProgress_updatesToUpgrading() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SENT, 0));
        when(baseMapper.update(any(), any())).thenReturn(1);
        Map<Object, Object> entry = new HashMap<>();
        entry.put("event_code", "OTA_PROGRESS");
        entry.put("payload", "{\"taskId\":\"100\",\"progress\":55}");

        service.handleOtaEvent(deviceEntity(), entry, "1758326401-10");

        LambdaUpdateWrapper<IotOtaTask> wrapper = captureTaskUpdate();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotOtaStatus.UPGRADING));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(55));
    }

    /** 事件旁路：状态机拒绝（终态任务）不得抛出异常影响事件流消费。 */
    @Test
    void handleOtaEvent_illegalTransition_swallowed() {
        when(baseMapper.selectById(100L)).thenReturn(task(IotOtaStatus.SUCCESS, 100));
        Map<Object, Object> entry = new HashMap<>();
        entry.put("event_code", "OTA_RESULT");
        entry.put("payload", "{\"otaTaskId\":\"100\",\"success\":false}");

        service.handleOtaEvent(deviceEntity(), entry, "1758326401-11");

        verify(baseMapper, never()).update(any(), any());
    }
}
