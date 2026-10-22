package com.nexus.iot.command.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.nexus.iot.command.mapper.IotDeviceCommandMapper;
import com.nexus.iot.command.service.impl.IotDeviceCommandServiceImpl;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotMqttPublisher;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * IoT 下行指令单元测试（IOT-PIPE-02 下行通道 / §4#8）。
 * <p>
 * 覆盖：下发即留痕（先 insert 再 publish）、Topic 规约、禁用设备拒绝下发、
 * 发布失败落 FAILED（不伪造成功）、设备回执更新指令状态（CAS + 幂等）、已成功指令不可重发。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotDeviceCommandServiceTest {

    private static final String TENANT = "000000";

    private IotDeviceCommandMapper baseMapper;
    private IIotDeviceService deviceService;
    private IotMqttPublisher publisher;
    private IotDeviceCommandServiceImpl service;

    private MockedStatic<TenantHelper> tenantMock;
    private MockedStatic<LoginHelper> loginMock;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceCommand.class);
        baseMapper = mock(IotDeviceCommandMapper.class);
        deviceService = mock(IIotDeviceService.class);
        publisher = mock(IotMqttPublisher.class);
        service =
                new IotDeviceCommandServiceImpl(
                        baseMapper, deviceService, publisher, new ObjectMapper());

        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(TenantHelper::getTenantId).thenReturn(TENANT);
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

    private IotDeviceVo deviceVo(String status) {
        IotDeviceVo vo = new IotDeviceVo();
        vo.setDeviceId(1L);
        vo.setDeviceCode("DEV0001");
        vo.setStatus(status);
        return vo;
    }

    private IotDeviceCommandBo bo() {
        IotDeviceCommandBo bo = new IotDeviceCommandBo();
        bo.setDeviceId(1L);
        bo.setParams(Map.of("temperature", 60));
        return bo;
    }

    /** 下发属性设置：先落库（留痕）→ 发布 → 状态 SENT，Topic 符合规约。 */
    @Test
    void sendPropertySet_persistsThenPublishes() {
        when(deviceService.selectById(1L)).thenReturn(deviceVo(IotDeviceStatus.ACTIVE));
        when(baseMapper.insert(any(IotDeviceCommand.class))).thenAnswer(inv -> {
            IotDeviceCommand c = inv.getArgument(0);
            c.setCommandId(1001L);
            return 1;
        });
        when(publisher.publish(anyString(), anyString())).thenReturn(true);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(1001L)).thenReturn(new IotDeviceCommand());

        service.sendPropertySet(bo());

        ArgumentCaptor<IotDeviceCommand> insertCaptor = ArgumentCaptor.forClass(IotDeviceCommand.class);
        verify(baseMapper).insert(insertCaptor.capture());
        IotDeviceCommand saved = insertCaptor.getValue();
        assertEquals(IotDeviceCommandServiceImpl.STATUS_PENDING, saved.getStatus(), "必须先以 PENDING 留痕");
        assertEquals("iot/" + TENANT + "/DEV0001/property/set", saved.getTopic());
        assertEquals("admin", saved.getOperatorName());

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(topicCaptor.capture(), payloadCaptor.capture());
        assertEquals("iot/" + TENANT + "/DEV0001/property/set", topicCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("\"commandId\":\"1001\""), "载荷必须携带指令号供设备回执匹配");
        assertTrue(payloadCaptor.getValue().contains("temperature"));

        ArgumentCaptor<Wrapper<IotDeviceCommand>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        LambdaUpdateWrapper<IotDeviceCommand> wrapper = (LambdaUpdateWrapper<IotDeviceCommand>) updateCaptor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotDeviceCommandServiceImpl.STATUS_SENT));
    }

    /** 服务调用：Topic 走 event/set，且必须指定服务名。 */
    @Test
    void sendServiceInvoke_usesEventSetTopic() {
        when(deviceService.selectById(1L)).thenReturn(deviceVo(IotDeviceStatus.ACTIVE));
        when(baseMapper.insert(any(IotDeviceCommand.class))).thenAnswer(inv -> {
            IotDeviceCommand c = inv.getArgument(0);
            c.setCommandId(1002L);
            return 1;
        });
        when(publisher.publish(anyString(), anyString())).thenReturn(true);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(1002L)).thenReturn(new IotDeviceCommand());

        IotDeviceCommandBo bo = bo();
        bo.setServiceName("reboot");
        service.sendServiceInvoke(bo);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(topicCaptor.capture(), anyString());
        assertEquals("iot/" + TENANT + "/DEV0001/event/set", topicCaptor.getValue());
    }

    /** 服务调用缺服务名 → 拒绝（不落库、不下发）。 */
    @Test
    void sendServiceInvoke_withoutServiceName_rejected() {
        assertThrows(ServiceException.class, () -> service.sendServiceInvoke(bo()));
        verify(baseMapper, never()).insert(any(IotDeviceCommand.class));
    }

    /** 禁用设备不可下发（与 MQTT 认证口径一致）。 */
    @Test
    void send_disabledDevice_rejected() {
        when(deviceService.selectById(1L)).thenReturn(deviceVo(IotDeviceStatus.INACTIVE));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.sendPropertySet(bo()));
        assertTrue(ex.getMessage().contains("不可下发"));
        verify(baseMapper, never()).insert(any(IotDeviceCommand.class));
        verify(publisher, never()).publish(anyString(), anyString());
    }

    /** Broker 未启用/发布失败 → 状态落 FAILED 并写明原因（不伪造"已下发"）。 */
    @Test
    @SuppressWarnings("unchecked")
    void send_publishFailure_marksFailed() {
        when(deviceService.selectById(1L)).thenReturn(deviceVo(IotDeviceStatus.ACTIVE));
        when(baseMapper.insert(any(IotDeviceCommand.class))).thenAnswer(inv -> {
            IotDeviceCommand c = inv.getArgument(0);
            c.setCommandId(1003L);
            return 1;
        });
        when(publisher.publish(anyString(), anyString())).thenReturn(false);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectById(1003L)).thenReturn(new IotDeviceCommand());

        service.sendPropertySet(bo());

        ArgumentCaptor<Wrapper<IotDeviceCommand>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotDeviceCommand> wrapper = (LambdaUpdateWrapper<IotDeviceCommand>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotDeviceCommandServiceImpl.STATUS_FAILED));
        assertTrue(wrapper.getParamNameValuePairs().values().stream()
                .anyMatch(v -> v instanceof String s && s.contains("未启用")),
            "失败原因必须写明 Broker 未启用，便于运维定位");
    }

    /** 设备回执：CAS 更新为 SUCCESS，并写入回执时间与载荷。 */
    @Test
    @SuppressWarnings("unchecked")
    void handleReply_updatesCommandStatus() {
        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setCommandId(1001L);
        cmd.setDeviceCode("DEV0001");
        cmd.setStatus(IotDeviceCommandServiceImpl.STATUS_SENT);
        when(baseMapper.selectById(1001L)).thenReturn(cmd);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Map<Object, Object> entry = new HashMap<>();
        entry.put("command_id", "1001");
        entry.put("success", "true");
        entry.put("message", "ok");
        entry.put("payload", "{\"success\":true}");
        service.handleReply(deviceEntity(), entry, "1758326401-5");

        ArgumentCaptor<Wrapper<IotDeviceCommand>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotDeviceCommand> wrapper = (LambdaUpdateWrapper<IotDeviceCommand>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotDeviceCommandServiceImpl.STATUS_SUCCESS));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("1758326401-5"), "回执 Stream id 作为幂等键落库");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ok"));
    }

    /** 回执幂等：已回执（CAS 0 行）→ 不抛异常、不改写。 */
    @Test
    void handleReply_duplicate_isIdempotent() {
        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setCommandId(1001L);
        cmd.setDeviceCode("DEV0001");
        when(baseMapper.selectById(1001L)).thenReturn(cmd);
        when(baseMapper.update(any(), any())).thenReturn(0);

        Map<Object, Object> entry = new HashMap<>();
        entry.put("command_id", "1001");
        entry.put("success", "true");
        service.handleReply(deviceEntity(), entry, "1758326401-6");

        verify(baseMapper).update(any(), any());
    }

    /** 回执未带 commandId → 回落到"该设备最近一条待回执指令"。 */
    @Test
    void handleReply_withoutCommandId_fallsBackToLatestPending() {
        IotDeviceCommand latest = new IotDeviceCommand();
        latest.setCommandId(1009L);
        latest.setDeviceCode("DEV0001");
        when(baseMapper.selectOne(any())).thenReturn(latest);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Map<Object, Object> entry = new HashMap<>();
        entry.put("success", "false");
        entry.put("message", "参数越界");
        service.handleReply(deviceEntity(), entry, "1758326401-7");

        verify(baseMapper).selectOne(any());
        verify(baseMapper).update(any(), any());
    }

    /** 未匹配到指令 → 仅记日志，不抛异常（不影响事件流消费）。 */
    @Test
    void handleReply_unmatched_noUpdate() {
        when(baseMapper.selectOne(any())).thenReturn(null);
        Map<Object, Object> entry = new HashMap<>();
        entry.put("success", "true");
        service.handleReply(deviceEntity(), entry, "1758326401-8");
        verify(baseMapper, never()).update(any(), any());
    }

    /** 已成功指令不可重发。 */
    @Test
    void resend_successCommand_rejected() {
        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setCommandId(1001L);
        cmd.setStatus(IotDeviceCommandServiceImpl.STATUS_SUCCESS);
        when(baseMapper.selectById(1001L)).thenReturn(cmd);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.resend(1001L));
        assertTrue(ex.getMessage().contains("无需重发"));
        verify(publisher, never()).publish(anyString(), anyString());
    }

    /** 失败指令可重发：累加重发次数并重新发布。 */
    @Test
    void resend_failedCommand_republishes() {
        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setCommandId(1001L);
        cmd.setStatus(IotDeviceCommandServiceImpl.STATUS_FAILED);
        cmd.setTopic("iot/000000/DEV0001/property/set");
        cmd.setPayload("{\"commandId\":\"1001\"}");
        when(baseMapper.selectById(1001L)).thenReturn(cmd);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(publisher.publish(anyString(), anyString())).thenReturn(true);

        assertNotNull(service.resend(1001L));
        verify(publisher).publish(eq("iot/000000/DEV0001/property/set"), anyString());
    }

    private IotDevice deviceEntity() {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setTenantId(TENANT);
        d.setDeviceCode("DEV0001");
        return d;
    }
}
