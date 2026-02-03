package com.nexus.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 流程业务单对象 wf_custom_form
 *
 * @author nbacheng
 * @date 2023-10-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_custom_form")
public class WfCustomForm extends BaseEntity {

    private static final long serialVersionUID=1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;
    /**
     * 业务表单名称
     */
    private String businessName;
    /**
     * 业务服务名称
     */
    private String businessService;
    /**
     * 
     */
    private String flowName;
    /**
     * 关联流程发布主键
     */
    private String deployId;
    /**
     * 前端路由地址
     */
    private String routeName;
    /**
     * 组件注入方法
     */
    private String component;
    /**
     * 关联业务主表id
     */
    private Long tableId;    

}
