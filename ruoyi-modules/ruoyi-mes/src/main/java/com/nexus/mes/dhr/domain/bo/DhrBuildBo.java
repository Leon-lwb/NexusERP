package com.nexus.mes.dhr.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DHR 批记录构建业务对象（POST /mes/dhr/build）。
 *
 * @author Nexus
 */
@Data
public class DhrBuildBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** MES 工单ID（pro_work_order.mes_wo_id），必填 */
    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    /** 批次号（可空；为空表示按整工单聚合） */
    private String lotNo;

    /** SN 序列号（可空；为空表示按整工单聚合） */
    private String sn;

    /** 备注（可空） */
    private String remark;
}
