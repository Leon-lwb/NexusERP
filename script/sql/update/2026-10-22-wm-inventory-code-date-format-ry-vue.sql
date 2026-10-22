-- 系统库：补齐生产退料、上料交接单编号的日期格式。
-- 仅修复日期片段的空配置，不覆盖既有自定义格式；执行前由维护者核对基线及备份。
UPDATE sys_auto_code_part p
JOIN sys_auto_code_rule r ON r.rule_id = p.rule_id AND r.tenant_id = p.tenant_id
SET p.date_format = 'yyyyMMdd'
WHERE r.rule_code IN ('WM_RETURN_CODE', 'WM_HANDOVER_CODE')
  AND p.part_type = 'NOWDATE'
  AND (p.date_format IS NULL OR TRIM(p.date_format) = '')
  AND p.del_flag = '0' AND r.del_flag = '0';
