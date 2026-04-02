package com.nexus.core.prd.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 生产工单资源分配对象 prd_work_order_resource
 *
 * @author nexus
 * @date 2025-12-16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("prd_work_order_resource")
public class PrdWorkOrderResource extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 资源分配ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工单ID
     */
    @Excel(name = "工单ID")
    private Long woId;

    /**
     * 资源类型：WORKSHOP-车间, WORKSTATION-工作站, EQUIPMENT-设备, TEAM-班组, PRODUCTION_LINE-生产线
     */
    @Excel(name = "资源类型")
    private String resourceType;

    /**
     * 资源ID
     */
    @Excel(name = "资源ID")
    private Long resourceId;

    /**
     * 资源编码
     */
    @Excel(name = "资源编码")
    private String resourceCode;

    /**
     * 资源名称
     */
    @Excel(name = "资源名称")
    private String resourceName;

    /**
     * 资源顺序/工序顺序
     */
    @Excel(name = "顺序")
    private Integer seq;

    /**
     * 父资源ID（如工作站所属车间ID）
     */
    @Excel(name = "父资源ID")
    private Long parentId;

    /**
     * 父资源类型
     */
    @Excel(name = "父资源类型")
    private String parentType;

    /**
     * 计划开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "计划开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date planStartTime;

    /**
     * 计划结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "计划结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date planEndTime;

    /**
     * 实际开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "实际开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date actualStartTime;

    /**
     * 实际结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "实际结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date actualEndTime;

    /**
     * 状态：PENDING-待执行, PROCESSING-执行中, COMPLETED-已完成
     */
    @Excel(name = "状态")
    private String status;

    /**
     * 备注
     */
    @Excel(name = "备注")
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    private Integer deleted;

    // 资源类型常量
    public static final String RESOURCE_TYPE_WORKSHOP = "WORKSHOP";
    public static final String RESOURCE_TYPE_WORKSTATION = "WORKSTATION";
    public static final String RESOURCE_TYPE_EQUIPMENT = "EQUIPMENT";
    public static final String RESOURCE_TYPE_TEAM = "TEAM";
    public static final String RESOURCE_TYPE_PRODUCTION_LINE = "PRODUCTION_LINE";

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
}
