package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.Param;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;

/**
 * 库存清单Mapper接口
 *
 * @author ruoyi
 * @date 2025-12-03
 */
public interface WmMaterialStockMapper extends BaseMapperPlus<WmMaterialStock, WmMaterialStockVo> {

    /**
     * 根据分类ID列表查询库存清单
     *
     * @param categoryIds 分类ID列表
     * @return 库存清单集合
     */
    List<WmMaterialStockVo> selectByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
}
