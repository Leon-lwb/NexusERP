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
 * @author nexus
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

    /**
     * 原子增加库存
     *
     * @param stockId  库存ID
     * @param addQty   增加数量
     * @param version  乐观锁版本号
     * @return 影响行数
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity + #{addQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} AND version = #{version}")
    int addStock(@Param("stockId") Long stockId,
                 @Param("addQty") BigDecimal addQty,
                 @Param("version") Integer version);

    /**
     * 原子预留库存 (乐观锁 + 可用数量校验)
     * @return 影响行数, 0 表示预留失败(库存不足或版本冲突)
     */
    @Update("UPDATE wm_material_stock SET reserved_quantity = reserved_quantity + #{reserveQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} " +
            "AND (stock_quantity - reserved_quantity) >= #{reserveQty} " +
            "AND version = #{version}")
    int reserveStock(@Param("stockId") Long stockId,
                     @Param("reserveQty") BigDecimal reserveQty,
                     @Param("version") Integer version);

    /**
     * 原子释放预留 (乐观锁, 不超过已预留数量)
     * @return 影响行数, 0 表示释放失败(版本冲突)
     */
    @Update("UPDATE wm_material_stock SET reserved_quantity = reserved_quantity - #{releaseQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} " +
            "AND reserved_quantity >= #{releaseQty} " +
            "AND version = #{version}")
    int releaseReserved(@Param("stockId") Long stockId,
                        @Param("releaseQty") BigDecimal releaseQty,
                        @Param("version") Integer version);

    /**
     * 原子出库并扣减预留 (出库时同步释放对应预留)
     * @return 影响行数, 0 表示失败
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{outQty}, " +
            "reserved_quantity = reserved_quantity - #{consumeReservedQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} " +
            "AND stock_quantity >= #{outQty} " +
            "AND reserved_quantity >= #{consumeReservedQty} " +
            "AND version = #{version}")
    int deductStockWithReserved(@Param("stockId") Long stockId,
                                @Param("outQty") BigDecimal outQty,
                                @Param("consumeReservedQty") BigDecimal consumeReservedQty,
                                @Param("version") Integer version);
}
