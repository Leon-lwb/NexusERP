package com.nexus.iot.mqtt;

import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * MQTT 设备认证器单元测试。
 * <p>
 * 覆盖 IOT-MQTT-01 / IOT-MQTT-02：身份口径必须统一为 clientId，
 * 禁止用他人 deviceCode 作 clientId 认证（否则可冒充/顶掉任意设备）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotMqttAuthenticatorTest {

    private static final String SECRET = "sec-of-dev0001";

    private IIotDeviceService deviceService;
    private IotMqttAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        deviceService = Mockito.mock(IIotDeviceService.class);
        authenticator = new IotMqttAuthenticator(deviceService);
        when(deviceService.selectEntityByCode("DEV0001"))
            .thenReturn(device("DEV0001", SECRET, "ACTIVE"));
        when(deviceService.selectEntityByCode("DEV0002"))
            .thenReturn(device("DEV0002", SECRET, "INACTIVE"));
        when(deviceService.selectEntityByCode("DEV0003"))
            .thenReturn(device("DEV0003", "", "ACTIVE"));
    }

    private IotDevice device(String code, String secret, String status) {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setDeviceCode(code);
        d.setDeviceSecret(secret);
        d.setStatus(status);
        d.setTenantId("000000");
        return d;
    }

    private byte[] pw(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void checkValid_clientIdEqualsDeviceCode_passes() {
        assertTrue(authenticator.checkValid("DEV0001", "DEV0001", pw(SECRET)));
    }

    @Test
    void checkValid_usernameBlank_passes() {
        assertTrue(authenticator.checkValid("DEV0001", null, pw(SECRET)));
    }

    /**
     * IOT-MQTT-01/02 核心断言：拿自己的密钥 + 他人 deviceCode 作 clientId 必须认证失败。
     * <p>修复前该用例返回 true（认证用 username=DEV0001 通过），故可冒充 DEV0006。
     */
    @Test
    void checkValid_usernameDiffersFromClientId_rejected() {
        assertFalse(authenticator.checkValid("DEV0006", "DEV0001", pw(SECRET)));
    }

    /**
     * username 指向他人设备、clientId 指向自己：username 不参与身份判定，但两者不等也必须拒绝。
     */
    @Test
    void checkValid_usernameIsOtherDevice_rejected() {
        assertFalse(authenticator.checkValid("DEV0001", "DEV0006", pw(SECRET)));
    }

    @Test
    void checkValid_blankClientId_rejected() {
        assertFalse(authenticator.checkValid("", "DEV0001", pw(SECRET)));
        assertFalse(authenticator.checkValid("   ", "DEV0001", pw(SECRET)));
        assertFalse(authenticator.checkValid(null, null, pw(SECRET)));
    }

    @Test
    void checkValid_nullPassword_rejected() {
        assertFalse(authenticator.checkValid("DEV0001", "DEV0001", null));
    }

    @Test
    void checkValid_unknownDevice_rejected() {
        assertFalse(authenticator.checkValid("DEV9999", "DEV9999", pw(SECRET)));
    }

    @Test
    void checkValid_inactiveDevice_rejected() {
        assertFalse(authenticator.checkValid("DEV0002", "DEV0002", pw(SECRET)));
    }

    @Test
    void checkValid_wrongSecret_rejected() {
        assertFalse(authenticator.checkValid("DEV0001", "DEV0001", pw("wrong-secret")));
    }

    @Test
    void checkValid_blankStoredSecret_rejected() {
        assertFalse(authenticator.checkValid("DEV0003", "DEV0003", pw("")));
    }

}
