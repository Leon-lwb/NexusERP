package com.nexus.generator.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * 代码生成业务字段表 gen_table_column
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_column")
public class GenTableColumn extends BaseEntity {

    /**
     * 编号
     */
    @TableId(value = "column_id")
    private Long columnId;

    /**
     * 归属表编号
     */
    private Long tableId;

    /**
     * 列名称
     */
    private String columnName;

    /**
     * 列描述
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String columnComment;

    /**
     * 列类型
     */
    private String columnType;

    /**
     * JAVA类型
     */
    private String javaType;

    /**
     * JAVA字段名
     */
    @NotBlank(message = "Java属性不能为空")
    private String javaField;

    /**
     * 是否主键（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isPk;

    /**
     * 是否自增（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isIncrement;

    /**
     * 是否必填（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isRequired;

    /**
     * 是否为插入字段（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isInsert;

    /**
     * 是否编辑字段（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isEdit;

    /**
     * 是否列表字段（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isList;

    /**
     * 是否查询字段（1是）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isQuery;

    /**
     * 查询方式（EQ等于、NE不等于、GT大于、LT小于、LIKE模糊、BETWEEN范围）
     */
    private String queryType;

    /**
     * 显示类型（input文本框、textarea文本域、select下拉框、checkbox复选框、radio单选框、datetime日期控件、image图片上传控件、upload文件上传控件、editor富文本控件）
     */
    private String htmlType;

    /**
     * 字典类型
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String dictType;

    /**
     * 表单验证规则JSON
     */
    private String formRules;

    /**
     * 字段联动配置JSON
     */
    private String linkConfig;

    /**
     * 表单栅格宽度(1-24)
     */
    private Integer formSpan;

    /**
     * 是否只读（0-否/1-是）
     */
    private String isReadonly;

    /**
     * 字段提示信息
     */
    private String fieldTip;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 获取首字母大写的 Java 字段名（用于类名/属性引用场景）。
     *
     * @return 首字母大写的字段名
     */
    public String getCapJavaField() {
        return StringUtils.capitalize(javaField);
    }

    /**
     * 判断当前列是否为主键（基于实例字段）。
     */
    public boolean isPk() {
        return isPk(this.isPk);
    }

    /**
     * 判断给定标记字符串是否表示主键。
     *
     * @param isPk 是否主键标记（1 是）
     * @return 是主键返回 true
     */
    public boolean isPk(String isPk) {
        return isPk != null && StringUtils.equals("1", isPk);
    }

    /**
     * 判断当前列是否自增（基于实例字段）。
     */
    public boolean isIncrement() {
        return isIncrement(this.isIncrement);
    }

    /**
     * 判断给定标记字符串是否表示自增。
     *
     * @param isIncrement 是否自增标记（1 是）
     * @return 是自增返回 true
     */
    public boolean isIncrement(String isIncrement) {
        return isIncrement != null && StringUtils.equals("1", isIncrement);
    }

    /**
     * 判断当前列是否必填（基于实例字段）。
     */
    public boolean isRequired() {
        return isRequired(this.isRequired);
    }

    /**
     * 判断给定标记字符串是否表示必填。
     *
     * @param isRequired 是否必填标记（1 是）
     * @return 是必填返回 true
     */
    public boolean isRequired(String isRequired) {
        return isRequired != null && StringUtils.equals("1", isRequired);
    }

    /**
     * 判断当前列是否为插入字段（基于实例字段）。
     */
    public boolean isInsert() {
        return isInsert(this.isInsert);
    }

    /**
     * 判断给定标记字符串是否表示插入字段。
     *
     * @param isInsert 是否插入字段标记（1 是）
     * @return 是插入字段返回 true
     */
    public boolean isInsert(String isInsert) {
        return isInsert != null && StringUtils.equals("1", isInsert);
    }

    /**
     * 判断当前列是否为编辑字段（基于实例字段）。
     */
    public boolean isEdit() {
        return isEdit(this.isEdit);
    }

    /**
     * 判断给定标记字符串是否表示编辑字段。
     *
     * @param isEdit 是否编辑字段标记（1 是）
     * @return 是编辑字段返回 true
     */
    public boolean isEdit(String isEdit) {
        return isEdit != null && StringUtils.equals("1", isEdit);
    }

    /**
     * 判断当前列是否为列表字段（基于实例字段）。
     */
    public boolean isList() {
        return isList(this.isList);
    }

    /**
     * 判断给定标记字符串是否表示列表字段。
     *
     * @param isList 是否列表字段标记（1 是）
     * @return 是列表字段返回 true
     */
    public boolean isList(String isList) {
        return isList != null && StringUtils.equals("1", isList);
    }

    /**
     * 判断当前列是否为查询字段（基于实例字段）。
     */
    public boolean isQuery() {
        return isQuery(this.isQuery);
    }

    /**
     * 判断给定标记字符串是否表示查询字段。
     *
     * @param isQuery 是否查询字段标记（1 是）
     * @return 是查询字段返回 true
     */
    public boolean isQuery(String isQuery) {
        return isQuery != null && StringUtils.equals("1", isQuery);
    }

    /**
     * 判断当前列是否为基类保留字段（基于实例字段）。
     */
    public boolean isSuperColumn() {
        return isSuperColumn(this.javaField);
    }

    /**
     * 判断给定字段名是否属于基类（BaseEntity/TreeEntity）字段，生成时应忽略。
     *
     * @param javaField 字段名
     * @return 属于基类字段返回 true
     */
    public static boolean isSuperColumn(String javaField) {
        return StringUtils.equalsAnyIgnoreCase(javaField,
            // BaseEntity
            "createBy", "createTime", "updateBy", "updateTime",
            // TreeEntity
            "parentName", "parentId");
    }

    /**
     * 判断当前列是否为可用业务字段（基于实例字段）。
     */
    public boolean isUsableColumn() {
        return isUsableColumn(javaField);
    }

    /**
     * 判断给定字段是否为可用业务字段（白名单），生成页面时不可忽略。
     *
     * @param javaField 字段名
     * @return 可用返回 true
     */
    public static boolean isUsableColumn(String javaField) {
        // isSuperColumn()中的名单用于避免生成多余Domain属性，若某些属性在生成页面时需要用到不能忽略，则放在此处白名单
        return StringUtils.equalsAnyIgnoreCase(javaField, "parentId", "orderNum", "remark");
    }

    /**
     * 判断是否只读
     */
    public boolean isReadonly() {
        return isReadonly(this.isReadonly);
    }

    public boolean isReadonly(String isReadonly) {
        return isReadonly != null && StringUtils.equals("1", isReadonly);
    }

    /**
     * 解析字段注释中的字典转换表达式。
     * <p>从列注释的「（」与「）」之间提取 "值=含义" 片段，拼为 {@code 值=含义;} 形式，
     * 若无括号内容则返回原注释。</p>
     *
     * @return 字典转换表达式字符串
     */
    public String readConverterExp() {
        String remarks = StringUtils.substringBetween(this.columnComment, "（", "）");
        StringBuffer sb = new StringBuffer();
        if (StringUtils.isNotEmpty(remarks)) {
            for (String value : remarks.split(" ")) {
                if (StringUtils.isNotEmpty(value)) {
                    Object startStr = value.subSequence(0, 1);
                    String endStr = value.substring(1);
                    sb.append(StringUtils.EMPTY).append(startStr).append("=").append(endStr).append(StringUtils.SEPARATOR);
                }
            }
            return sb.deleteCharAt(sb.length() - 1).toString();
        } else {
            return this.columnComment;
        }
    }
}
