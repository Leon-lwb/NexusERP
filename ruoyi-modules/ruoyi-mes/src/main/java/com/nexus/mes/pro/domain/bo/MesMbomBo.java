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
    /** MBOMID */
    private Long mbomId;

    @NotNull(message = "关联ERP BOM ID不能为空")
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;
    /** MBOMVersion */
    private String mbomVersion;
    /** MBOMStatus */
    private String mbomStatus;
    /** remark */
    private String remark;

    /** MBOM明细行列表(随MBOM一起保存:先删后插) */
    private List<MesMbomLineBo> lines;
}
