package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdWorkstation;

import java.math.BigDecimal;

/**
 * 工作站业务对象 bd_workstation
 *
 * @author nexus
 * @date 2025-11-19
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdWorkstation.class, reverseConvertGenerate = false)
public class BdWorkstationBo extends BaseEntity {

    /**
     * 工作站ID
     */
    private Long workstationId;

    /**
     * 工作站编码
     * <p>
     * 自动生成开关打开时由前端清空、后端兜底生成（避免预览消耗流水号），故不强制非空。
     */
    private String workstationCode;

    /**
     * 工作站名称
     */
    @NotBlank(message = "工作站名称不能为空")
    private String workstationName;

    /**
     * 所属车间ID
     */
    private Long workshopId;

    /**
     * 车间编码
     */
    private String workshopCode;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 工作中心描述
     */
    private String workstationDesc;

    /**
     * 设备ID
     */
    private Long equipmentId;

    /**
     * 标准操作工人数
     */
    private Long operatorCount;

    /**
     * 标准产能
     */
    private BigDecimal standardCapacity;

    /**
     * 状态
     */
    private Long status;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
