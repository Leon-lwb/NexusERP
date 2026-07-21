package com.nexus.erp.wm.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.wm.domain.WmStockReconciliation;

import java.math.BigDecimal;

/**
 * 库存台账校验业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockReconciliation.class, reverseConvertGenerate = false)
public class WmStockReconciliationBo extends BaseEntity {

    /**
     * 校验ID
     */
    private Long reconId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 单位名称
     */
    private String baseUomName;

    /**
     * 账面库存
     */
    private BigDecimal bookQty;

    /**
     * 事务汇总数量
     */
    private BigDecimal txBalance;

    /**
     * 差异数量
     */
    private BigDecimal diffQty;

    /**
     * 校验状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}
