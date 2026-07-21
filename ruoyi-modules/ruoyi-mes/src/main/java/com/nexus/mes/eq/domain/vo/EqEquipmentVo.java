package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqEquipment;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES设备台账视图对象 eq_equipment
 *
 * <p>属于MES 设备管理模块，设备视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqEquipment.class)
public class EqEquipmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备ID */
    @ExcelProperty("设备ID")
    private Long equipmentId;

    /** 设备编码 */
    @ExcelProperty("设备编码")
    private String equipmentCode;

    /** 设备名称 */
    @ExcelProperty("设备名称")
    private String equipmentName;

    /** 设备类型 */
    @ExcelProperty("设备类型")
    private String equipmentType;

    /** 型号 */
    @ExcelProperty("型号")
    private String model;

    /** 制造商 */
    @ExcelProperty("制造商")
    private String manufacturer;

    /** 出厂编号 */
    @ExcelProperty("出厂编号")
    private String serialNo;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码 */
    @ExcelProperty("工作中心编码")
    private String workcenterCode;

    /** 安装位置 */
    @ExcelProperty("安装位置")
    private String location;

    /** 购置日期 */
    @ExcelProperty("购置日期")
    private Date purchaseDate;

    /** 保修到期 */
    @ExcelProperty("保修到期")
    private Date warrantyDate;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 点检项目及标准(JSON) */
    private String inspectionItems;

    /** 最近一次点检结果(JSON) */
    private String lastInspection;

    /** 关联IoT设备ID */
    private Long iotDeviceId;

    /** 备注 */
    private String remark;
}
