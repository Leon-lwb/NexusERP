package com.nexus.mes.dc.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.dc.domain.DcDataRecord;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES采集数据记录业务对象 dc_data_record
 *
 * <p>属于MES 数据采集模块，数据记录业务对象（新增/编辑入参）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = DcDataRecord.class, reverseConvertGenerate = false)
public class DcDataRecordBo extends BaseEntity {

    /** 记录ID */
    private Long recordId;

    /** 采集点ID */
    @NotNull(message = "采集点ID不能为空")
    private Long pointId;

    /** IoT属性编码（source=IoT时填充，用于查询过滤） */
    private String propertyCode;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联工序记录ID */
    private Long stepRecordId;

    /** 数值 */
    private BigDecimal numericValue;

    /** 文本值 */
    private String textValue;

    /** 采集时间 */
    @NotNull(message = "采集时间不能为空")
    private Date collectTime;

    /** 来源 */
    private String source;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
