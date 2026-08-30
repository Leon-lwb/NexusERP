package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PDA 临时盘点建单入参
 * <p>
 * locationCode 指定时仅盘点该库位; dynamicDays > 0 时仅盘点近 N 天有库存流水的库位(动碰盘)。
 */
@Data
public class PdaStocktakingCreateBo {

    /** 仓库编码 */
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;

    /** 库位编码(可选, 限定库位范围) */
    private String locationCode;

    /** 动碰盘天数(可选, 仅生成近 N 天有流水的库位明细) */
    private Integer dynamicDays;

    /** 盘点模式 OPEN/BLIND, 缺省 OPEN */
    private String countMode;
}
