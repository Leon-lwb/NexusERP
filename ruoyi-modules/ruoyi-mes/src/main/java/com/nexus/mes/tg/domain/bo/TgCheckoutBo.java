package com.nexus.mes.tg.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工装领用入参 BO
 * <p>配合 TgToolingController.checkout 使用,承载领用时需记录的工单/工位/操作员信息。</p>
 *
 * @author Nexus
 */
@Data
public class TgCheckoutBo {

    /** 操作员ID(领用人) */
    @NotNull(message = "操作员ID不能为空")
    private Long operatorId;

    /** 操作员姓名(冗余) */
    private String operatorName;

    /** 工单ID(可选) */
    private Long woId;

    /** 工单编号(冗余,可选) */
    private String woNo;

    /** 工位ID(可选) */
    private Long workstationId;

    /** 工位名称(冗余,可选) */
    private String workstationName;

    /** 备注 */
    private String remark;
}
