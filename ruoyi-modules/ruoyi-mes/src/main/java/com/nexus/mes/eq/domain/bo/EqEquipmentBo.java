package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqEquipment;

import java.util.Date;

/**
 * MES设备台账业务对象 eq_equipment
 *
 * <p>属于MES 设备管理模块，设备业务对象（新增/编辑入参）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqEquipment.class, reverseConvertGenerate = false)
public class EqEquipmentBo extends BaseEntity {

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码 */
    @NotBlank(message = "设备编码不能为空")
    private String equipmentCode;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    private String equipmentName;

    /** 设备类型 */
    private String equipmentType;

    /** 型号 */
    private String model;

    /** 制造商 */
    private String manufacturer;

    /** 出厂编号 */
    private String serialNo;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    private String workcenterCode;

    /** 安装位置 */
    private String location;

    /** 购置日期 */
    private Date purchaseDate;

    /** 保修到期 */
    private Date warrantyDate;

    /** 状态 */
    private String status;

    /** 点检项目及标准(JSON) */
    private String inspectionItems;

    /** 最近一次点检结果(JSON) */
    private String lastInspection;

    /** 关联IoT设备ID */
    private Long iotDeviceId;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
