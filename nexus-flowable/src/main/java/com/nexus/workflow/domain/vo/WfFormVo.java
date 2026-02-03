package com.nexus.workflow.domain.vo;

import com.nexus.workflow.domain.WfForm;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程分类视图对象
 *
 * @author nbacheng
 * @createTime 2022/3/7 22:07
 */
@Data
@AutoMapper(target = WfForm.class)
public class WfFormVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表单主键
     */
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
     * 关联生成的数据库表名
     */
    private String tableName;

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
    //private String tenantId;
}
