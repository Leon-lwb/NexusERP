package com.nexus.erp.prd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 生产工单资源分配对象 prd_work_order_resource
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_work_order_resource")
public class PrdWorkOrderResource extends TenantEntity {

    /**
     * 资源分配ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 工单ID
     */
    @ExcelProperty("工单ID")
    private Long woId;

    /**
     * 资源类型：WORKSHOP-车间, WORKSTATION-工作站, EQUIPMENT-设备, TEAM-班组, PRODUCTION_LINE-生产线
     */
    @ExcelProperty("资源类型")
    private String resourceType;

    /**
     * 资源ID
     */
    @ExcelProperty("资源ID")
    private Long resourceId;

    /**
     * 资源编码
     */
    @ExcelProperty("资源编码")
    private String resourceCode;

    /**
     * 资源名称
     */
    @ExcelProperty("资源名称")
    private String resourceName;

    /**
     * 资源顺序/工序顺序
     */
    @ExcelProperty("顺序")
    private Integer seq;

    /**
     * 父资源ID（如工作站所属车间ID）
     */
    @ExcelProperty("父资源ID")
    private Long parentId;

    /**
     * 父资源类型
     */
    @ExcelProperty("父资源类型")
    private String parentType;

    /**
     * 计划开始时间
     */
    @ExcelProperty("计划开始时间")
    private Date planStartTime;

    /**
     * 计划结束时间
     */
    @ExcelProperty("计划结束时间")
    private Date planEndTime;

    /**
     * 实际开始时间
     */
    @ExcelProperty("实际开始时间")
    private Date actualStartTime;

    /**
     * 实际结束时间
     */
    @ExcelProperty("实际结束时间")
    private Date actualEndTime;

    /**
     * 状态：PENDING-待执行, PROCESSING-执行中, COMPLETED-已完成
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

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
