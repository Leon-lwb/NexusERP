package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdWorkOrderResource;

import java.util.Date;

/**
 * 生产工单资源分配业务对象 prd_work_order_resource
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdWorkOrderResource.class, reverseConvertGenerate = false)
public class PrdWorkOrderResourceBo extends BaseEntity {

    /**
     * 资源分配ID
     */
    private Long id;

    /**
     * 工单ID
     */
    private Long woId;

    /**
     * 资源类型
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private Long resourceId;

    /**
     * 资源编码
     */
    private String resourceCode;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 资源顺序
     */
    private Integer seq;

    /**
     * 父资源ID
     */
    private Long parentId;

    /**
     * 父资源类型
     */
    private String parentType;

    /**
     * 计划开始时间
     */
    private Date planStartTime;

    /**
     * 计划结束时间
     */
    private Date planEndTime;

    /**
     * 实际开始时间
     */
    private Date actualStartTime;

    /**
     * 实际结束时间
     */
    private Date actualEndTime;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
