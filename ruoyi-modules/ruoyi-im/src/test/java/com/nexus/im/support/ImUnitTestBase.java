package com.nexus.im.support;

import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * IM 单元测试基类。
 * <p>
 * 统一管理 {@link LoginHelper} / {@link TenantHelper} 静态方法的 mock 生命周期，
 * 子类通过 {@link #loginAs(Long)} 切换当前登录用户视角，覆盖"归属用户/非归属用户"等安全场景。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>Mockito 5.x 内置 inline mock maker，无需 mockito-inline 依赖即可 mockStatic</li>
 *   <li>每个测试方法独立的 MockedStatic（@BeforeEach/@AfterEach 配对），避免线程间污染</li>
 *   <li>默认登录 {@link #ME_USER_ID}，租户 {@link #TENANT_ID}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
public abstract class ImUnitTestBase {

    protected static final String TENANT_ID = "1";
    protected static final Long ME_USER_ID = 100L;
    protected static final Long PEER_USER_ID = 200L;
    protected static final Long OTHER_TENANT_USER_ID = 300L;
    protected static final String OTHER_TENANT_ID = "2";

    protected MockedStatic<TenantHelper> tenantHelperMock;
    protected MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUpBase() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(ME_USER_ID);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn(TENANT_ID);

        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT_ID);
        // 默认不启用多租户（子类按需覆盖）
        tenantHelperMock.when(TenantHelper::isEnable).thenReturn(false);
    }

    @AfterEach
    void tearDownBase() {
        // MockedStatic 必须在 afterEach 关闭，否则会泄漏到其他测试
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
        if (tenantHelperMock != null) {
            tenantHelperMock.close();
        }
    }

    /**
     * 切换当前登录用户（用于模拟"我是消息归属人"vs"我不是"等场景）。
     */
    protected void loginAs(Long userId) {
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(userId);
    }

    /**
     * 启用多租户模式（用于测试跨租户越权场景）。
     */
    protected void enableTenant() {
        tenantHelperMock.when(TenantHelper::isEnable).thenReturn(true);
    }
}
