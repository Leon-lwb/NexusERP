package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqPatrolRoute;

/**
 * 属于MES 设备管理模块，巡检路线业务对象（新增/编辑入参）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqPatrolRoute.class, reverseConvertGenerate = false)
public class EqPatrolRouteBo extends BaseEntity {
    private Long routeId;
    @NotBlank(message = "路线名称不能为空")
    private String routeName;
    private String description;
    private String equipmentIds;
    private String checkItems;
    private String frequency;
    private String status;
    private String remark;
    private Long[] ids;
}
