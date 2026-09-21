package com.nexus.iot.mqtt;

import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import io.moquette.broker.subscriptions.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * MQTT Topic 授权策略单元测试。
 * <p>
 * 覆盖 IOT-MQTT-01（身份口径统一为 clientId）与 IOT-MQTT-03（设备级隔离：
 * 禁止 {@code iot/{tenant}/#} 订阅同租户全部设备、禁止发布/订阅他人 deviceCode 的 topic）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotMqttAuthorizatorPolicyTest {

    private IIotDeviceService deviceService;
    private IotMqttAuthorizatorPolicy policy;

    @BeforeEach
    void setUp() {
        deviceService = Mockito.mock(IIotDeviceService.class);
        policy = new IotMqttAuthorizatorPolicy(deviceService);
        when(deviceService.selectEntityByCode("DEV0001")).thenReturn(device("DEV0001", "000000"));
        when(deviceService.selectEntityByCode("DEV0002")).thenReturn(device("DEV0002", "000000"));
        when(deviceService.selectEntityByCode("DEV0009")).thenReturn(device("DEV0009", "000001"));
    }

    private IotDevice device(String code, String tenantId) {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setDeviceCode(code);
        d.setTenantId(tenantId);
        d.setStatus("ACTIVE");
        return d;
    }

    private Topic topic(String s) {
        return new Topic(s);
    }

    // ---------------- canWrite ----------------

    @Test
    void canWrite_ownTopic_allowed() {
        assertTrue(policy.canWrite(topic("iot/000000/DEV0001/property/post"), "DEV0001", "DEV0001"));
    }

    /** IOT-MQTT-03：同租户内不得向他人 deviceCode 的 topic 发布。 */
    @Test
    void canWrite_otherDeviceTopicSameTenant_denied() {
        assertFalse(policy.canWrite(topic("iot/000000/DEV0002/property/post"), "DEV0001", "DEV0001"));
    }

    @Test
    void canWrite_otherTenantTopic_denied() {
        assertFalse(policy.canWrite(topic("iot/000001/DEV0001/property/post"), "DEV0001", "DEV0001"));
    }

    /** IOT-MQTT-01：username 不得作为身份来源，身份一律取 client。 */
    @Test
    void canWrite_identityComesFromClientIdNotUsername() {
        // user 声称 DEV0002，但 client 是 DEV0001 → 以 client 为准，写自己的 topic 通过
        assertTrue(policy.canWrite(topic("iot/000000/DEV0001/property/post"), "DEV0002", "DEV0001"));
        // 反之：user=DEV0001 也无法让 client=DEV0002 写 DEV0001 的 topic
        assertFalse(policy.canWrite(topic("iot/000000/DEV0001/property/post"), "DEV0001", "DEV0002"));
    }

    @Test
    void canWrite_unknownDevice_denied() {
        assertFalse(policy.canWrite(topic("iot/000000/DEV9999/property/post"), "DEV9999", "DEV9999"));
    }

    @Test
    void canWrite_nonIotTopicPrefix_denied() {
        assertFalse(policy.canWrite(topic("other/000000/DEV0001/property/post"), "DEV0001", "DEV0001"));
    }

    // ---------------- canRead ----------------

    @Test
    void canRead_ownTopic_allowed() {
        assertTrue(policy.canRead(topic("iot/000000/DEV0001/property/set"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_ownSubtreeWildcard_allowed() {
        assertTrue(policy.canRead(topic("iot/000000/DEV0001/#"), "DEV0001", "DEV0001"));
    }

    /** IOT-MQTT-03 核心：禁止 {@code iot/{tenant}/#} 窃听同租户全部设备数据。 */
    @Test
    void canRead_tenantWildcard_denied() {
        assertFalse(policy.canRead(topic("iot/000000/#"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_otherDeviceSubtreeWildcard_denied() {
        assertFalse(policy.canRead(topic("iot/000000/DEV0002/#"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_otherDeviceTopic_denied() {
        assertFalse(policy.canRead(topic("iot/000000/DEV0002/property/set"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_deviceSegmentWildcard_denied() {
        assertFalse(policy.canRead(topic("iot/000000/+/property/set"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_otherTenant_denied() {
        assertFalse(policy.canRead(topic("iot/000001/DEV0001/property/set"), "DEV0001", "DEV0001"));
    }

    @Test
    void canRead_missingDeviceSegment_denied() {
        assertFalse(policy.canRead(topic("iot/000000"), "DEV0001", "DEV0001"));
    }

}
