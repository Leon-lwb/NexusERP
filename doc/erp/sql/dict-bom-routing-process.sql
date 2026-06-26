-- ============================================================
-- BOM增强+工艺路线 字典数据初始化
-- 字典类型ID从600106开始，字典数据ID从600106开始
-- 执行前请确认无ID冲突
-- ============================================================

-- ============================================================
-- 1. 字典类型 - 工序类别
-- ============================================================
INSERT INTO `sys_dict_type` VALUES (600106, '000000', '工序类别', 'bd_process_category', 103, 1, NOW(), NULL, NULL, '工序类别: 加工/装配/检验/包装');

-- 字典数据 - 工序类别
INSERT INTO `sys_dict_data` VALUES (600106, '000000', 1, '加工', 'PROCESSING', 'bd_process_category', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '加工工序');
INSERT INTO `sys_dict_data` VALUES (600107, '000000', 2, '装配', 'ASSEMBLY', 'bd_process_category', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '装配工序');
INSERT INTO `sys_dict_data` VALUES (600108, '000000', 3, '检验', 'INSPECTION', 'bd_process_category', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '检验工序');
INSERT INTO `sys_dict_data` VALUES (600109, '000000', 4, '包装', 'PACKAGING', 'bd_process_category', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '包装工序');

-- ============================================================
-- 2. 字典类型 - BOM类型
-- ============================================================
INSERT INTO `sys_dict_type` VALUES (600107, '000000', 'BOM类型', 'bd_bom_type', 103, 1, NOW(), NULL, NULL, 'BOM类型: 工程BOM/制造BOM/服务BOM/配置BOM');

-- 字典数据 - BOM类型
INSERT INTO `sys_dict_data` VALUES (600110, '000000', 1, '工程BOM', 'EBOM', 'bd_bom_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '工程BOM - 设计部门维护');
INSERT INTO `sys_dict_data` VALUES (600111, '000000', 2, '制造BOM', 'MBOM', 'bd_bom_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '制造BOM - 工艺部门维护');
INSERT INTO `sys_dict_data` VALUES (600112, '000000', 3, '服务BOM', 'SBOM', 'bd_bom_type', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '服务BOM - 售后部门维护');
INSERT INTO `sys_dict_data` VALUES (600113, '000000', 4, '配置BOM', 'CBOM', 'bd_bom_type', '', 'info', 'N', 103, 1, NOW(), NULL, NULL, '配置BOM - 可选配产品(预留)');

-- ============================================================
-- 3. 字典类型 - 替代类型
-- ============================================================
INSERT INTO `sys_dict_type` VALUES (600108, '000000', '替代料类型', 'bd_substitution_type', 103, 1, NOW(), NULL, NULL, 'BOM替代料替代类型');

-- 字典数据 - 替代类型
INSERT INTO `sys_dict_data` VALUES (600114, '000000', 1, '完全替代', 'FULL', 'bd_substitution_type', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, '完全替代 - 1:1替换');
INSERT INTO `sys_dict_data` VALUES (600115, '000000', 2, '部分替代', 'PARTIAL', 'bd_substitution_type', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, '部分替代 - 按比例替换');
INSERT INTO `sys_dict_data` VALUES (600116, '000000', 3, '互换', 'INTERCHANGEABLE', 'bd_substitution_type', '', 'primary', 'N', 103, 1, NOW(), NULL, NULL, '互换 - 双向可替换');
