package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.MesMbom;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MesMbom.class, reverseConvertGenerate = false)
public class MesMbomBo extends BaseEntity {
    private Long mbomId;

    @NotNull(message = "关联ERP BOM ID不能为空")
    private Long bomId;

    private Long routingId;
    private String mbomVersion;
    private String mbomStatus;
    private String remark;

    /** MBOM明细行列表(随MBOM一起保存:先删后插) */
    private List<MesMbomLineBo> lines;
}
