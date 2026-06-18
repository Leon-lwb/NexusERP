package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES SN序列号追溯对象 pro_sn
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_sn")
public class ProSn extends TenantEntity {

    /** SN ID(主键) */
    @TableId(value = "sn_id")
    private Long snId;

    /** SN序列号编码 */
    private String snCode;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号(冗余) */
    private String cardNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 当前工序序号 */
    private Integer currentStepSeq;

    /** SN状态: IN_PRODUCTION/COMPLETED/SCRAPPED/SHIPPED */
    private String snStatus;

    /** 父SN ID(组装追溯, 组件SN指向成品SN) */
    private Long parentSnId;

    /** 父SN编码(冗余) */
    private String parentSnCode;

    /** 来源: MANUAL/BATCH */
    private String source;

    /** 生产日期 */
    private Date productionDate;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
