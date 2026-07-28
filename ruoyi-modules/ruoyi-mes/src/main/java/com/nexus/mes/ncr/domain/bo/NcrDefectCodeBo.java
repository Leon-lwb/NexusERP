package com.nexus.mes.ncr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.ncr.domain.NcrDefectCode;

/**
 * MES NCR缺陷代码业务对象 ncr_defect_code
 * <p>属于 MES NCR 不合格品管理模块，承载 缺陷代码 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = NcrDefectCode.class, reverseConvertGenerate = false)
public class NcrDefectCodeBo extends BaseEntity {

    /** 代码ID */
    private Long codeId;

    /** 父级ID(查询用) */
    private Long parentId;

    /** 缺陷编码(查询用) */
    @NotBlank(message = "缺陷编码不能为空")
    private String code;

    /** 缺陷名称(查询用) */
    @NotBlank(message = "缺陷名称不能为空")
    private String name;

    /** 层级 */
    private Integer level;

    /** 是否启用(查询用) */
    private String enabled;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}