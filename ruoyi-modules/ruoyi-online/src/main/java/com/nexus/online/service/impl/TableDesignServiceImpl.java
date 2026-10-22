package com.nexus.online.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.generator.domain.GenTableDesign;
import com.nexus.generator.mapper.GenTableDesignMapper;
import com.nexus.generator.service.IGenTableService;
import com.nexus.online.service.ITableDesignService;

import jakarta.annotation.Resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在线表单设计Service业务层处理
 *
 * @author wanghuan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableDesignServiceImpl implements ITableDesignService {

    /** 表设计 Mapper，负责 gen_table_design 的持久化。 */
    private final GenTableDesignMapper designMapper;
    /** JDBC 模板，用于执行动态生成的建表/删表 SQL。 */
    private final JdbcTemplate jdbcTemplate;
    @Resource
    /** 代码生成表业务服务，用于导入表设计到代码生成器。 */
    private IGenTableService genTableService;

    /**
     * 保存或更新表设计（新增写创建时间，更新写更新时间）。
     *
     * @param design 表设计实体
     * @return 保存后的设计 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdate(GenTableDesign design) {
        if (design.getDesignId() == null) {
            design.setCreateTime(new Date());
            designMapper.insert(design);
        } else {
            design.setUpdateTime(new Date());
            designMapper.updateById(design);
        }
        return design.getDesignId();
    }

    /**
     * 按关键字查询表设计列表。
     *
     * @param keyword 可选查询关键字，匹配设计名、表名或表注释
     * @return 表设计列表，按更新时间倒序
     */
    @Override
    public List<GenTableDesign> list(String keyword) {
        LambdaQueryWrapper<GenTableDesign> qw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            qw.like(GenTableDesign::getDesignName, keyword)
              .or().like(GenTableDesign::getTableName, keyword)
              .or().like(GenTableDesign::getTableComment, keyword);
        }
        qw.orderByDesc(GenTableDesign::getUpdateTime);
        return designMapper.selectList(qw);
    }

    /**
     * 查询指定表设计的详情。
     *
     * @param id 表设计 ID
     * @return 表设计实体
     */
    @Override
    public GenTableDesign getById(Long id) {
        return designMapper.selectById(id);
    }

    /**
     * 删除指定表设计。
     *
     * @param id 待删除的表设计 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeById(Long id) {
        designMapper.deleteById(id);
    }

    /**
     * 根据设计 JSON 生成建表及外键 SQL（不执行）。
     *
     * @param designJson 表设计 JSON（含 tables 与 relations）
     * @return 生成的完整 SQL 字符串
     */
    @Override
    public String generateSql(String designJson) {
        JSONObject root = JSON.parseObject(designJson);
        JSONArray tables = root.getJSONArray("tables");
        JSONArray relations = root.getJSONArray("relations");

        // 构建表->字段映射，便于外键校验
        Map<String, JSONArray> tableToFields = new HashMap<>();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                tableToFields.put(StringUtils.defaultString(t.getString("name")), t.getJSONArray("fields"));
            }
        }

        StringBuilder full = new StringBuilder();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                full.append(buildCreateTableSql(t));
            }
        }
        if (relations != null) {
            for (int i = 0; i < relations.size(); i++) {
                JSONObject r = relations.getJSONObject(i);
                String fromTable = r.getString("fromTable");
                String fromField = r.getString("fromField");
                String toTable = r.getString("toTable");
                String toField = r.getString("toField");
                if (StringUtils.isAnyBlank(fromTable, fromField, toTable, toField)) continue;

                // 字段存在且基础类型一致才生成外键
                JSONObject fromFld = findField(tableToFields.get(fromTable), fromField);
                JSONObject toFld = findField(tableToFields.get(toTable), toField);
                if (fromFld == null || toFld == null) continue;
                String fromBase = normalizeBaseType(fromFld.getString("type"));
                String toBase = normalizeBaseType(toFld.getString("type"));
                if (!StringUtils.equals(fromBase, toBase)) continue;

                String fkName = String.format("fk_%s_%s_%d", fromTable, fromField, i);
                full.append("ALTER TABLE `").append(fromTable).append("` ADD CONSTRAINT `")
                    .append(fkName).append("` FOREIGN KEY (`").append(fromField)
                    .append("`) REFERENCES `").append(toTable).append("`(`")
                    .append(toField).append("`);")
                    .append('\n');
            }
        }
        return full.toString();
    }

    private JSONObject findField(JSONArray fields, String name) {
        if (fields == null || StringUtils.isBlank(name)) return null;
        for (int i = 0; i < fields.size(); i++) {
            JSONObject f = fields.getJSONObject(i);
            if (StringUtils.equals(name, f.getString("name"))) return f;
        }
        return null;
    }

    private String normalizeBaseType(String type) {
        if (type == null) return "";
        String lower = type.toLowerCase();
        int p = lower.indexOf('(');
        return p > 0 ? lower.substring(0, p) : lower;
    }

    private String buildCreateTableSql(JSONObject table) {
        String tableName = table.getString("name");
        String engine = StringUtils.defaultIfBlank(table.getString("engine"), "InnoDB");
        String charset = StringUtils.defaultIfBlank(table.getString("charset"), "utf8mb4");
        String comment = StringUtils.defaultString(table.getString("comment"));
        JSONArray fields = table.getJSONArray("fields");
        List<String> columnDefs = new ArrayList<>();
        List<String> pks = new ArrayList<>();
        List<String> unique = new ArrayList<>();
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                JSONObject f = fields.getJSONObject(i);
                String name = f.getString("name");
                String type = StringUtils.defaultIfBlank(f.getString("type"), "varchar");
                Integer length = f.getInteger("length");
                Integer precision = f.getInteger("precision");
                Integer scale = f.getInteger("scale");
                boolean notNull = f.getBooleanValue("isNotNull");
                boolean isPk = f.getBooleanValue("isPrimaryKey");
                boolean isUnique = f.getBooleanValue("isUnique");
                String defVal = f.getString("defaultValue");
                String colComment = f.getString("comment");

                String typeLower = type.toLowerCase();
                String typeDef = type;
                if (supportsLength(typeLower)) {
                    int effLen = (length != null) ? length : 255; // 默认 255
                    typeDef = typeLower + "(" + effLen + ")";
                } else if (isDecimalType(typeLower) && (precision != null || scale != null)) {
                    int p = precision != null ? precision : 10;
                    int s = scale != null ? scale : 0;
                    typeDef = typeLower + "(" + p + "," + s + ")";
                }

                StringBuilder col = new StringBuilder("  `").append(name).append("` ").append(typeDef);
                if (notNull) col.append(" NOT NULL");
                if (StringUtils.isNotBlank(defVal)) {
                    if (isNumericType(typeLower)) {
                        col.append(" DEFAULT ").append(defVal);
                    } else if ("current_timestamp".equals(defVal.toLowerCase())) {
                        col.append(" DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        col.append(" DEFAULT '").append(defVal.replace("'", "''")).append("'");
                    }
                }
                if (StringUtils.isNotBlank(colComment)) {
                    col.append(" COMMENT '").append(colComment.replace("'", "''")).append("'");
                }
                columnDefs.add(col.toString());
                if (isPk) pks.add(name);
                if (isUnique && !isPk) unique.add(name);
            }
        }
        if (!pks.isEmpty()) {
            columnDefs.add("  PRIMARY KEY (" + String.join(", ", pks.stream().map(n -> "`" + n + "`").toList()) + ")");
        }
        for (String uq : unique) {
            columnDefs.add("  UNIQUE KEY `uk_" + tableName + "_" + uq + "` (`" + uq + "`)");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("-- 创建表 ").append(tableName).append('\n');
        sql.append("CREATE TABLE `").append(tableName).append("` (\n");
        sql.append(String.join(",\n", columnDefs));
        sql.append("\n) ENGINE=").append(engine).append(" DEFAULT CHARSET=").append(charset);
        if (StringUtils.isNotBlank(comment)) sql.append(" COMMENT='").append(comment.replace("'", "''")).append("'");
        sql.append(";\n\n");
        return sql.toString();
    }

    private boolean supportsLength(String type) {
        return List.of("varchar", "char", "varbinary").contains(type.toLowerCase());
    }

    private boolean isDecimalType(String type) {
        return List.of("decimal", "numeric", "number").contains(type.toLowerCase());
    }

    private boolean isNumericType(String type) {
        return List.of("int", "integer", "bigint", "smallint", "tinyint", "float", "double", "decimal", "numeric").contains(type.toLowerCase());
    }

    /**
     * 执行建表 SQL，在数据库中创建物理表。
     *
     * @param sql 完整建表 SQL（可含多条语句）
     * @param overwriteIfExists 表已存在时是否先删除后重建
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTables(String sql, boolean overwriteIfExists) {
        if (StringUtils.isBlank(sql)) {
            return;
        }
        // 简单分句：按分号拆分，忽略空行
        String[] parts = sql.split(";\n|;\r\n|;\r|;");
        for (String raw : parts) {
            String s = StringUtils.trim(raw);
            if (StringUtils.isBlank(s)) continue;

            // 去除开头的单行与块注释再判断是否为 CREATE TABLE
            String sNoComments = removeLeadingComments(s);

            // 安全白名单：仅允许 CREATE TABLE 语句，拒绝其他任意 SQL（DROP/ALTER/UPDATE/DELETE 等）
            if (!isCreateTableStatement(sNoComments)) {
                throw new ServiceException("仅允许执行 CREATE TABLE 建表语句");
            }

            if (overwriteIfExists && isCreateTableStatement(sNoComments)) {
                String tableName = extractCreateTableName(sNoComments);
                if (StringUtils.isNotBlank(tableName)) {
                    try {
                        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                    } catch (Exception e) {
                        throw new ServiceException("删除已有表失败：" + tableName);
                    }
                }
            }
            // 执行前移除所有注释，避免驱动不接受带注释语句
            String executable = removeAllComments(s);
            executable = StringUtils.trim(executable);
            if (StringUtils.isBlank(executable)) continue;
            try {
                jdbcTemplate.execute(executable);
            } catch (Exception e) {
                // 友好提示
                String lower = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (isCreateTableStatement(sNoComments)) {
                    if (lower.contains("exists") || lower.contains("already") || lower.contains("1050")) {
                        throw new ServiceException("表已经存在，创建失败！");
                    }
                    throw new ServiceException("创建表失败，请检查表字段类型与长度设置是否正确。");
                }
                throw new ServiceException("SQL执行失败，请检查设计配置。");
            }
        }
    }

    private String removeLeadingComments(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return sqlStmt;
        String[] lines = sqlStmt.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : lines) {
            String l = line;
            String trimmed = l.trim();
            if (!inBlock) {
                if (trimmed.startsWith("/*")) {
                    inBlock = true;
                    if (trimmed.endsWith("*/")) {
                        inBlock = false;
                    }
                    continue;
                }
                if (trimmed.startsWith("--")) {
                    continue;
                }
                sb.append(line).append('\n');
            } else {
                if (trimmed.endsWith("*/")) {
                    inBlock = false;
                }
            }
        }
        return sb.toString().trim();
    }

    private String removeAllComments(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return sqlStmt;
        // 去除块注释 /* ... */
        String noBlock = sqlStmt.replaceAll("/\\*([\\s\\S]*?)\\*/", " ");
        // 去除行注释 -- 直到行尾
        String[] lines = noBlock.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int idx = line.indexOf("--");
            String part = idx >= 0 ? line.substring(0, idx) : line;
            sb.append(part).append('\n');
        }
        return sb.toString();
    }

    private boolean isCreateTableStatement(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return false;
        String lower = sqlStmt.trim().toLowerCase();
        return lower.startsWith("create table");
    }

    private String extractCreateTableName(String sqlStmt) {
        if (StringUtils.isBlank(sqlStmt)) return null;
        // 匹配 create table [if not exists] `name` 或 标识符
        Pattern p =
                Pattern.compile(
                        "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:`([^`]+)`|([a-zA-Z0-9_]+))");
        Matcher m = p.matcher(sqlStmt);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return StringUtils.isNotBlank(g1) ? g1 : g2;
        }
        return null;
    }

    /**
     * 将表设计导入代码生成器（生成 GenTable / GenTableColumn 配置）。
     *
     * @param designJson 表设计 JSON
     * @param dataName 目标数据源名称，默认 master
     * @param overwriteIfExists 同名表已存在时是否覆盖导入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importToGen(String designJson, String dataName, boolean overwriteIfExists) {
        try {
            JSONObject root = JSON.parseObject(designJson);
            JSONArray tables = root.getJSONArray("tables");
            if (tables == null || tables.isEmpty()) {
                throw new ServiceException("没有表数据");
            }

            List<GenTable> list = new ArrayList<>();
            for (int i = 0; i < tables.size(); i++) {
                JSONObject t = tables.getJSONObject(i);
                String tableName = t.getString("name");

                if (StringUtils.isBlank(tableName)) {
                    continue;
                }

                GenTable gt = new GenTable();
                gt.setDataName(StringUtils.defaultIfBlank(dataName, "master"));
                gt.setTableName(tableName);
                gt.setTableComment(StringUtils.defaultString(t.getString("comment"), tableName));
                gt.setClassName(capitalize(tableName));
                gt.setTplCategory("crud");
                gt.setPackageName("com.nexus.modules");
                gt.setModuleName("modules");
                gt.setFunctionName(gt.getTableComment());
                gt.setFunctionAuthor("system");
                gt.setBusinessName(tableName);

                List<GenTableColumn> cols = new ArrayList<>();
                JSONArray fields = t.getJSONArray("fields");
                if (fields != null) {
                    int sort = 1;
                    for (int j = 0; j < fields.size(); j++) {
                        JSONObject f = fields.getJSONObject(j);
                        String fieldName = f.getString("name");
                        String fieldType = f.getString("type");

                        if (StringUtils.isBlank(fieldName)) {
                            continue;
                        }

                        GenTableColumn c = new GenTableColumn();
                        c.setColumnName(fieldName);
                        c.setColumnComment(StringUtils.defaultString(f.getString("comment"), fieldName));
                        c.setColumnType(resolveColumnType(f));
                        c.setJavaType(resolveJavaType(fieldType));
                        c.setJavaField(toCamelCase(fieldName));
                        c.setIsPk(booleanToOne(f.getBoolean("isPrimaryKey")));
                        c.setIsIncrement("0");
                        c.setIsRequired(booleanToOne(f.getBoolean("isNotNull")));
                        c.setIsInsert("1");
                        c.setIsEdit("1");
                        c.setIsList("1");
                        c.setIsQuery("1");
                        c.setQueryType("LIKE");
                        c.setHtmlType(resolveHtmlType(fieldType, f));
                        c.setSort(sort++);
                        cols.add(c);
                    }
                }
                gt.setColumns(cols);
                list.add(gt);
            }

            if (list.isEmpty()) {
                throw new ServiceException("没有有效的表数据可以导入");
            }

            // 覆盖导入：如果代码生成中已存在同名表（同数据源），由 GenTableServiceImpl 内部删除后重建
            genTableService.importGenTable(list, StringUtils.defaultIfBlank(dataName, "master"));
        } catch (Exception e) {
            log.error("导入表设计到代码生成失败", e);
            throw new ServiceException("导入失败: " + e.getMessage());
        }
    }

    private String resolveColumnType(JSONObject f) {
        String type = StringUtils.defaultIfBlank(f.getString("type"), "varchar").toLowerCase();
        Integer length = f.getInteger("length");
        Integer precision = f.getInteger("precision");
        Integer scale = f.getInteger("scale");
        if (supportsLength(type)) {
            int effLen = (length != null) ? length : 255;
            return type + "(" + effLen + ")";
        }
        if (isDecimalType(type) && (precision != null || scale != null)) {
            int p = precision != null ? precision : 10;
            int s = scale != null ? scale : 0;
            return type + "(" + p + "," + s + ")";
        }
        return type;
    }

    private String booleanToOne(Boolean value) {
        return (value != null && value) ? "1" : "0";
    }

    private String capitalize(String s) {
        if (StringUtils.isBlank(s)) return "Entity";
        String[] parts = s.split("_+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "");
        }
        return b.toString();
    }

    private String resolveJavaType(String fieldType) {
        if (StringUtils.isBlank(fieldType)) return "String";

        String type = fieldType.toLowerCase();
        if (type.contains("int") || type.contains("bigint") || type.contains("smallint") || type.contains("tinyint")) {
            return "Long";
        } else if (type.contains("decimal") || type.contains("numeric") || type.contains("number")) {
            return "BigDecimal";
        } else if (type.contains("float") || type.contains("double")) {
            return "Double";
        } else if (type.contains("date") || type.contains("time") || type.contains("timestamp")) {
            return "Date";
        } else if (type.contains("text") || type.contains("longtext")) {
            return "String";
        } else {
            return "String";
        }
    }

    private String resolveHtmlType(String fieldType, JSONObject field) {
        if (StringUtils.isBlank(fieldType)) return "input";

        String type = fieldType.toLowerCase();
        if (type.contains("date") || type.contains("time")) {
            return "datetime";
        } else if (type.contains("text") || type.contains("longtext")) {
            return "textarea";
        } else if (type.contains("int") || type.contains("decimal") || type.contains("numeric")) {
            return "input";
        } else {
            return "input";
        }
    }

    private String toCamelCase(String fieldName) {
        if (StringUtils.isBlank(fieldName)) return fieldName;

        String[] parts = fieldName.split("_+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (StringUtils.isBlank(part)) continue;

            if (i == 0) {
                result.append(part.toLowerCase());
            } else {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
}
