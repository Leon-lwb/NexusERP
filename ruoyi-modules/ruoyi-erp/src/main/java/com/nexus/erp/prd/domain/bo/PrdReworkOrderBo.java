package com.nexus.erp.prd.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdReworkOrder;

import java.math.BigDecimal;

/**
 * 返工订单业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdReworkOrder.class, reverseConvertGenerate = false)
public class PrdReworkOrderBo extends BaseEntity {

    /** 返工单ID */
    private Long reworkId;
    /** 返工单号 */
    private String reworkNo;
    /** 来源工单ID */
    private Long sourceWoId;
    /** 来源工单编号 */
    private String sourceWoNo;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位名称 */
    private String baseUomName;
    /** 返工数量 */
    private BigDecimal reworkQty;
    /** 返工原因 */
    private String reworkReason;
    /** 实际完成数量 */
    private BigDecimal actualQty;
    /** 报废数量 */
    private BigDecimal scrapQty;
    /** 返工状态 */
    private String status;
    /** 备注 */
    private String remark;
    /** ID数组（用于批量操作） */
    private Long[] ids;
}
