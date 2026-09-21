package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 二级认证调度器测试。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class SignAuthVerifierTest {

    private static final Long UID = 7L;

    /** 记录被调用情况的假实现 */
    private static class FakeChecker implements SignCredentialChecker {
        final String method;
        final AtomicBoolean called = new AtomicBoolean(false);
        final boolean shouldFail;

        FakeChecker(String method, boolean shouldFail) {
            this.method = method;
            this.shouldFail = shouldFail;
        }

        @Override
        public String authMethod() {
            return method;
        }

        @Override
        public void verify(Long userId, String credential) {
            called.set(true);
            if (shouldFail) {
                throw new ServiceException("凭证错误");
            }
        }
    }

    @Test
    @DisplayName("LOGIN_ONLY: 不调用任何 checker，直接通过")
    void loginOnly_passesWithoutChecker() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.LOGIN_ONLY, null));
        assertFalse(c.called.get(), "LOGIN_ONLY 不应调用其它 checker");
    }

    @Test
    @DisplayName("PASSWORD: 委派给对应 checker 并传参")
    void password_delegatesToChecker() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.PASSWORD, "secret"));
        assertTrue(c.called.get());
    }

    @Test
    @DisplayName("PASSWORD: checker 抛异常时向上传播")
    void password_checkerFailure_propagates() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, true);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertThrows(ServiceException.class, () -> v.verify(UID, SignAuthMethod.PASSWORD, "wrong"));
    }

    @Test
    @DisplayName("未支持的认证方式: 抛 ServiceException")
    void unsupportedMethod_throws() {
        SignAuthVerifier v = new SignAuthVerifier(List.of());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> v.verify(UID, SignAuthMethod.FACE, "x"));
        assertTrue(ex.getMessage().contains("不支持"));
    }

    @Test
    @DisplayName("缺凭证: 抛 ServiceException（不允许降级）")
    void missingCredential_throws() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertThrows(ServiceException.class, () -> v.verify(UID, SignAuthMethod.PASSWORD, ""));
        assertFalse(c.called.get());
    }

    @Test
    @DisplayName("认证方式为 null: 抛 ServiceException")
    void nullMethod_throws() {
        SignAuthVerifier v = new SignAuthVerifier(List.of());
        assertThrows(ServiceException.class, () -> v.verify(UID, null, "x"));
    }

    @Test
    @DisplayName("多个 checker: 按 authMethod 精确匹配")
    void multipleCheckers_exactMatch() {
        FakeChecker pwd = new FakeChecker(SignAuthMethod.PASSWORD, false);
        FakeChecker sms = new FakeChecker(SignAuthMethod.SMS, true);
        SignAuthVerifier v = new SignAuthVerifier(List.of(pwd, sms));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.PASSWORD, "ok"));
        assertTrue(pwd.called.get());
        assertFalse(sms.called.get());
    }
}
