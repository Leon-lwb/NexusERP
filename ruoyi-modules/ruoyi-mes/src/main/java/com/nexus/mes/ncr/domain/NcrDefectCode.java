package com.nexus.mes.ncr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES NCR缺陷代码对象 ncr_defect_code
 * <p>属于 MES NCR 不合格品管理模块，表达 缺陷代码 的领域模型，支持树形层级。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ncr_defect_code")
public class NcrDefectCode extends TenantEntity {

    /** 代码ID(主键) */
    @TableId(value = "code_id")
    private Long codeId;

    /** 父级ID */
    private Long parentId;

    /** 缺陷编码 */
    private String code;

    /** 缺陷名称 */
    private String name;

    /** 层级(1:大类,2:中类,3:细类) */
    private Integer level;

    /** 是否启用(Y/N) */
    private String enabled;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}