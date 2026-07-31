package com.nexus.erp.fin.fa.domain.bo;

import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 固定资产类别 BO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@AutoMapper(target = FaAssetCategory.class)
public class FaAssetCategoryBo {

    /** 类别ID(新增为空) */
    private Long categoryId;

    /** 类别编码 */
    private String categoryCode;

    /** 类别名称 */
    @NotBlank(message = "类别名称不能为空")
    private String categoryName;

    /** 折旧方法 */
    @NotBlank(message = "折旧方法不能为空")
    private String depreciationMethod;

    /** 预计使用年限(月) */
    @NotNull(message = "使用年限不能为空")
    private Integer usefulLife;

    /** 净残值率(0-1) */
    private BigDecimal salvageValueRate;

    /** 折旧费用科目ID */
    private Long debitAccountId;

    /** 累计折旧科目ID */
    private Long creditAccountId;

    /** 处置损失科目ID */
    private Long disposalLossAccountId;

    /** 处置收益科目ID */
    private Long disposalGainAccountId;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 显示顺序 */
    private Integer sortOrder;
}
