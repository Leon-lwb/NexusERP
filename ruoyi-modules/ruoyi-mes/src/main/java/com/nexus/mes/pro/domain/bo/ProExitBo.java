package com.nexus.mes.pro.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * MES工序出站业务对象(P0-3)
 * <p>承载 工序出站(exit) 的校验上下文与完工人参。</p>
 * <p>goodQty/defectQty/scrapQty 供 QUANTITY_REACHED 规则校验(三者之和须等于计划数量)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProExitBo extends BaseEntity {

    /**
     * 本次末工序事务中登记产品批次分类的稳定命令号。
     */
    private String productOutputCommandId;

    /**
     * 根据实际产品批次登记批次型产出，再执行流转卡与工单最终汇总。
     */
    private java.util.List<com.nexus.mes.trace.MesProductLotOutputWriter.OutputLine> productOutputs;

    /**
     * 流转卡ID
     */
    @NotNull(message = "流转卡不能为空")
    private Long cardId;

    /**
     * 工序记录ID
     */
    @NotNull(message = "工序记录不能为空")
    private Long stepRecordId;

    /**
     * 良品数量
     */
    @NotNull(message = "良品数量不能为空")
    private BigDecimal goodQty;

    /**
     * 不良品数量
     */
    private BigDecimal defectQty;

    /**
     * 报废数量
     */
    private BigDecimal scrapQty;
}
