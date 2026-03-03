package com.nexus.online.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.generator.service.IGenTableService;
import com.nexus.online.domain.GenTableDesign;
import com.nexus.online.mapper.GenTableDesignMapper;
import com.nexus.online.service.ITableDesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableDesignServiceImpl implements ITableDesignService {

    private final GenTableDesignMapper designMapper;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired(required = false)
    private IGenTableService genTableService;

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

    @Override
    public List<GenTableDesign> list(String keyword) {
        LambdaQueryWrapper<GenTableDesign> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(GenTableDesign::getDesignName, keyword)
              .or().like(GenTableDesign::getTableName, keyword)
              .or().like(GenTableDesign::getTableComment, keyword);
        }
        qw.orderByDesc(GenTableDesign::getUpdateTime);
        return designMapper.selectList(qw);
    }

    @Override
    public GenTableDesign getById(Long id) {
        return designMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeById(Long id) {
        designMapper.deleteById(id);
    }

    @Override
    public String generateSql(String designJson) {
        ObjectNode root = JsonUtils.parseObject(designJson, ObjectNode.class);
        if (root == null) {
            return "";
        }
        
        ArrayNode tables = root.has("tables") ? root.withArray("tables") : null;
        ArrayNode relations = root.has("relations") ? root.withArray("relations") : null;

        Map<String, ArrayNode> tableToFields = new HashMap<>();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JsonNode t = tables.get(i);
                String name = t.has("name") ? t.get("name").asText() : "";
                if (t.has("fields") && t.get("fields").isArray()) {
                    tableToFields.put(name, (ArrayNode) t.get("fields"));
                }
            }
        }

        StringBuilder full = new StringBuilder();
        if (tables != null) {
            for (int i = 0; i < tables.size(); i++) {
                JsonNode t = tables.get(i);
                full.append(buildCreateTableSql(t));
            }
        }
        if (relations != null) {
            for (int i = 0; i < relations.size(); i++) {
                JsonNode r = relations.get(i);
                String fromTable = r.has("fromTable") ? r.get("fromTable").asText() : "";
                String fromField = r.has("fromField") ? r.get("fromField").asText() : "";
                String toTable = r.has("toTable") ? r.get("toTable").asText() : "";
                String toField = r.has("toField") ? r.get("toField").asText() : "";
                if (!StringUtils.hasText(fromTable) || !StringUtils.hasText(fromField) || !StringUtils.hasText(toTable) || !StringUtils.hasText(toField)) continue;

                JsonNode fromFld = findField(tableToFields.get(fromTable), fromField);
                JsonNode toFld = findField(tableToFields.get(toTable), toField);
                if (fromFld == null || toFld == null) continue;
                String fromBase = normalizeBaseType(fromFld.has("type") ? fromFld.get("type").asText() : "");
                String toBase = normalizeBaseType(toFld.has("type") ? toFld.get("type").asText() : "");
                if (!fromBase.equals(toBase)) continue;

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

    private JsonNode findField(ArrayNode fields, String name) {
        if (fields == null || !StringUtils.hasText(name)) return null;
        for (int i = 0; i < fields.size(); i++) {
            JsonNode f = fields.get(i);
            if (f.has("name") && name.equals(f.get("name").asText())) return f;
        }
        return null;
    }

    private String normalizeBaseType(String type) {
        if (type == null) return "";
        String lower = type.toLowerCase();
        int p = lower.indexOf('(');
        return p > 0 ? lower.substring(0, p) : lower;
    }

    private String buildCreateTableSql(JsonNode table) {
        String tableName = table.has("name") ? table.get("name").asText() : "";
        String engine = table.has("engine") ? table.get("engine").asText() : "InnoDB";
        String charset = table.has("charset") ? table.get("charset").asText() : "utf8mb4";
        String comment = table.has("comment") ? table.get("comment").asText() : "";
        ArrayNode fields = table.has("fields") && table.get("fields").isArray() ? (ArrayNode) table.get("fields") : null;
        List<String> columnDefs = new ArrayList<>();
        List<String> pks = new ArrayList<>();
        List<String> unique = new ArrayList<>();
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                JsonNode f = fields.get(i);
                String name = f.has("name") ? f.get("name").asText() : "";
                String type = f.has("type") ? f.get("type").asText() : "varchar";
                Integer length = f.has("length") ? f.get("length").asInt() : null;
                Integer precision = f.has("precision") ? f.get("precision").asInt() : null;
                Integer scale = f.has("scale") ? f.get("scale").asInt() : null;
                boolean notNull = f.has("isNotNull") && f.get("isNotNull").asBoolean();
                boolean isPk = f.has("isPrimaryKey") && f.get("isPrimaryKey").asBoolean();
                boolean isUnique = f.has("isUnique") && f.get("isUnique").asBoolean();
                String defVal = f.has("defaultValue") ? f.get("defaultValue").asText() : "";
                String colComment = f.has("comment") ? f.get("comment").asText() : "";

                String typeLower = type.toLowerCase();
                String typeDef = type;
                if (supportsLength(typeLower)) {
                    int effLen = (length != null) ? length : 255;
                    typeDef = typeLower + "(" + effLen + ")";
                } else if (isDecimalType(typeLower) && (precision != null || scale != null)) {
                    int p = precision != null ? precision : 10;
                    int s = scale != null ? scale : 0;
                    typeDef = typeLower + "(" + p + "," + s + ")";
                }

                StringBuilder col = new StringBuilder("  `").append(name).append("` ").append(typeDef);
                if (notNull) col.append(" NOT NULL");
                if (StringUtils.hasText(defVal)) {
                    if (isNumericType(typeLower)) {
                        col.append(" DEFAULT ").append(defVal);
                    } else if ("current_timestamp".equals(defVal.toLowerCase())) {
                        col.append(" DEFAULT CURRENT_TIMESTAMP");
                    } else {
                        col.append(" DEFAULT '").append(defVal.replace("'", "''")).append("'");
                    }
                }
                if (StringUtils.hasText(colComment)) {
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
        if (StringUtils.hasText(comment)) sql.append(" COMMENT='").append(comment.replace("'", "''")).append("'");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTables(String sql, boolean overwriteIfExists) {
        if (!StringUtils.hasText(sql)) {
            return;
        }
        String[] parts = sql.split(";\n|;\r\n|;\r|;");
        for (String raw : parts) {
            String s = raw.trim();
            if (!StringUtils.hasText(s)) continue;

            String sNoComments = removeLeadingComments(s);

            if (overwriteIfExists && isCreateTableStatement(sNoComments)) {
                String tableName = extractCreateTableName(sNoComments);
                if (StringUtils.hasText(tableName)) {
                    try {
                        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                    } catch (Exception e) {
                        throw new ServiceException("删除已有表失败：" + tableName);
                    }
                }
            }
            String executable = removeAllComments(s);
            executable = executable.trim();
            if (!StringUtils.hasText(executable)) continue;
            try {
                jdbcTemplate.execute(executable);
            } catch (Exception e) {
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
        if (!StringUtils.hasText(sqlStmt)) return sqlStmt;
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
        if (!StringUtils.hasText(sqlStmt)) return sqlStmt;
        String noBlock = sqlStmt.replaceAll("/\\*([\\s\\S]*?)\\*/", " ");
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
        if (!StringUtils.hasText(sqlStmt)) return false;
        String lower = sqlStmt.trim().toLowerCase();
        return lower.startsWith("create table");
    }

    private String extractCreateTableName(String sqlStmt) {
        if (!StringUtils.hasText(sqlStmt)) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:`([^`]+)`|([a-zA-Z0-9_]+))");
        java.util.regex.Matcher m = p.matcher(sqlStmt);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return StringUtils.hasText(g1) ? g1 : g2;
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importToGen(String designJson, String dataName, boolean overwriteIfExists) {
        try {
            ObjectNode root = JsonUtils.parseObject(designJson, ObjectNode.class);
            if (root == null) {
                throw new ServiceException("没有表数据");
            }
            
            ArrayNode tables = root.has("tables") ? root.withArray("tables") : null;
            if (tables == null || tables.isEmpty()) {
                throw new ServiceException("没有表数据");
            }
            
            List<GenTable> list = new ArrayList<>();
            for (int i = 0; i < tables.size(); i++) {
                JsonNode t = tables.get(i);
                String tableName = t.has("name") ? t.get("name").asText() : "";
                
                if (!StringUtils.hasText(tableName)) {
                    continue;
                }
                
                GenTable gt = new GenTable();
                gt.setDataName(StringUtils.hasText(dataName) ? dataName : "master");
                gt.setTableName(tableName);
                gt.setTableComment(StringUtils.hasText(t.has("comment") ? t.get("comment").asText() : "") ? t.get("comment").asText() : tableName);
                gt.setClassName(capitalize(tableName));
                gt.setTplCategory("crud");
                gt.setPackageName("com.nexus.modules");
                gt.setModuleName("modules");
                gt.setFunctionName(gt.getTableComment());
                gt.setFunctionAuthor("system");
                gt.setBusinessName(tableName);

                List<GenTableColumn> cols = new ArrayList<>();
                ArrayNode fields = t.has("fields") && t.get("fields").isArray() ? (ArrayNode) t.get("fields") : null;
                if (fields != null) {
                    int sort = 1;
                    for (int j = 0; j < fields.size(); j++) {
                        JsonNode f = fields.get(j);
                        String fieldName = f.has("name") ? f.get("name").asText() : "";
                        String fieldType = f.has("type") ? f.get("type").asText() : "";
                        
                        if (!StringUtils.hasText(fieldName)) {
                            continue;
                        }
                        
                        GenTableColumn c = new GenTableColumn();
                        c.setColumnName(fieldName);
                        c.setColumnComment(StringUtils.hasText(f.has("comment") ? f.get("comment").asText() : "") ? f.get("comment").asText() : fieldName);
                        c.setColumnType(resolveColumnType(f));
                        c.setJavaType(resolveJavaType(fieldType));
                        c.setJavaField(toCamelCase(fieldName));
                        c.setIsPk(booleanToOne(f.has("isPrimaryKey") && f.get("isPrimaryKey").asBoolean()));
                        c.setIsIncrement("0");
                        c.setIsRequired(booleanToOne(f.has("isNotNull") && f.get("isNotNull").asBoolean()));
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
            
            if (genTableService != null) {
                genTableService.importGenTable(list, StringUtils.hasText(dataName) ? dataName : "master");
            }
        } catch (Exception e) {
            log.error("导入表设计到代码生成失败", e);
            throw new ServiceException("导入失败: " + e.getMessage());
        }
    }

    private String resolveColumnType(JsonNode f) {
        String type = f.has("type") ? f.get("type").asText().toLowerCase() : "varchar";
        Integer length = f.has("length") ? f.get("length").asInt() : null;
        Integer precision = f.has("precision") ? f.get("precision").asInt() : null;
        Integer scale = f.has("scale") ? f.get("scale").asInt() : null;
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
        if (!StringUtils.hasText(s)) return "Entity";
        String[] parts = s.split("_+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "");
        }
        return b.toString();
    }

    private String resolveJavaType(String fieldType) {
        if (!StringUtils.hasText(fieldType)) return "String";
        
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

    private String resolveHtmlType(String fieldType, JsonNode field) {
        if (!StringUtils.hasText(fieldType)) return "input";
        
        String type = fieldType.toLowerCase();
        if (type.contains("date") || type.contains("time")) {
            return "datetime";
        } else if (type.contains("text") || type.contains("longtext")) {
            return "textarea";
        } else if (type.contains("int") || type.contains("decimal") || type.contains("numeric")) {
            return "input";
        } else if (type.contains("tinyint") || type.contains("bit")) {
            return "radio";
        } else {
            return "input";
        }
    }

    private String toCamelCase(String name) {
        if (!StringUtils.hasText(name)) return "";
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }
}
