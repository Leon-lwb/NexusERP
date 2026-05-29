package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.math.BigDecimal;
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

    /**
     * 原子扣减库存
     *
     * @param stockId   库存ID
     * @param deductQty 扣减数量
     * @param version   乐观锁版本号
     * @return 影响行数
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{deductQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} AND stock_quantity >= #{deductQty} AND version = #{version}")
    int deductStock(@Param("stockId") Long stockId,
                    @Param("deductQty") BigDecimal deductQty,
                    @Param("version") Integer version);
}
