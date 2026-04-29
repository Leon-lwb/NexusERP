package org.dromara.workflow.domain;

import com.baomidou.mybatisplus.annotation.*;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 流程配置主对象 wf_flow_config
 *
 * @author nbacheng
 * @date 2023-11-19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_flow_config")
public class WfFlowConfig extends BaseEntity {

    private static final long serialVersionUID=1L;

    /**
     * 流程配置主表主键
     */
    @TableId(value = "id")
    private Long id;
    /**
     * 流程模型ID
     */
    private String modelId;
    /**
     * 节点Key
     */
    private String nodeKey;
    /**
     * 节点名称
     */
    private String nodeName;
    /**
     * 表单Key
     */
    private String formKey;
    /**
     * 应用类型
     */
    private String appType;

}
