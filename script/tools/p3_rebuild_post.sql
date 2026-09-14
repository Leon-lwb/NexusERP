-- =============================================================================
--  ERP 重建 · 后置修复脚本
--  生成日期：2026-09-14
--  用途：在 rebuild.sql 之后执行，完成
--        ① 工人档案字段规范化与补全
--        ② 补建缺失的 PRICE_LIST_CODE 编码规则
--  说明：本脚本幂等，可重复执行。
-- =============================================================================

-- =============================================================================
--  ① 工人档案规范化与补全（37 人）
-- -----------------------------------------------------------------------------
--  背景：历史 sch_worker 数据存在字段取值不统一
--        skill_level ：1/2/3/4 与 SENIOR/INTERMEDIATE/JUNIOR 混用，另有 NULL
--        work_category：英文（WELDER/FITTER/QC…）与 NULL 混用
--        work_status  ：未设置
-- =============================================================================

-- 1.1 统一技能等级取值
UPDATE `ry-erp`.sch_worker SET skill_level = CASE skill_level
        WHEN '1' THEN 'JUNIOR'   WHEN '2' THEN 'INTERMEDIATE'
        WHEN '3' THEN 'SENIOR'   WHEN '4' THEN 'EXPERT'
        ELSE skill_level END
 WHERE skill_level IN ('1','2','3','4');
UPDATE `ry-erp`.sch_worker SET skill_level = 'INTERMEDIATE'
 WHERE skill_level IS NULL OR skill_level = '';

-- 1.2 统一工种为中文
UPDATE `ry-erp`.sch_worker SET work_category = CASE UPPER(work_category)
        WHEN 'WELDER'    THEN '焊工'     WHEN 'FITTER'   THEN '装配钳工'
        WHEN 'PACKER'    THEN '包装工'   WHEN 'QC'       THEN '质检员'
        WHEN 'WELDING'   THEN '焊接工'   WHEN 'ASSEMBLY' THEN '装配工'
        WHEN 'MACHINING' THEN '机加工'   WHEN 'MOLDING'  THEN '注塑工'
        WHEN 'PACKAGING' THEN '包装工'   WHEN 'PUNCHING' THEN '冲压工'
        WHEN 'SPRAYING'  THEN '喷涂工'   WHEN 'CUTTING'  THEN '下料工'
        WHEN 'DEBUG'     THEN '调试工'
        ELSE work_category END
 WHERE work_category IS NOT NULL AND work_category <> '';

-- 1.3 按技能等级设定差异化工时单价（元/小时）
UPDATE `ry-erp`.sch_worker SET labor_rate = CASE skill_level
        WHEN 'EXPERT'       THEN 78.00
        WHEN 'SENIOR'       THEN 62.00
        WHEN 'INTERMEDIATE' THEN 48.00
        WHEN 'JUNIOR'       THEN 36.00
        ELSE 42.00 END;

-- 1.4 补全基础信息
--     ⚠️ worker_id 含雪花 ID，直接参与 DATE_SUB 会 datetime overflow，必须取模
UPDATE `ry-erp`.sch_worker
   SET entry_date = DATE_SUB(NOW(), INTERVAL (100 + (worker_id % 900)) DAY)
 WHERE entry_date IS NULL;

UPDATE `ry-erp`.sch_worker
   SET regularization_date = DATE_ADD(entry_date, INTERVAL 90 DAY)
 WHERE regularization_date IS NULL AND entry_date IS NOT NULL;

UPDATE `ry-erp`.sch_worker
   SET birth_date = DATE_SUB(NOW(), INTERVAL (22 + (worker_id % 25)) YEAR)
 WHERE birth_date IS NULL;

UPDATE `ry-erp`.sch_worker
   SET phonenumber = CONCAT('138', LPAD(worker_id % 100000000, 8, '0'))
 WHERE phonenumber IS NULL;

UPDATE `ry-erp`.sch_worker
   SET emergency_phone = CONCAT('139', LPAD(worker_id % 100000000, 8, '0'))
 WHERE emergency_phone IS NULL;

UPDATE `ry-erp`.sch_worker
   SET emergency_contact = CONCAT('家属', LPAD(worker_id % 100, 2, '0'))
 WHERE emergency_contact IS NULL OR emergency_contact = '待补充';

UPDATE `ry-erp`.sch_worker SET work_status = 'ON_DUTY'
 WHERE work_status IS NULL OR work_status NOT IN ('ON_DUTY','OFF_DUTY','LEAVE');

UPDATE `ry-erp`.sch_worker SET sex = CASE WHEN worker_id % 2 = 0 THEN '1' ELSE '0' END
 WHERE sex IS NULL;

-- 1.5 校验：37 人应全部齐备
SELECT COUNT(*) AS 总数,
       SUM(workshop_id  IS NOT NULL) AS 已挂车间,
       SUM(worker_code  IS NOT NULL) AS 有工号,
       SUM(labor_rate  > 0)          AS 有工时单价,
       SUM(entry_date   IS NOT NULL) AS 有入职日期,
       SUM(birth_date   IS NOT NULL) AS 有出生日期,
       SUM(phonenumber  IS NOT NULL) AS 有电话,
       SUM(emergency_contact IS NOT NULL) AS 有紧急联系人,
       SUM(work_status  = 'ON_DUTY') AS 在岗
FROM `ry-erp`.sch_worker;


-- =============================================================================
--  ② 补建缺失的 PRICE_LIST_CODE 编码规则
-- -----------------------------------------------------------------------------
--  背景：SoPriceListServiceImpl.java:96 调用 genSerialCode("PRICE_LIST_CODE","")，
--        但 sys_auto_code_rule 中不存在该规则编码 → 新建价目表时抛
--        「未获取到指定类型:[PRICE_LIST_CODE]的业务编码生成规则」。
--        属"代码引用了未配置的规则"，此处以补规则的方式修复（不改代码）。
-- =============================================================================
DELETE FROM `ry-vue`.sys_auto_code_part WHERE rule_id = 302;
DELETE FROM `ry-vue`.sys_auto_code_rule WHERE rule_id = 302;

INSERT INTO `ry-vue`.sys_auto_code_rule
  (rule_id, tenant_id, rule_code, rule_name, rule_desc, max_length, is_padded, padded_char,
   padded_method, enable_flag, remark, create_by, create_dept, create_time, del_flag)
VALUES
  (302,'000000','PRICE_LIST_CODE','价目表编码','销售价目表头编码 PL+yyyyMMdd+3位流水',
   14,'N','0','L','Y','2026-09-14 补建：代码引用但库中缺失',1,103,NOW(),'0');

INSERT INTO `ry-vue`.sys_auto_code_part
  (part_id, tenant_id, rule_id, part_index, part_type, part_code, part_name, part_length,
   fix_character, seria_start_no, seria_step, cycle_flag, cycle_method,
   create_by, create_dept, create_time, del_flag)
VALUES
  (30201,'000000',302,1,'FIXCHAR','PREFIX','前缀',2,'PL',NULL,NULL,NULL,NULL,1,103,NOW(),'0'),
  (30202,'000000',302,2,'NOWDATE','DATE','日期',8,NULL,NULL,NULL,NULL,NULL,1,103,NOW(),'0'),
  (30203,'000000',302,3,'SERIALNO','SERIAL','流水号',3,NULL,1,1,'Y','DAY',1,103,NOW(),'0');

-- 计数器置 1（已存在 PL<今日>001），下次生成 002，不撞号
DELETE FROM `ry-vue`.sys_auto_code_result WHERE rule_id = 302;
INSERT INTO `ry-vue`.sys_auto_code_result
  (code_id, tenant_id, rule_id, gen_date, gen_index, last_result, last_serial_no,
   last_input_char, remark, attr1, attr2, create_dept, create_by, create_time,
   update_by, update_time, del_flag)
VALUES
  (9000000099,'000000',302,CONCAT(DATE_FORMAT(NOW(),'%Y%m%d'),'000000'),1,
   CONCAT('PL',DATE_FORMAT(NOW(),'%Y%m%d'),'001'),1,NULL,'',0,0,103,1,NOW(),1,NOW(),'0');


-- =============================================================================
--  ③ 编码规则健全性终检（应返回空集）
-- =============================================================================
-- 3.1 无任何组成片段的规则（生成时必抛「生成的编码为空！」）
SELECT '无组成片段的规则' AS 检查项, r.rule_code, r.rule_name
FROM `ry-vue`.sys_auto_code_rule r
LEFT JOIN `ry-vue`.sys_auto_code_part p ON p.rule_id = r.rule_id AND p.del_flag = '0'
WHERE r.del_flag = '0'
GROUP BY r.rule_id, r.rule_code, r.rule_name
HAVING COUNT(p.part_id) = 0;

-- 3.2 含多个 SERIALNO 片段的规则（引擎断言失败）
SELECT '多流水号片段' AS 检查项, r.rule_code, COUNT(*) AS cnt
FROM `ry-vue`.sys_auto_code_rule r
JOIN `ry-vue`.sys_auto_code_part p ON p.rule_id = r.rule_id AND p.del_flag = '0'
WHERE p.part_type = 'SERIALNO'
GROUP BY r.rule_code HAVING COUNT(*) > 1;

-- 3.3 计数器与实际最大编码的一致性（永不重置的规则）
SELECT 'VENDOR_CODE'    AS 规则, (SELECT last_serial_no FROM `ry-vue`.sys_auto_code_result WHERE rule_id=208) AS 计数器,
       (SELECT COUNT(*) FROM `ry-erp`.bd_vendor) AS 实际行数
UNION ALL SELECT 'CLIENT_CODE',
       (SELECT last_serial_no FROM `ry-vue`.sys_auto_code_result WHERE rule_id=207),
       (SELECT COUNT(*) FROM `ry-erp`.bd_client)
UNION ALL SELECT 'WORKSHOP_CODE',
       (SELECT last_serial_no FROM `ry-vue`.sys_auto_code_result WHERE rule_id=209),
       (SELECT COUNT(*) FROM `ry-erp`.bd_workshop)
UNION ALL SELECT 'WAREHOUSE_CODE',
       (SELECT last_serial_no FROM `ry-vue`.sys_auto_code_result WHERE rule_id=210),
       (SELECT COUNT(*) FROM `ry-erp`.wm_warehouse WHERE warehouse_id <> 9900000001)
UNION ALL SELECT 'WORKSTATION_CODE',
       (SELECT last_serial_no FROM `ry-vue`.sys_auto_code_result WHERE rule_id=216),
       (SELECT COUNT(*) FROM `ry-erp`.bd_workstation);
