package com.nexus.erp.fin.gl.domain.bo;

import com.nexus.erp.fin.gl.domain.GlPeriod;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 会计期间业务对象 gl_period
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = GlPeriod.class, reverseConvertGenerate = false)
public class GlPeriodBo extends BaseEntity {

    /** 期间ID */
    private Long periodId;

    /** 会计年度 */
    private Integer fiscalYear;

    /** 期间号 */
    private Integer periodNo;

    /** 期间名称 */
    private String periodName;

    /** 开始日期 */
    private Date startDate;

    /** 结束日期 */
    private Date endDate;

    /** 状态 */
    private String status;
}
