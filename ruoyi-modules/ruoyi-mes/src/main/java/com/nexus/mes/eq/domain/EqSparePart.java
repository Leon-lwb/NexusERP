package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES备件主数据对象 eq_spare_part
 * <p>
 * current_stock 为冗余字段, 由出入库流水({@code eq_spare_stock_log})维护,
 * 出库用条件更新 current_stock &gt;= quantity 防超卖。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_part")
public class EqSparePart extends TenantEntity {

    /** 备件ID(主键) */
    @TableId(value = "spare_id")
    private Long spareId;

    /** 备件编码(唯一) */
    private String spareCode;

    /** 备件名称 */
    private String spareName;

    /** 规格型号 */
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    private String unit;

    /** 分类(字典 mes_spare_category) */
    private String category;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 当前库存(冗余, 出入库流水维护) */
    private BigDecimal currentStock;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
