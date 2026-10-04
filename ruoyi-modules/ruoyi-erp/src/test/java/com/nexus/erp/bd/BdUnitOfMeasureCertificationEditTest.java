package com.nexus.erp.bd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.bo.BdUnitOfMeasureBo;
import com.nexus.erp.bd.mapper.BdCatalogConfirmationMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.bd.service.impl.BdUnitOfMeasureServiceImpl;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 普通编辑必须保留根恒等映射；事实改变以显式置空撤销认证，而非忽略空值。 */
@ExtendWith(MockitoExtension.class)
class BdUnitOfMeasureCertificationEditTest {
    @Mock BdUnitOfMeasureMapper mapper;
    @Mock BdCatalogConfirmationMapper catalogMapper;
    @Mock PuRequirementErpTransactionExecutor executor;
    @InjectMocks BdUnitOfMeasureServiceImpl service;
    private static MockedStatic<SpringUtil> spring;
    private MockedStatic<TenantHelper> tenant;
    private MockedStatic<LoginHelper> login;
    private BdUnitOfMeasure existing;

    @BeforeAll
    static void prepare() {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter() {
            @Override public <S, T> T convert(S source, Class<T> target) { return BeanUtil.copyProperties(source, target); }
        });
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BdUnitOfMeasure.class);
    }

    @AfterAll
    static void cleanupConverter() { spring.close(); }

    @BeforeEach
    void context() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(9401L);
        lenient().when(executor.executeRepeatableRead(eq("000000"), any()))
            .thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        existing = new BdUnitOfMeasure();
        existing.setUomId(9001L); existing.setTenantId("000000"); existing.setUomName("模拟计件单位");
        existing.setIsBase(1L); existing.setPrimaryId(9001L); existing.setPrimaryName("模拟计件单位");
        existing.setConversionRate(BigDecimal.ONE); existing.setCategory("数量"); existing.setQuantityScale(0);
        existing.setStatus(1L); existing.setDelFlag("0"); existing.setConversionVersion("unit-old");
        existing.setConversionVerifiedAt(new Date());
        lenient().when(catalogMapper.lockUnit("000000", 9001L)).thenReturn(existing);
        lenient().when(mapper.update(any(BdUnitOfMeasure.class), any())).thenReturn(1);
    }

    @AfterEach
    void cleanupContext() { login.close(); tenant.close(); }

    @Test
    void rootEditKeepsIdentityMapping() {
        service.updateByBo(bo());
        ArgumentCaptor<BdUnitOfMeasure> saved = ArgumentCaptor.forClass(BdUnitOfMeasure.class);
        verify(mapper).update(saved.capture(), any());
        assertEquals(9001L, saved.getValue().getPrimaryId(), "根单位必须显式映射自身");
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getValue().getConversionRate()));
        assertFalse(String.valueOf(wrapper().getSqlSet()).contains("conversion_verified_at"));
    }

    @Test
    void factualScaleChangeExplicitlyInvalidatesVerificationUnderLock() {
        BdUnitOfMeasureBo request = bo(); request.setQuantityScale(2);
        assertEquals(1, service.updateByBo(request));
        LambdaUpdateWrapper<BdUnitOfMeasure> sql = wrapper();
        assertTrue(sql.getSqlSet().contains("conversion_verified_at"));
        assertTrue(sql.getSqlSet().contains("conversion_version"));
        assertTrue(sql.getParamNameValuePairs().containsValue(null), "认证列必须实际写 NULL");
        assertTrue(sql.getTargetSql().contains("tenant_id"));
        assertTrue(sql.getParamNameValuePairs().containsValue("unit-old"), "普通编辑也要 CAS 锁内原版本");
        verify(catalogMapper).lockUnit("000000", 9001L);
    }

    @Test
    void harmlessSortChangePreservesVerification() {
        BdUnitOfMeasureBo request = bo(); request.setSortOrder(10L);
        service.updateByBo(request);
        assertFalse(String.valueOf(wrapper().getSqlSet()).contains("conversion_verified_at"));
    }

    @Test
    void newRootCrudCreatesSelfMappingWithoutVerification() {
        BdUnitOfMeasureBo request = bo(); request.setUomId(null);
        when(mapper.insert(any(BdUnitOfMeasure.class))).thenReturn(1);
        service.insertByBo(request);
        ArgumentCaptor<BdUnitOfMeasure> saved = ArgumentCaptor.forClass(BdUnitOfMeasure.class);
        verify(mapper).insert(saved.capture());
        assertNotNull(saved.getValue().getUomId());
        assertEquals(saved.getValue().getUomId(), saved.getValue().getPrimaryId());
        assertEquals(0, saved.getValue().getConversionRate().compareTo(BigDecimal.ONE));
        assertNull(saved.getValue().getConversionVersion());
        assertNull(saved.getValue().getConversionVerifiedAt());
    }

    @Test
    void rootSelfReferenceDoesNotPreventDeletion() {
        when(mapper.selectBatchIds(any())).thenReturn(List.of(existing));
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        when(mapper.deleteBatchIds(any())).thenReturn(1);
        assertEquals(1, service.deleteByIds(new Long[]{9001L}));
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<BdUnitOfMeasure> wrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<BdUnitOfMeasure>> captured = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(any(BdUnitOfMeasure.class), captured.capture());
        captured.getValue().getTargetSql(); return captured.getValue();
    }

    private BdUnitOfMeasureBo bo() {
        BdUnitOfMeasureBo request = new BdUnitOfMeasureBo();
        request.setUomId(9001L); request.setUomName("模拟计件单位"); request.setIsBase(1L);
        return request;
    }
}
