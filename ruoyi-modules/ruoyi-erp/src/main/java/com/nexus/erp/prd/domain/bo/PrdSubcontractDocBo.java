package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontractDoc;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外工程文件业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdSubcontractDoc.class, reverseConvertGenerate = false)
public class PrdSubcontractDocBo extends BaseEntity {

    /** 文件ID */
    private Long docId;
    /** 物料ID */
    private Long materialId;
    /** 委外订单ID(NULL=物料级工程文件库) */
    private Long subcontractId;
    /** 文件类型: GERBER/PICK_PLACE/BOM_FILE/STENCIL/PROCESS_FILE/OTHER */
    private String docType;
    /** 文件名称 */
    private String docName;
    /** OSS文件ID(sys_oss.oss_id) */
    private Long fileOssId;
    /** 文件版本 */
    private String docVersion;
    /** 创建部门 */
    private Long createDept;
    /** 创建者 */
    private Long createBy;
    /** 创建时间 */
    private Date createTime;
    /** 更新者 */
    private Long updateBy;
    /** 更新时间 */
    private Date updateTime;
    /** 备注 */
    private String remark;
}
