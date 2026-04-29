-- ============================================
-- 代码生成与Online设计合并 - 数据库迁移脚本
-- 版本: 1.0.0
-- 说明: 扩展gen_table和gen_table_column表，支持Online设计功能
-- ============================================

-- --------------------------------------------
-- 1. 扩展 gen_table 表
-- --------------------------------------------

-- 增加来源类型字段
ALTER TABLE gen_table
    ADD COLUMN source_type VARCHAR(20) DEFAULT 'import' COMMENT '来源类型：import-从数据库导入/design-在线设计' AFTER function_author;

-- 增加关联设计ID字段
ALTER TABLE gen_table
    ADD COLUMN design_id BIGINT COMMENT '关联的Online设计ID' AFTER source_type;

-- 增加设计内容字段（冗余存储，便于快速读取）
ALTER TABLE gen_table
    ADD COLUMN design_content TEXT COMMENT 'Online设计JSON内容' AFTER design_id;

-- 增加是否支持Online表单字段
ALTER TABLE gen_table
    ADD COLUMN is_online_form CHAR(1) DEFAULT '0' COMMENT '是否启用Online表单：0-否/1-是' AFTER design_content;

-- --------------------------------------------
-- 2. 扩展 gen_table_column 表
-- --------------------------------------------

-- 增加表单验证规则字段
ALTER TABLE gen_table_column
    ADD COLUMN form_rules TEXT COMMENT '表单验证规则JSON' AFTER dict_type;

-- 增加字段联动配置字段
ALTER TABLE gen_table_column
    ADD COLUMN link_config TEXT COMMENT '字段联动配置JSON' AFTER form_rules;

-- 增加表单栅格宽度字段
ALTER TABLE gen_table_column
    ADD COLUMN form_span INT DEFAULT 24 COMMENT '表单栅格宽度(1-24)' AFTER link_config;

-- 增加是否只读字段
ALTER TABLE gen_table_column
    ADD COLUMN is_readonly CHAR(1) DEFAULT '0' COMMENT '是否只读：0-否/1-是' AFTER form_span;

-- 增加字段提示信息
ALTER TABLE gen_table_column
    ADD COLUMN field_tip VARCHAR(500) COMMENT '字段提示信息' AFTER is_readonly;

-- --------------------------------------------
-- 3. 创建设计历史表（可选，用于版本管理）
-- --------------------------------------------

CREATE TABLE IF NOT EXISTS gen_table_design_history (
    history_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '历史记录ID',
    table_id BIGINT NOT NULL COMMENT '关联的gen_table ID',
    design_content TEXT NOT NULL COMMENT '设计JSON内容',
    version INT DEFAULT 1 COMMENT '版本号',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME COMMENT '创建时间',
    remark VARCHAR(500) COMMENT '备注'
) COMMENT '代码生成表设计历史记录';

-- --------------------------------------------
-- 4. 创建索引优化查询
-- --------------------------------------------

-- 为gen_table的design_id创建索引
CREATE INDEX idx_gen_table_design_id ON gen_table(design_id);

-- 为gen_table的source_type创建索引
CREATE INDEX idx_gen_table_source_type ON gen_table(source_type);

-- 为gen_table_design_history的table_id创建索引
CREATE INDEX idx_design_history_table_id ON gen_table_design_history(table_id);

-- --------------------------------------------
-- 5. 数据迁移（将现有的Online设计数据关联到代码生成）
-- --------------------------------------------

-- 如果有gen_table_design表，将其数据与gen_table关联
-- 注意：此步骤需要根据实际数据情况调整

-- UPDATE gen_table t
-- INNER JOIN gen_table_design d ON t.table_name = d.table_name
-- SET t.design_id = d.design_id,
--     t.design_content = d.content,
--     t.source_type = 'design',
--     t.is_online_form = '1'
-- WHERE t.table_name = d.table_name;

-- --------------------------------------------
-- 6. 验证脚本（执行后检查）
-- --------------------------------------------

-- 检查gen_table表结构
-- DESC gen_table;

-- 检查gen_table_column表结构
-- DESC gen_table_column;

-- 检查数据迁移结果
-- SELECT table_id, table_name, source_type, design_id, is_online_form FROM gen_table LIMIT 10;
