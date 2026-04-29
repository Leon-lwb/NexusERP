-- 检查 sys_notice_type 字典数据
SELECT 
    dict_code,
    dict_sort,
    dict_label,
    dict_value,
    dict_type,
    status
FROM sys_dict_data
WHERE dict_type = 'sys_notice_type'
ORDER BY dict_sort;

-- 检查 sys_notice_type 字典数据（不带状态过滤）
SELECT 
    dict_code,
    dict_sort,
    dict_label,
    dict_value,
    dict_type,
    status
FROM sys_dict_data
WHERE dict_type = 'sys_notice_type'
ORDER BY dict_sort;

-- 检查所有字典类型
SELECT DISTINCT dict_type
FROM sys_dict_data
ORDER BY dict_type;
