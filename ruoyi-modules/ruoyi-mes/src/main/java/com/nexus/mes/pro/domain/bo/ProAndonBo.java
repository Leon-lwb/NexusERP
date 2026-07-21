package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProAndon;

/**
 * MES安灯呼叫业务对象 pro_andon
 * <p>属于 MES 生产管理模块，承载 安灯告警 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProAndon.class, reverseConvertGenerate = false)
public class ProAndonBo extends BaseEntity {

    /** 安灯ID */
    private Long andonId;

    /** 安灯编号 */
    private String andonNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联流转卡工序记录ID */
    private Long stepRecordId;

    /** 呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER */
    @NotBlank(message = "呼叫类型不能为空")
    private String callType;

    /** 呼叫原因 */
    private String callReason;

    /** 安灯状态 */
    private String andonStatus;

    /** 来源 */
    private String source;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
