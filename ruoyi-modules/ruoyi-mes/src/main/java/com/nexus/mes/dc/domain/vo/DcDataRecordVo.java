package com.nexus.mes.dc.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.dc.domain.DcDataRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES采集数据记录视图对象 dc_data_record
 *
 * <p>属于MES 数据采集模块，数据记录视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = DcDataRecord.class)
public class DcDataRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @ExcelProperty("记录ID")
    private Long recordId;

    /** 采集点ID */
    private Long pointId;

    /** 采集点编码 */
    @ExcelProperty("采集点编码")
    private String pointCode;

    /** IoT属性编码（source=IoT时填充） */
    @ExcelProperty("IoT属性编码")
    private String propertyCode;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联工序记录ID */
    private Long stepRecordId;

    /** 数值 */
    @ExcelProperty("数值")
    private BigDecimal numericValue;

    /** 文本值 */
    @ExcelProperty("文本值")
    private String textValue;

    /** 采集时间 */
    @ExcelProperty("采集时间")
    private Date collectTime;

    /** 采集人 */
    private Long collectBy;

    /** 来源 */
    @ExcelProperty("来源")
    private String source;

    /** 备注 */
    private String remark;
}
