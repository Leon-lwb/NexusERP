package com.nexus.mes.pda.domain.vo;

import com.nexus.mes.pda.util.AqlSampler;
import lombok.Data;

/**
 * PDA AQL 试算结果 (M3 批次 5)
 * <p>
 * 试算为决策辅助: 展示 GB/T 2828.1 正常检验一次抽样方案与判定建议,
 * 最终判定以检验员提交的 inspectResult 为准。
 */
@Data
public class PdaAqlPlanVo {

    /** 批量 */
    private Long lotSize;
    /** 检验水平 I/II/III */
    private String level;
    /** AQL */
    private Double aql;
    /** 样本量字码 A~R */
    private String codeLetter;
    /** 样本量(≥批量时为批量, 整批检查) */
    private Integer sampleSize;
    /** 接收数 Ac */
    private Integer acceptQty;
    /** 拒收数 Re */
    private Integer rejectQty;
    /** 是否整批检查 */
    private Boolean wholeLot;
    /** 检验水平/批量对应的整批检查提示(未截断时的标准样本量) */
    private Integer standardSampleSize;
    /** 缺陷数(未传入则为空) */
    private Long defectQty;
    /** 判定建议 QUALIFIED/UNQUALIFIED(未传 defectQty 则为空) */
    private String suggestResult;

    public static PdaAqlPlanVo of(long lotSize, String level, double aql, AqlSampler.AqlPlan plan, Long defectQty) {
        PdaAqlPlanVo vo = new PdaAqlPlanVo();
        vo.setLotSize(lotSize);
        vo.setLevel(level);
        vo.setAql(aql);
        vo.setCodeLetter(String.valueOf(plan.getCodeLetter()));
        vo.setSampleSize(plan.getSampleSize());
        vo.setAcceptQty(plan.getAcceptQty());
        vo.setRejectQty(plan.getRejectQty());
        vo.setWholeLot(plan.isWholeLot());
        vo.setStandardSampleSize(plan.getStandardSampleSize());
        if (defectQty != null) {
            vo.setDefectQty(defectQty);
            vo.setSuggestResult(defectQty <= plan.getAcceptQty() ? "QUALIFIED" : "UNQUALIFIED");
        }
        return vo;
    }
}
