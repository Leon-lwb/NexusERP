package com.nexus.erp.pu.invoice.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 三单匹配(PO + 收货 + 发票)校验结果
 *
 * @author nexus
 * @date 2026-07-16
 */
@Data
public class MatchResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否通过(无阻断性差异) */
    private boolean passed;

    /** 差异条目数 */
    private int mismatchCount;

    /** 差异明细(人读) */
    private List<String> mismatches = new ArrayList<>();

    /** 匹配维度汇总(物料/数量/单价/税率/税额/价税合计) */
    private String summary;

    /**
     * 记录一条差异并标记结果为不通过。
     *
     * @param msg 差异描述（人读）
     */
    public void addMismatch(String msg) {
        this.mismatches.add(msg);
        this.mismatchCount = this.mismatches.size();
        this.passed = false;
    }
}
