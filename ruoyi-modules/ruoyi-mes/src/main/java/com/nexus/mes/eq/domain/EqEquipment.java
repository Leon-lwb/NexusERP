package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES设备台账对象 eq_equipment
 *
 * <p>属于MES 设备管理模块，设备持久化实体。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_equipment")
public class EqEquipment extends TenantEntity {

    /** 设备ID(主键) */
    @TableId(value = "equipment_id")
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备类型 */
    private String equipmentType;

    /** 型号 */
    private String model;

    /** 制造商 */
    private String manufacturer;

    /** 出厂编号 */
    private String serialNo;

    /** 工作中心ID(ERP bd_workstation.workstation_id) */
    private Long workcenterId;

    /** 工作中心编码(冗余) */
    private String workcenterCode;

    /** 安装位置 */
    private String location;

    /** 购置日期 */
    private Date purchaseDate;

    /** 保修到期 */
    private Date warrantyDate;

    /** 状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED */
    private String status;

    /** 点检项目及标准(JSON) */
    private String inspectionItems;

    /** 最近一次点检结果(JSON) */
    private String lastInspection;

    /** 关联IoT设备ID(预留,无外键) */
    private Long iotDeviceId;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
