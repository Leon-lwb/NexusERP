package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.vo.WmLocationStockAggVo;
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
     * 按库位聚合查询指定仓库的库存汇总（供 3D 可视化库位状态计算）
     *
     * @param warehouseId 仓库ID
     * @return 库位库存聚合列表
     */
    @Select("SELECT location_id              AS locationId, " +
            "COUNT(*)                        AS stockCount, " +
            "COALESCE(SUM(stock_quantity), 0)     AS stockQuantity, " +
            "COALESCE(SUM(reserved_quantity), 0)  AS reservedQuantity, " +
            "MAX(frozen_flag)                AS frozenFlag " +
            "FROM wm_material_stock " +
            "WHERE warehouse_id = #{warehouseId} AND del_flag = '0' " +
            "GROUP BY location_id")
    List<WmLocationStockAggVo> aggregateStockByWarehouse(@Param("warehouseId") Long warehouseId);

    /**
     * 原子扣减库存（含冻结守卫）
     * <p>ERP-WM-09：守卫下沉到原子 SQL 加 {@code frozen_flag = 0}，消除「读库存行 → IQC 冻结 → 扣减」
     * 窗口内冻结被绕过（不合格品被领用）的 TOCTOU 缺陷。
     * 盘点盘亏需调整冻结行时改用 {@link #deductStockIgnoreFrozen}。</p>
     *
     * @param stockId   库存ID
     * @param deductQty 扣减数量
     * @param version   乐观锁版本号
     * @return 影响行数，0 表示库存不足 / 已冻结 / 版本冲突
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{deductQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} AND stock_quantity >= #{deductQty} " +
            "AND frozen_flag = 0 AND version = #{version}")
    int deductStock(@Param("stockId") Long stockId,
                    @Param("deductQty") BigDecimal deductQty,
                    @Param("version") Integer version);

    /**
     * 原子扣减库存（可用量守卫，ERP-WM-02）
     * <p>与 {@link #deductStock} 的区别：扣减上限按「可用量 = stock_quantity - reserved_quantity」判定。
     * 供生产领料 / 杂项出库 / 库位转储 / 委外发料等「不得扣穿他人预留」的出库路径使用；
     * 同时保留冻结守卫，排除已删行和未知预留量，防止读取后删除竞态或未知量兜底。</p>
     *
     * @param stockId   库存ID
     * @param deductQty 扣减数量
     * @param version   乐观锁版本号
     * @return 影响行数，0 表示可用量不足 / 已冻结 / 版本冲突
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{deductQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} " +
            "AND (stock_quantity - IFNULL(reserved_quantity, 0)) >= #{deductQty} " +
            "AND del_flag = '0' AND reserved_quantity IS NOT NULL " +
            "AND frozen_flag = 0 AND version = #{version}")
    int deductStockAvailable(@Param("stockId") Long stockId,
                             @Param("deductQty") BigDecimal deductQty,
                             @Param("version") Integer version);

    /**
     * 原子扣减库存（不带冻结守卫，仅供盘点盘亏使用）
     * <p>盘点必须能把冻结行调整到实盘值（冻结是质量状态、不是库存维度），
     * 因此该入口只保留「不超扣」与乐观锁守卫，调用方须自行确认业务语义。</p>
     *
     * @param stockId   库存ID
     * @param deductQty 扣减数量
     * @param version   乐观锁版本号
     * @return 影响行数
     */
    @Update("UPDATE wm_material_stock SET stock_quantity = stock_quantity - #{deductQty}, " +
            "version = version + 1, update_time = NOW() " +
            "WHERE stock_id = #{stockId} AND stock_quantity >= #{deductQty} AND version = #{version}")
    int deductStockIgnoreFrozen(@Param("stockId") Long stockId,
                                @Param("deductQty") BigDecimal deductQty,
                                @Param("version") Integer version);

    /**
     * 出库成本结转（原子相对扣减，ERP-WM-01）
     * <p>原实现先 {@code selectById} 再用「扣减后余量」校验 {@code outQty > qty}，导致单批次扣减超过
     * 扣减后余量（整批出库 100% 命中）即抛「出库数量超过库存数量」并整单回滚。
     * 这里改为原子相对扣减，数量充足性完全由 {@code deductStock*} 的守卫保证，不再重复校验。</p>
     *
     * @param stockId 库存ID
     * @param outQty  出库数量
     * @return 影响行数
     */
    @Update("UPDATE wm_material_stock SET total_cost = GREATEST(IFNULL(total_cost, 0) - IFNULL(unit_cost, 0) * #{outQty}, 0), " +
            "update_time = NOW() " +
            "WHERE stock_id = #{stockId}")
    int issueStockCost(@Param("stockId") Long stockId, @Param("outQty") BigDecimal outQty);

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
            "AND frozen_flag = 0 " +
            "AND version = #{version}")
    int deductStockWithReserved(@Param("stockId") Long stockId,
                                @Param("outQty") BigDecimal outQty,
                                @Param("consumeReservedQty") BigDecimal consumeReservedQty,
                                @Param("version") Integer version);
}
