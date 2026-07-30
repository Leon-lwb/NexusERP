package com.nexus.erp.fin.gl.domain.bo;

import com.nexus.erp.fin.gl.domain.GlAccount;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * 会计科目业务对象 gl_account
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = GlAccount.class, reverseConvertGenerate = false)
public class GlAccountBo extends BaseEntity {

    /** 科目ID */
    private Long accountId;

    /** 科目编码 */
    @NotBlank(message = "科目编码不能为空")
    private String accountCode;

    /** 科目名称 */
    @NotBlank(message = "科目名称不能为空")
    private String accountName;

    /** 科目类型 */
    @NotBlank(message = "科目类型不能为空")
    private String accountType;

    /** 余额方向 */
    @NotBlank(message = "余额方向不能为空")
    private String balanceDir;

    /** 父科目ID(0=顶级) */
    private Long parentId;

    /** 是否末级 0-否 1-是 */
    private String isLeaf;

    /** 是否统驭科目 0-否 1-是 */
    private String isRecon;

    /** 统驭类型 AR/AP/FA */
    private String reconType;

    /** 是否未清项管理 0-否 1-是 */
    private String isOpenItem;

    /** 币种 */
    private String currency;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 排序 */
    private Integer sortOrder;
}
