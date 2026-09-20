package com.nexus.erp.pu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.impl.PuPurchaseRequisitionServiceImpl;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 低库存自动生成采购申请单元测试（ERP-BD-02：物料状态必须按 tinyint 数值比较）。
 * <p>回归点：此前用 {@code .eq(BdMaterial::getStatus, "ACTIVE")}，MySQL 把 'ACTIVE' 隐式转为 0，
 * 只会命中「停用」物料、排除全部启用物料。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseRequisitionServiceImplTest {

    @Mock
    private PuPurchaseRequisitionMapper baseMapper;
    @Mock
    private PuPurchaseRequisitionLineMapper lineMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private PuPurchaseRequisitionServiceImpl requisitionService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdMaterial.class);
    }

    /** 物料状态条件必须是数值 1（启用），且不得再出现字符串 'ACTIVE' */
    @Test
    @SuppressWarnings("unchecked")
    void generateFromLowStock_materialStatusComparedNumerically() {
        when(bdMaterialMapper.selectList(any())).thenReturn(List.of());

        requisitionService.generateFromLowStock();

        ArgumentCaptor<LambdaQueryWrapper<BdMaterial>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bdMaterialMapper).selectList(captor.capture());
        LambdaQueryWrapper<BdMaterial> wrapper = captor.getValue();
        // MyBatis-Plus 的 eq/gt 条件值只有在生成 SQL 片段（getSqlSegment）时才物化进
        // paramNameValuePairs；直接读会得到空 Map，必须先物化再断言
        wrapper.getSqlSegment();
        assertNotNull(wrapper.getParamNameValuePairs());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L),
            "状态条件应为数值 1L（启用），实际参数=" + wrapper.getParamNameValuePairs());
        assertFalse(wrapper.getParamNameValuePairs().containsValue("ACTIVE"),
            "不得再使用字符串 'ACTIVE' 与 tinyint 比较");
    }
}
