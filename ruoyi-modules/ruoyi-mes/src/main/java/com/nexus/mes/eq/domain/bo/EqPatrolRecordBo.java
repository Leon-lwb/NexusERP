package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqPatrolRecord;

/**
 * 属于MES 设备管理模块，巡检记录业务对象（新增/编辑入参）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqPatrolRecord.class, reverseConvertGenerate = false)
public class EqPatrolRecordBo extends BaseEntity {
    private Long recordId;
    private Long taskId;
    private Long routeId;
    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;
    private String checkItem;
    private String actualValue;
    private String result;
    private String photoOssId;
    private String remark;
    private Long[] ids;
}
