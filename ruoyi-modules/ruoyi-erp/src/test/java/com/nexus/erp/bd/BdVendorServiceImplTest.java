package com.nexus.erp.bd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.domain.bo.BdVendorBo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;
import com.nexus.erp.bd.mapper.BdVendorMapper;
import com.nexus.erp.bd.service.impl.BdVendorServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 供应商列表查询条件单元测试。
 * <p>
 * 回归点：前端「合作状态」下拉传入的 {@code status} 曾被 {@code buildQueryWrapper} 忽略，
 * 导致按合作状态筛选完全无效（供应商管理页）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class BdVendorServiceImplTest {

    @Mock
    private BdVendorMapper baseMapper;

    @InjectMocks
    private BdVendorServiceImpl vendorService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析 BdVendor::getStatus 依赖 TableInfo 的 lambda 缓存，
        // 未初始化会抛 "未找到当前实体的表达式缓存"
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdVendor.class);
    }

    /** 传了 status 就必须出现在 SQL 条件与参数里 */
    @Test
    void selectPageList_filtersByStatus() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        BdVendorBo bo = new BdVendorBo();
        bo.setStatus("ACTIVE");

        vendorService.selectPageList(bo, new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertTrue(lqw.getTargetSql().contains("status"), "SQL 条件应包含 status，实际: " + lqw.getTargetSql());
        assertTrue(lqw.getParamNameValuePairs().containsValue("ACTIVE"),
            "查询参数应包含 ACTIVE，实际: " + lqw.getParamNameValuePairs());
    }

    /** status 为空时不得产生 status 条件（否则会筛出空结果） */
    @Test
    void selectPageList_omitsStatusWhenBlank() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        vendorService.selectPageList(new BdVendorBo(), new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertFalse(lqw.getTargetSql().contains("status"), "未传 status 时不应有 status 条件，实际: " + lqw.getTargetSql());
    }

    /** 供货品类 / 地区筛选（既有行为，防回归） */
    @Test
    void selectPageList_filtersByIndustryAndRegion() {
        when(baseMapper.selectVoPage(any(), any())).thenReturn(new Page<BdVendorVo>());

        BdVendorBo bo = new BdVendorBo();
        bo.setIndustry("METAL");
        bo.setRegion("2");

        vendorService.selectPageList(bo, new PageQuery(10, 1));

        LambdaQueryWrapper<BdVendor> lqw = capturedWrapper();
        assertTrue(lqw.getParamNameValuePairs().containsValue("METAL"),
            "查询参数应包含 METAL，实际: " + lqw.getParamNameValuePairs());
        assertTrue(lqw.getParamNameValuePairs().containsValue("2"),
            "查询参数应包含 2，实际: " + lqw.getParamNameValuePairs());
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<BdVendor> capturedWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<BdVendor>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(baseMapper).selectVoPage(any(), captor.capture());
        LambdaQueryWrapper<BdVendor> wrapper = captor.getValue();
        // 关键：条件里的值是以 ISqlSegment 惰性回调形式挂上去的，paramNameValuePairs 只有
        // 在渲染 SQL 片段时才会被填充。不先渲染一次就直接读 getParamNameValuePairs()，
        // 会恒得到空 map，把「条件已生效」误判成「条件未生效」。
        wrapper.getTargetSql();
        return wrapper;
    }
}
