package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外工程文件对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("erp_subcontract_doc")
public class PrdSubcontractDoc extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 文件ID */
    Long docId;
    /** 物料ID */
    Long materialId;
    /** 委外订单ID(NULL=物料级工程文件库) */
    Long subcontractId;
    /** 文件类型: GERBER/PICK_PLACE/BOM_FILE/STENCIL/PROCESS_FILE/OTHER */
    String docType;
    /** 文件名称 */
    String docName;
    /** OSS文件ID(sys_oss.oss_id) */
    Long fileOssId;
    /** 文件版本 */
    String docVersion;
    /** 创建部门 */
    Long createDept;
    /** 创建者 */
    Long createBy;
    /** 创建时间 */
    Date createTime;
    /** 更新者 */
    Long updateBy;
    /** 更新时间 */
    Date updateTime;
    /** 备注 */
    String remark;
}
