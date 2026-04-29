package org.dromara.workflow.domain;

import com.baomidou.mybatisplus.annotation.*;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 钉钉流程对象 wf_dd_flow
 *
 * @author nbacheng
 * @date 2023-11-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_dd_flow")
public class WfDdFlow extends BaseEntity {

    private static final long serialVersionUID=1L;

    /**
     * 钉钉流程主键
     */
    @TableId(value = "id")
    private Long id;
    /**
     * 流程名称
     */
    private String name;
    /**
     * 流程JSON数据
     */
    private String flowData;

}
