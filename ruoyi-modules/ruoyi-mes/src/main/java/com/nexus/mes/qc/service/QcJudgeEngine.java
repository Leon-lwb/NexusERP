package com.nexus.mes.qc.service;

import com.nexus.mes.qc.domain.QcInspectDetail;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * QC 检验判定引擎（纯函数, 可单测）
 * <p>
 * 定量: 按明细快照的标准(nominal/lsl/usl/tol)判定实测值;
 * 定性: 实测值属于合格枚举("OK"/"合格"/"PASS"/"良好")即 PASS。
 * 明细级 PASS/FAIL 汇总为单级判定: 全 PASS=QUALIFIED; 任一 CRITICAL 项
 * FAIL=UNQUALIFIED(整单拒收); 仅 MAJOR/MINOR FAIL=PARTIAL(让步/挑选)。
 * </p>
 */
public final class QcJudgeEngine {

    /** 明细判定: 通过 */
    public static final String JUDGE_PASS = "PASS";
    /** 明细判定: 不通过 */
    public static final String JUDGE_FAIL = "FAIL";
    /** 明细判定: 待判定 */
    public static final String JUDGE_PENDING = "PENDING";

    /** 单级判定: 合格 */
    public static final String RESULT_QUALIFIED = "QUALIFIED";
    /** 单级判定: 不合格(整单拒收) */
    public static final String RESULT_UNQUALIFIED = "UNQUALIFIED";
    /** 单级判定: 部分不合格(CRITICAL 全过, 仅 MAJOR/MINOR 失败) */
    public static final String RESULT_PARTIAL = "PARTIAL";

    /** 定性项目的合格枚举(大小写不敏感) */
    private static final Set<String> QUALITATIVE_PASS_WORDS = Set.of("OK", "PASS", "合格", "良好", "正常", "NG-NO", "是");

    private QcJudgeEngine() {
    }

    /**
     * 明细级判定: 按实测值与标准比较, 写回 detail.judgeResult 并返回。
     * <p>实测值缺失/无法解析按 FAIL 处理(宁严勿漏)。</p>
     */
    public static String judgeDetail(QcInspectDetail detail) {
        String result = doJudge(detail);
        detail.setJudgeResult(result);
        return result;
    }

    private static String doJudge(QcInspectDetail detail) {
        String measured = detail.getMeasuredValue();
        if (measured == null || measured.isBlank()) {
            return JUDGE_FAIL;
        }
        String dataType = detail.getDataType() == null ? "QUANTITATIVE" : detail.getDataType();
        if ("QUALITATIVE".equals(dataType)) {
            return QUALITATIVE_PASS_WORDS.contains(measured.trim().toUpperCase()) ? JUDGE_PASS : JUDGE_FAIL;
        }
        return judgeQuantitative(detail, measured);
    }

    /**
     * 定量判定: 支持多值实测(如 "3.02,2.95" 或 "3.02 2.95"), 全部落标才 PASS。
     */
    private static String judgeQuantitative(QcInspectDetail detail, String measured) {
        String standardType = detail.getStandardType();
        if (standardType == null || standardType.isBlank()) {
            // 定量但无标准快照: 无法判定, 视为 FAIL(数据配置异常)
            return JUDGE_FAIL;
        }
        for (String token : measured.split("[,;\\s]+")) {
            BigDecimal value = parse(token);
            if (value == null || !inStandard(detail, standardType, value)) {
                return JUDGE_FAIL;
            }
        }
        return JUDGE_PASS;
    }

    private static boolean inStandard(QcInspectDetail detail, String standardType, BigDecimal value) {
        switch (standardType) {
            case "LSL_USL" -> {
                return ge(value, detail.getLslValue()) && le(value, detail.getUslValue());
            }
            case "LSL_ONLY" -> {
                return ge(value, detail.getLslValue());
            }
            case "USL_ONLY" -> {
                return le(value, detail.getUslValue());
            }
            case "NOMINAL" -> {
                // MES-QC-06: null 安全 —— 名义值/上公差/下公差任一缺失即无法判定, 返回 false 走 FAIL, 不抛 NPE
                BigDecimal nominal = detail.getNominalValue();
                BigDecimal tolUpper = detail.getTolUpper();
                BigDecimal tolLower = detail.getTolLower();
                if (nominal == null || tolUpper == null || tolLower == null) {
                    return false;
                }
                return ge(value, nominal.subtract(tolLower)) && le(value, nominal.add(tolUpper));
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 单级判定: 汇总明细结果。
     * <p>仅统计已判定的明细(跳过 PENDING); 无有效明细返回 null(调用方回退人工判定)。</p>
     *
     * @return QUALIFIED / UNQUALIFIED / PARTIAL / null
     */
    public static String judgeOverall(List<QcInspectDetail> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        boolean anyFail = false;
        boolean anyCriticalFail = false;
        int judged = 0;
        for (QcInspectDetail detail : details) {
            if (!JUDGE_FAIL.equals(detail.getJudgeResult()) && !JUDGE_PASS.equals(detail.getJudgeResult())) {
                continue;
            }
            judged++;
            if (JUDGE_FAIL.equals(detail.getJudgeResult())) {
                anyFail = true;
                if ("CRITICAL".equals(detail.getCriticality())) {
                    anyCriticalFail = true;
                }
            }
        }
        if (judged == 0) {
            return null;
        }
        if (!anyFail) {
            return RESULT_QUALIFIED;
        }
        return anyCriticalFail ? RESULT_UNQUALIFIED : RESULT_PARTIAL;
    }

    private static BigDecimal parse(String token) {
        try {
            return new BigDecimal(token.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** null 安全的 >= (边界值为 null 视为不设限, 即满足) */
    private static boolean ge(BigDecimal value, BigDecimal limit) {
        return limit == null || value.compareTo(limit) >= 0;
    }

    /** null 安全的 <= (边界值为 null 视为不设限, 即满足) */
    private static boolean le(BigDecimal value, BigDecimal limit) {
        return limit == null || value.compareTo(limit) <= 0;
    }
}
