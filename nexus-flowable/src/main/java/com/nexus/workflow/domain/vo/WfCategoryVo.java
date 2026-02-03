package com.nexus.workflow.domain.vo;

import com.nexus.workflow.domain.WfCategory;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程分类视图对象 flow_category
 *
 * @author nbacheng
 * @date 2022-01-15
 */
@Data
@AutoMapper(target = WfCategory.class)
public class WfCategoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 分类编码
     */
    private String code;
    
    /**
     * 应用类型
     */
    private String appType;

    /**
     * 备注
     */
    private String remark;
    /**
     * 租户编号
     */
    private String tenantId;


}
