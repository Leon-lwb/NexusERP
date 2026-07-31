package com.nexus.erp.fin.fa.domain.bo;

import com.nexus.erp.fin.fa.domain.FaAsset;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 固定资产卡片 BO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
@AutoMapper(target = FaAsset.class)
public class FaAssetBo {

    /** 资产ID(新增为空) */
    private Long assetId;

    /** 资产编号(新增自动生成) */
    private String assetNo;

    /** 资产名称 */
    @NotBlank(message = "资产名称不能为空")
    private String assetName;

    /** 类别ID */
    @NotNull(message = "资产类别不能为空")
    private Long categoryId;

    /** 原值 */
    @NotNull(message = "原值不能为空")
    private BigDecimal originalValue;

    /** 净残值(空=原值×类别残值率) */
    private BigDecimal salvageValue;

    /** 使用年限(月,空=随类别) */
    private Integer usefulLife;

    /** 折旧方法(空=随类别) */
    private String depreciationMethod;

    /** 购置日期 */
    private Date acquisitionDate;

    /** 启用日期(开始计提,空=购置日) */
    private Date inUseDate;

    /** 存放地点 */
    private String location;

    /** 状态 IN_USE-使用中 CWIP-在建 DISPOSED-已处置 */
    private String status;

    /** 查询条件:状态 */
    private String statusQuery;

    /** 查询条件:资产名称关键字 */
    private String keyword;
}
