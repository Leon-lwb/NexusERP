package com.nexus.im.support;

import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-1: 测试基础设施冒烟测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>测试依赖（spring-boot-starter-test）能被识别</li>
 *   <li>JUnit 5 + AssertJ 可用</li>
 *   <li>{@link ImUnitTestBase} 的静态 mock 机制工作正常</li>
 *   <li>{@link ImTestFixture} 数据构造方法可用</li>
 *   <li>surefire 配置覆盖后，无 @Tag 的测试能在默认 mvn test 中执行</li>
 * </ul>
 */
class ImTestSmokeTest extends ImUnitTestBase {

    @Test
    void smoke_junitAndAssertj_works() {
        assertThat(1 + 1).isEqualTo(2);
    }

    @Test
    void smoke_loginHelper_mocked() {
        // 验证 BaseTest 的静态 mock 生效
        assertThat(LoginHelper.getUserId()).isEqualTo(ME_USER_ID);
        assertThat(LoginHelper.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void smoke_loginAs_switchesUser() {
        loginAs(PEER_USER_ID);
        assertThat(LoginHelper.getUserId()).isEqualTo(PEER_USER_ID);
    }

    @Test
    void smoke_tenantHelper_mocked() {
        assertThat(TenantHelper.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(TenantHelper.isEnable()).isFalse();
    }

    @Test
    void smoke_fixture_buildMessage() {
        var msg = ImTestFixture.buildMessage(1L, ME_USER_ID, PEER_USER_ID, null, "hello");
        assertThat(msg.getMessageId()).isEqualTo(1L);
        assertThat(msg.getFromUserId()).isEqualTo(ME_USER_ID);
        assertThat(msg.getToUserId()).isEqualTo(PEER_USER_ID);
        assertThat(msg.getGroupId()).isNull();
        assertThat(msg.getContent()).isEqualTo("hello");
        assertThat(msg.getMsgType()).isEqualTo("text");
        assertThat(msg.getIsRead()).isEqualTo("0");
        assertThat(msg.getRevoked()).isEqualTo("0");
    }

    @Test
    void smoke_fixture_snowflakeId_exceedsJsSafeInteger() {
        Long id = ImTestFixture.snowflakeId(123L);
        // 雪花 ID 应超出 JS Number.MAX_SAFE_INTEGER (2^53 - 1 = 9007199254740991)
        assertThat(id).isGreaterThan(9_007_199_254_740_991L);
        assertThat(id).isEqualTo(1_900_000_000_000_000_123L);
    }
}
