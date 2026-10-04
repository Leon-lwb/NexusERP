package com.nexus.erp.bd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorBo;
import com.nexus.erp.bd.mapper.*;
import com.nexus.erp.bd.service.impl.BdMaterialVendorServiceImpl;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 条款普通编辑事实变更必须撤销认证，备注与展示优先级不得伪造新的认证。 */
@ExtendWith(MockitoExtension.class)
class BdMaterialVendorCertificationEditTest {
    @Mock BdMaterialVendorMapper mapper;
    @Mock BdMaterialMapper materialMapper;
    @Mock BdCatalogConfirmationMapper catalogMapper;
    @Mock PuRequirementErpTransactionExecutor executor;
    @InjectMocks BdMaterialVendorServiceImpl service;
    private static MockedStatic<SpringUtil> spring;
    private MockedStatic<TenantHelper> tenant;
    private MockedStatic<LoginHelper> login;

    @BeforeAll
    static void prepare() {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter() {
            @Override public <S, T> T convert(S source, Class<T> target) { return BeanUtil.copyProperties(source, target); }
        });
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BdMaterialVendor.class);
    }

    @AfterAll static void closeConverter() { spring.close(); }

    @BeforeEach
    void context() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(9401L);
        when(executor.executeRepeatableRead(eq("000000"), any()))
            .thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(1)).get());
        BdMaterialVendor existing = new BdMaterialVendor();
        existing.setRelationId(9301L); existing.setTenantId("000000"); existing.setMaterialId(9101L);
        existing.setVendorId(9201L); existing.setDelFlag("0"); existing.setTermsVersion("terms-old");
        existing.setPrice(BigDecimal.ONE); existing.setVerifiedMoqQty(new BigDecimal("2"));
        when(catalogMapper.lockRelation("000000", 9301L)).thenReturn(existing);
        when(mapper.update(any(BdMaterialVendor.class), any())).thenReturn(1);
    }

    @AfterEach void cleanup() { login.close(); tenant.close(); }

    @Test
    void changedMoqExplicitlyInvalidatesTermsVersionAndTime() {
        BdMaterialVendorBo request = bo(); request.setVerifiedMoqQty(new BigDecimal("3"));
        assertEquals(1, service.updateByBo(request));
        var sql = wrapper();
        assertTrue(sql.getSqlSet().contains("terms_version"));
        assertTrue(sql.getSqlSet().contains("terms_verified_at"));
        assertTrue(sql.getParamNameValuePairs().containsValue(null));
        assertTrue(sql.getParamNameValuePairs().containsValue("terms-old"));
        assertTrue(sql.getTargetSql().contains("tenant_id"));
    }

    @Test
    void changedPriceAlsoInvalidatesConfirmation() {
        BdMaterialVendorBo request = bo(); request.setPrice(BigDecimal.TEN);
        service.updateByBo(request);
        assertTrue(String.valueOf(wrapper().getSqlSet()).contains("terms_verified_at"));
    }

    @Test
    void harmlessRemarkChangePreservesConfirmation() {
        BdMaterialVendorBo request = bo(); request.setRemark("模拟资料的备注");
        service.updateByBo(request);
        assertFalse(String.valueOf(wrapper().getSqlSet()).contains("terms_verified_at"));
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<BdMaterialVendor> wrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<BdMaterialVendor>> captured = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(any(BdMaterialVendor.class), captured.capture());
        captured.getValue().getTargetSql(); return captured.getValue();
    }

    private BdMaterialVendorBo bo() {
        BdMaterialVendorBo request = new BdMaterialVendorBo();
        request.setRelationId(9301L); request.setMaterialId(9101L); request.setVendorId(9201L); return request;
    }
}
