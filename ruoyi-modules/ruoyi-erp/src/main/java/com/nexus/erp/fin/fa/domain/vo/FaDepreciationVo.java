package com.nexus.erp.fin.fa.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.nexus.erp.fin.fa.domain.FaDepreciation;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 折旧记录 VO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FaDepreciation.class)
@EqualsAndHashCode(callSuper = true)
public class FaDepreciationVo extends FaDepreciation {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 资产编号 */
    private String assetNo;

    /** 资产名称 */
    private String assetName;

    /** 期间名称(如 2026-07) */
    private String periodName;
}
