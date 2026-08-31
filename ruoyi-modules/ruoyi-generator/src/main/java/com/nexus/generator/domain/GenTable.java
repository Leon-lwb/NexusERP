package com.nexus.generator.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.generator.constant.GenConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 业务表 gen_table
 *
 * @author wanghuan
 */

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table")
public class GenTable extends BaseEntity {

    /**
     * 编号
     */
    @TableId(value = "table_id")
    private Long tableId;

    /**
     * 数据源名称
     */
    @NotBlank(message = "数据源名称不能为空")
    private String dataName;

    /**
     * 表名称
     */
    @NotBlank(message = "表名称不能为空")
    private String tableName;

    /**
     * 表描述
     */
    @NotBlank(message = "表描述不能为空")
    private String tableComment;

    /**
     * 关联父表的表名
     */
    private String subTableName;

    /**
     * 本表关联父表的外键名
     */
    private String subTableFkName;

    /**
     * 实体类名称(首字母大写)
     */
    @NotBlank(message = "实体类名称不能为空")
    private String className;

    /**
     * 使用的模板（crud单表操作 tree树表操作 sub主子表操作）
     */
    private String tplCategory;

    /**
     * 生成包路径
     */
    @NotBlank(message = "生成包路径不能为空")
    private String packageName;

    /**
     * 生成模块名
     */
    @NotBlank(message = "生成模块名不能为空")
    private String moduleName;

    /**
     * 生成业务名
     */
    @NotBlank(message = "生成业务名不能为空")
    private String businessName;

    /**
     * 生成功能名
     */
    @NotBlank(message = "生成功能名不能为空")
    private String functionName;

    /**
     * 生成作者
     */
    @NotBlank(message = "作者不能为空")
    private String functionAuthor;

    /**
     * 来源类型（import-从数据库导入/design-在线设计）
     */
    private String sourceType;

    /**
     * 关联的Online设计ID
     */
    private Long designId;

    /**
     * Online设计JSON内容
     */
    private String designContent;

    /**
     * 是否启用Online表单（0-否/1-是）
     */
    private String isOnlineForm;

    /**
     * 生成代码方式（0zip压缩包 1自定义路径）
     */
    private String genType;

    /**
     * 生成路径（不填默认项目路径）
     */
    @TableField(updateStrategy = FieldStrategy.NOT_EMPTY)
    private String genPath;

    /**
     * 主键信息
     */
    @TableField(exist = false)
    private GenTableColumn pkColumn;

    /**
     * 表列信息
     */
    @Valid
    @TableField(exist = false)
    private List<GenTableColumn> columns;

    /**
     * 其它生成选项
     */
    private String options;

    /**
     * 备注
     */
    private String remark;

    /**
     * 树编码字段
     */
    @TableField(exist = false)
    private String treeCode;

    /**
     * 树父编码字段
     */
    @TableField(exist = false)
    private String treeParentCode;

    /**
     * 树名称字段
     */
    @TableField(exist = false)
    private String treeName;

    /*
     * 菜单id列表
     */
    @TableField(exist = false)
    private List<Long> menuIds;

    /**
     * 上级菜单ID字段
     */
    @TableField(exist = false)
    private Long parentMenuId;

    /**
     * 上级菜单名称字段
     */
    @TableField(exist = false)
    private String parentMenuName;

    /**
     * 判断当前表是否为树表（模板类型为 tree）。
     */
    public boolean isTree() {
        return isTree(this.tplCategory);
    }

    /**
     * 判断模板类型是否为树表。
     *
     * @param tplCategory 模板类型
     * @return 是树表返回 true
     */
    public static boolean isTree(String tplCategory) {
        return tplCategory != null && StringUtils.equals(GenConstants.TPL_TREE, tplCategory);
    }

    /**
     * 判断当前表是否为单表（模板类型为 crud）。
     */
    public boolean isCrud() {
        return isCrud(this.tplCategory);
    }

    /**
     * 判断模板类型是否为单表。
     *
     * @param tplCategory 模板类型
     * @return 是单表返回 true
     */
    public static boolean isCrud(String tplCategory) {
        return tplCategory != null && StringUtils.equals(GenConstants.TPL_CRUD, tplCategory);
    }

    /**
     * 判断指定字段是否为基类（BaseEntity/TreeEntity）字段。
     */
    public boolean isSuperColumn(String javaField) {
        return isSuperColumn(this.tplCategory, javaField);
    }

    /**
     * 判断给定字段名是否属于基类字段，生成代码时应忽略。
     *
     * @param tplCategory 模板类型（预留参数，便于后续按模板差异化）
     * @param javaField   字段名
     * @return 属于基类字段返回 true
     */
    public static boolean isSuperColumn(String tplCategory, String javaField) {
        return StringUtils.equalsAnyIgnoreCase(javaField, GenConstants.BASE_ENTITY);
    }

    /**
     * 判断是否从Online设计导入
     */
    public boolean isFromDesign() {
        return "design".equals(this.sourceType);
    }

    /**
     * 判断是否启用Online表单
     */
    public boolean isOnlineFormEnabled() {
        return "1".equals(this.isOnlineForm);
    }
}
