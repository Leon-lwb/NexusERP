-- 检查 sys_notice 表的字段配置
SELECT 
    table_id,
    column_name,
    column_comment,
    dict_type,
    html_type
FROM gen_table_column
WHERE table_id IN (
    SELECT table_id FROM gen_table WHERE table_name = 'sys_notice'
)
AND column_name = 'notice_type';

-- 检查 sys_notice 表的其他字段配置
SELECT 
    column_name,
    column_comment,
    dict_type,
    html_type,
    is_list,
    is_query,
    is_edit
FROM gen_table_column
WHERE table_id IN (
    SELECT table_id FROM gen_table WHERE table_name = 'sys_notice'
)
ORDER BY sort;

-- 检查 notice_type 字典数据
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

-- 检查所有与 notice 相关的字典类型
SELECT DISTINCT dict_type
FROM sys_dict_data
WHERE dict_type LIKE '%notice%'
   OR dict_type LIKE '%sys_notice%';
