package com.nexus.workflow.domain.dto;

import lombok.Data;

/**
 * 流程元数据信息对象
 * <p>描述流程的创建人、描述与关联表单信息，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/6/21 9:16
 */
@Data
public class WfMetaInfoDto {

    /**
     * 创建者（username）
     */
    private String createUser;

    /**
     * 流程描述
     */
    private String description;
    /**
     * 表单类型
     */
    private Integer formType;
    /**
     * 表单编号
     */
    private Long formId;

}
