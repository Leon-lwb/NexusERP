package com.nexus.erp.fin.fa;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.domain.FaDepreciation;
import com.nexus.erp.fin.fa.mapper.FaAssetCategoryMapper;
import com.nexus.erp.fin.fa.mapper.FaAssetMapper;
import com.nexus.erp.fin.fa.mapper.FaDepreciationMapper;
import com.nexus.erp.fin.fa.service.impl.FaDepreciationServiceImpl;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.mapper.GlPeriodMapper;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 固定资产折旧回写单元测试 —— 固化 FIN-FA-01 修复口径。
 *
 * <p><b>背景</b>：原实现在同一条 UPDATE 的 SET 中先 {@code cumulative_depreciation = cumulative_depreciation + X}
 * 再 {@code net_book_value = original_value - cumulative_depreciation - X}。MySQL 单表 UPDATE 的 SET
 * 从左到右求值，第二个表达式读到的是刚写入的新值，于是净值实际为
 * {@code original - cum_old - 2X} —— 每计提一期多扣一个 X，误差逐月累积且不会自我暴露。</p>
 *
 * <p>本类做数值级断言：原值 10000、已提 1000、本期直线 1000 时，累计折旧必须为 2000.00、
 * 净值必须为 8000.00（而不是 7000.00）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class FaDepreciationServiceImplTest {

    @Mock
    private FaDepreciationMapper baseMapper;
    @Mock
    private FaAssetMapper assetMapper;
    @Mock
    private FaAssetCategoryMapper categoryMapper;
    @Mock
    private GlPeriodMapper periodMapper;
    @Mock
    private GlAccountMapper accountMapper;
    @Mock
    private IGlVoucherService voucherService;

    @InjectMocks
    private FaDepreciationServiceImpl depreciationService;

    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        // IdGeneratorUtil 的静态字段在类初始化时取 SpringUtils.getBean(IdentifierGenerator.class)
        // （该方法继承自 hutool SpringUtil，故必须 mock SpringUtil），单测无 Spring 上下文
        springUtilMock = mockStatic(SpringUtil.class);
        IdentifierGenerator idGenerator = mock(IdentifierGenerator.class);
        lenient().when(idGenerator.nextId(any())).thenReturn(12345L);
        lenient().when(SpringUtil.getBean(IdentifierGenerator.class)).thenReturn(idGenerator);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, FaAsset.class);
        TableInfoHelper.initTableInfo(assistant, FaDepreciation.class);
        TableInfoHelper.initTableInfo(assistant, FaAssetCategory.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    @Test
    void runDepreciation_netBookValueIsOriginalMinusNewCumulative() {
        Date end = new Date();
        LocalDate endLocal = end.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate inUseLocal = endLocal.minusMonths(3);

        GlPeriod period = new GlPeriod();
        period.setPeriodId(9L);
        period.setPeriodName("2026-01");
        period.setStatus(GlConstants.PERIOD_OPEN);
        period.setEndDate(end);
        when(periodMapper.selectById(9L)).thenReturn(period);

        FaAsset asset = new FaAsset();
        asset.setAssetId(1L);
        asset.setAssetNo("FA001");
        asset.setCategoryId(2L);
        asset.setOriginalValue(new BigDecimal("10000.00"));
        asset.setSalvageValue(BigDecimal.ZERO);
        asset.setCumulativeDepreciation(new BigDecimal("1000.00"));
        asset.setNetBookValue(new BigDecimal("9000.00"));
        asset.setUsefulLife(10);
        asset.setDepreciationMethod(FaConstants.METHOD_STRAIGHT_LINE);
        asset.setInUseDate(Date.from(inUseLocal.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        asset.setStatus(FaConstants.ASSET_STATUS_IN_USE);
        asset.setVersion(5);

        FaAssetCategory category = new FaAssetCategory();
        category.setCategoryId(2L);
        category.setDepreciationMethod(FaConstants.METHOD_STRAIGHT_LINE);
        category.setUsefulLife(10);
        category.setDebitAccountId(3L);

        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset));
        GlAccount account = new GlAccount();
        account.setAccountId(3L);
        when(accountMapper.selectOne(any())).thenReturn(account);
        GlVoucherVo voucher = new GlVoucherVo();
        voucher.setVoucherId(77L);
        voucher.setVoucherNo("J-77");
        when(voucherService.createAndPost(any())).thenReturn(voucher);
        when(baseMapper.insert(any(FaDepreciation.class))).thenReturn(1);
        when(assetMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, depreciationService.runDepreciation(9L));

        // 折旧记录:本期 1000.00(直线法 10000/10),累计 1000+1000 = 2000.00
        ArgumentCaptor<FaDepreciation> planCaptor = ArgumentCaptor.forClass(FaDepreciation.class);
        verify(baseMapper).insert(planCaptor.capture());
        assertEquals(0, new BigDecimal("1000.00").compareTo(planCaptor.getValue().getDepreciationAmount()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(planCaptor.getValue().getCumulativeDepreciation()));

        // 资产回写:累计 2000.00、净值 8000.00;且 SQL 不得引用 original_value(旧缺陷的跨列求值)
        ArgumentCaptor<Wrapper<FaAsset>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(assetMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<FaAsset> wrapper = (LambdaUpdateWrapper<FaAsset>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertFalse(sqlSet.contains("original_value"),
            "净值必须是服务端算好的绝对值,不能在同一条 SET 中引用 original_value/cumulative_depreciation: " + sqlSet);
        assertTrue(hasAmount(wrapper, "2000.00"),
            "累计折旧应为 2000.00, 实际参数: " + wrapper.getParamNameValuePairs());
        assertTrue(hasAmount(wrapper, "8000.00"),
            "净值应为 8000.00(修复前为 7000.00,每月多扣一个 X), 实际参数: " + wrapper.getParamNameValuePairs());
    }

    /** 断言包装器里存在指定数值参数（统一按数值比较，规避取值形态差异） */
    private static boolean hasAmount(Wrapper<?> wrapper, String expected) {
        BigDecimal target = new BigDecimal(expected);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values().stream()
            .anyMatch(v -> toDecimal(v) != null && toDecimal(v).compareTo(target) == 0);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString());
        }
        if (v instanceof CharSequence) {
            try {
                return new BigDecimal(v.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
