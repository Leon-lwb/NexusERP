-- ====================================================================
-- SPC 过程能力分析(Cp/Cpk) v1.0
-- 库: ry-mes
-- 变更: spc_control_chart 新增规格限与过程能力列
--   usl/lsl: 规格上下限(用户维护, X_BAR_R/X_BAR_S 计量型控制图适用)
--   cp/cpk/cpk_grade: analyze() 计算回写
-- ====================================================================

ALTER TABLE `spc_control_chart`
    ADD COLUMN `usl` decimal(14,4) NULL DEFAULT NULL COMMENT '规格上限(USL, 用户维护)' AFTER `lcl`,
    ADD COLUMN `lsl` decimal(14,4) NULL DEFAULT NULL COMMENT '规格下限(LSL, 用户维护)' AFTER `usl`,
    ADD COLUMN `cp` decimal(10,4) NULL DEFAULT NULL COMMENT '过程能力指数Cp(analyze回写)' AFTER `lsl`,
    ADD COLUMN `cpk` decimal(10,4) NULL DEFAULT NULL COMMENT '过程能力指数Cpk(analyze回写)' AFTER `cp`,
    ADD COLUMN `cpk_grade` varchar(8) NULL DEFAULT NULL COMMENT '能力等级: A+(>=1.67)/A(>=1.33)/B(>=1.0)/C(<1.0)' AFTER `cpk`;

-- 示例数据补规格限(装配关键尺寸 10mm ±0.05)
UPDATE `spc_control_chart` SET `usl` = 10.0500, `lsl` = 9.9500 WHERE `chart_id` = 2 AND `usl` IS NULL;

-- ==================== 校验 ====================
-- SELECT chart_id, chart_name, usl, lcl, lsl, cp, cpk, cpk_grade FROM spc_control_chart;
