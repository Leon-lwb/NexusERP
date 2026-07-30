package com.nexus.erp.fin.gl.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 会计科目表 gl_account
 * <p>物化路径树(path_ids/path_names/level_num),统驭科目标识(is_recon/recon_type),仅末级(is_leaf=1)可过账。</p>
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("gl_account")
public class GlAccount extends TenantEntity {

    @TableId(value = "account_id")
    private Long accountId;

    /** 科目编码(如 1001) */
    private String accountCode;

    /** 科目名称 */
    private String accountName;

    /** 科目类型 ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE */
    private String accountType;

    /** 余额方向 DEBIT/CREDIT */
    private String balanceDir;

    /** 父科目ID(0=顶级) */
    private Long parentId;

    /** 层级(1=一级) */
    private Integer levelNum;

    /** 路径ID串(如 6005001,6005002) */
    private String pathIds;

    /** 路径名称串 */
    private String pathNames;

    /** 是否末级 0-否 1-是(仅末级可过账) */
    private String isLeaf;

    /** 是否统驭科目 0-否 1-是 */
    private String isRecon;

    /** 统驭类型 AR/AP/FA */
    private String reconType;

    /** 是否未清项管理 0-否 1-是 */
    private String isOpenItem;

    /** 币种(空=本位币) */
    private String currency;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
