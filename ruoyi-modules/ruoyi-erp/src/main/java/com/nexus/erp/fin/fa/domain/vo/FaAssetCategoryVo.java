package com.nexus.erp.fin.fa.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 固定资产类别 VO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FaAssetCategory.class)
@EqualsAndHashCode(callSuper = true)
public class FaAssetCategoryVo extends FaAssetCategory {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 折旧费用科目名称 */
    private String debitAccountName;

    /** 累计折旧科目名称 */
    private String creditAccountName;

    /** 处置损失科目名称 */
    private String disposalLossAccountName;

    /** 处置收益科目名称 */
    private String disposalGainAccountName;
}
