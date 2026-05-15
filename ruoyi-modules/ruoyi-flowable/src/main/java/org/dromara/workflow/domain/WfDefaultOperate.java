package org.dromara.workflow.domain;

import com.baomidou.mybatisplus.annotation.*;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 流程默认操作对象 wf_default_operate
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_default_operate")
public class WfDefaultOperate extends BaseEntity {

    private static final long serialVersionUID=1L;

    /**
     * 流程默认操作主键
     */
    @TableId(value = "id")
    private Long id;
    /**
     * 操作类型
     */
    private String opeType;
    /**
     * 操作名称
     */
    private String opeName;
    /**
     * 是否启用1-启用0-关闭默认
     */
    private String isEnable;
    /**
     * 排序
     */
    private Long sort;

}
