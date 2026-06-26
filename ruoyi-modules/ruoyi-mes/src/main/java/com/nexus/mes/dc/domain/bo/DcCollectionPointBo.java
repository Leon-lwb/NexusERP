package com.nexus.mes.dc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.dc.domain.DcCollectionPoint;

/**
 * MES数据采集点业务对象 dc_collection_point
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = DcCollectionPoint.class, reverseConvertGenerate = false)
public class DcCollectionPointBo extends BaseEntity {

    /** 采集点ID */
    private Long pointId;

    /** 采集点编码 */
    @NotBlank(message = "采集点编码不能为空")
    private String pointCode;

    /** 采集点名称 */
    @NotBlank(message = "采集点名称不能为空")
    private String pointName;

    /** 采集点类型: MANUAL/AUTO */
    @NotBlank(message = "采集点类型不能为空")
    private String pointType;

    /** 关联设备ID */
    private Long equipmentId;

    /** 关联IoT设备ID（AUTO类型必填） */
    private Long iotDeviceId;

    /** IoT属性编码（对应 iot_device_data.property_code） */
    private String iotPropertyCode;

    /** 关联工序ID */
    private Long processId;

    /** 数据类型: NUMERIC/TEXT/ENUM */
    private String dataType;

    /** 单位 */
    private String unit;

    /** 采集频率(秒) */
    private Integer frequency;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
