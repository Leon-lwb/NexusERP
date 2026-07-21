package com.nexus.mes.dc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.dc.domain.DcCollectionPoint;

import java.io.Serial;
import java.io.Serializable;

/**
 * MES数据采集点视图对象 dc_collection_point
 *
 * <p>属于MES 数据采集模块，采集点视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = DcCollectionPoint.class)
public class DcCollectionPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 采集点ID */
    @ExcelProperty("采集点ID")
    private Long pointId;

    /** 采集点编码 */
    @ExcelProperty("采集点编码")
    private String pointCode;

    /** 采集点名称 */
    @ExcelProperty("采集点名称")
    private String pointName;

    /** 采集点类型 */
    @ExcelProperty("采集点类型")
    private String pointType;

    /** 关联设备ID */
    private Long equipmentId;

    /** 关联工序ID */
    private Long processId;

    /** 数据类型 */
    @ExcelProperty("数据类型")
    private String dataType;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 采集频率(秒) */
    @ExcelProperty("采集频率(秒)")
    private Integer frequency;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    private String remark;
}
