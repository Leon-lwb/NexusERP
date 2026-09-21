package com.nexus.erp.dashboard;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.mapper.BdClientMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.dashboard.controller.ErpDashboardController;
import com.nexus.erp.dashboard.domain.vo.ErpDashboardStatsVo;
import com.nexus.erp.dashboard.service.impl.ErpDashboardServiceImpl;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.wm.domain.WmStockAlert;
import com.nexus.erp.wm.mapper.WmStockAlertMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首页统计单元测试（ERP-DASH-01）
 * <p>修复前：端点无权限码（任意登录用户可见全租户销售额）；每次首页加载都做 3 条全表 count。
 * 断言：① stats 端点必须带 {@code erp:dashboard:stats} 权限码；② 同租户 TTL 内 3 条全表 count 只执行一次。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
@ExtendWith(MockitoExtension.class)
class ErpDashboardServiceImplTest {

    @Mock
    private SoSalesOrderHeaderMapper soHeaderMapper;
    @Mock
    private BdClientMapper clientMapper;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private WmStockAlertMapper stockAlertMapper;

    @InjectMocks
    private ErpDashboardServiceImpl service;

    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 服务内部使用 LambdaQueryWrapper（selectCount/in），MP 需实体列缓存，单测无 Spring 需手工注册
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, com.nexus.erp.so.domain.SoSalesOrderHeader.class);
        TableInfoHelper.initTableInfo(assistant, WmStockAlert.class);
    }

    @BeforeEach
    void setUp() {
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
    }

    private void stubCommon() {
        when(soHeaderMapper.selectMaps(any())).thenReturn(List.of());
        when(soHeaderMapper.selectCount(any())).thenReturn(0L);
        when(soHeaderMapper.selectList(any())).thenReturn(List.of());
        when(stockAlertMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void statsEndpoint_requiresPermissionCode() throws Exception {
        Method method = ErpDashboardController.class.getMethod("getStats");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(permission, "首页统计端点必须带 @SaCheckPermission（修复前无权限码）");
        assertEquals("erp:dashboard:stats", permission.value()[0]);
    }

    @Test
    void fullTableCounts_areCachedWithinTtlPerTenant() {
        stubCommon();
        when(clientMapper.selectCount(any())).thenReturn(5L);
        when(materialMapper.selectCount(any())).thenReturn(7L);
        when(stockAlertMapper.selectCount(any())).thenReturn(2L);

        ErpDashboardStatsVo first = service.getStats();
        ErpDashboardStatsVo second = service.getStats();

        assertEquals(5L, first.getClientCount());
        assertEquals(7L, first.getMaterialCount());
        assertEquals(2L, first.getStockAlertCount());
        assertEquals(5L, second.getClientCount());
        // 修复前每次 getStats() 都全表 count 一次 → 这里必须是 1 次
        verify(clientMapper, times(1)).selectCount(any());
        verify(materialMapper, times(1)).selectCount(any());
        verify(stockAlertMapper, times(1)).selectCount(any());
    }
}
