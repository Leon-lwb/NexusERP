package org.dromara.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程表单对象 wf_form
 *
 * @author nbacheng
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form")
public class WfForm extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表单主键
     */
    @TableId(value = "form_id")
    private Long formId;

    /**
     * 表单名称
     */
    private String formName;

    /**
     * 表单内容
     */
    private String content;

    /**
     * 备注
     */
    private String remark;
    /**
     * 是否保存数据库
     */
    private Boolean db;
    /**
     * 删除标志
     */
    private String delFlag;
    /**
     * 租户编号
     */
    private String tenantId;
    /**
     * 关联生成的数据库表名
     */
    private String tableName;
}
