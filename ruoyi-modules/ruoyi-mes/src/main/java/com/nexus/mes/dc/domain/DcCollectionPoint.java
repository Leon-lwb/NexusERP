package com.nexus.mes.dc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES数据采集点对象 dc_collection_point
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dc_collection_point")
public class DcCollectionPoint extends TenantEntity {

    /** 采集点ID(主键) */
    @TableId(value = "point_id")
    private Long pointId;

    /** 采集点编码 */
    private String pointCode;

    /** 采集点名称 */
    private String pointName;

    /** 采集点类型: MANUAL/AUTO */
    private String pointType;

    /** 关联设备ID */
    private Long equipmentId;

    /** 关联工序ID */
    private Long processId;

    /** 数据类型: NUMERIC/TEXT/ENUM */
    private String dataType;

    /** 单位 */
    private String unit;

    /** 采集频率(秒) */
    private Integer frequency;

    /** 状态: ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
