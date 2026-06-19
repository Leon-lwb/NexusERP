package com.nexus.mes.dc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES采集数据记录对象 dc_data_record
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dc_data_record")
public class DcDataRecord extends TenantEntity {

    /** 记录ID(主键) */
    @TableId(value = "record_id")
    private Long recordId;

    /** 采集点ID */
    private Long pointId;

    /** 采集点编码(冗余) */
    private String pointCode;

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
    private Date collectTime;

    /** 采集人(手动时) */
    private Long collectBy;

    /** 来源: MANUAL/IoT */
    private String source;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
