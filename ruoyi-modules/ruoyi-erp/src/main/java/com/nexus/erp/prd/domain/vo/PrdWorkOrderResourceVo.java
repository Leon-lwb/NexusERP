package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.prd.domain.PrdWorkOrderResource;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 生产工单资源分配视图对象 prd_work_order_resource
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdWorkOrderResource.class)
public class PrdWorkOrderResourceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资源分配ID
     */
    @ExcelProperty("资源分配ID")
    private Long id;

    /**
     * 工单ID
     */
    @ExcelProperty("工单ID")
    private Long woId;

    /**
     * 资源类型
     */
    @ExcelProperty("资源类型")
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
     * 顺序
     */
    @ExcelProperty("顺序")
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
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    private String remark;
}
