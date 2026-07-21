package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProSn;

/**
 * MES SN序列号业务对象 pro_sn
 * <p>属于 MES 生产管理模块，承载 SN序列号追溯 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProSn.class, reverseConvertGenerate = false)
public class ProSnBo extends BaseEntity {

    /** SN ID */
    private Long snId;

    /** SN序列号编码 */
    private String snCode;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 当前工序序号 */
    private Integer currentStepSeq;

    /** SN状态 */
    private String snStatus;

    /** 父SN ID */
    private Long parentSnId;

    /** 来源 */
    private String source;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
