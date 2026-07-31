package com.nexus.erp.fin.fa.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.nexus.erp.fin.fa.domain.FaDisposal;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 资产处置 VO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FaDisposal.class)
@EqualsAndHashCode(callSuper = true)
public class FaDisposalVo extends FaDisposal {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 资产编号 */
    private String assetNo;

    /** 资产名称 */
    private String assetName;
}
