package com.nexus.erp.fin.fa.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.nexus.erp.fin.fa.domain.FaAsset;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 固定资产卡片 VO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FaAsset.class)
@EqualsAndHashCode(callSuper = true)
public class FaAssetVo extends FaAsset {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 类别名称 */
    private String categoryName;

    /** 类别折旧方法 */
    private String categoryMethod;

    /** 折旧方法显示(资产自身或类别) */
    private String methodLabel;

    /** 剩余使用月数(直线法重算用) */
    private Long remainingMonths;

    /** 账面净值(冗余,便于前端显示) */
    private BigDecimal netValue;
}
