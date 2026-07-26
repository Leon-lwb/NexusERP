package com.nexus.mes.sop.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.sop.domain.SopDocument;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES SOP文档视图对象 sop_document
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SopDocument.class)
public class SopDocumentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SOP ID */
    @ExcelProperty("SOP ID")
    private Long sopId;

    /** SOP编码 */
    @ExcelProperty("SOP编码")
    private String sopCode;

    /** SOP名称 */
    @ExcelProperty("SOP名称")
    private String sopName;

    /** SOP类型: PRODUCTION/INSPECTION/SAFETY/SETUP/CLEAN */
    @ExcelProperty("SOP类型")
    private String sopType;

    /** 当前发布版本号 */
    @ExcelProperty("当前版本")
    private String currentVersion;

    /** 状态: DRAFT/IN_REVIEW/PUBLISHED/ARCHIVED */
    @ExcelProperty("状态")
    private String status;

    /** 当前发布文件OSS ID(私有桶存ID) */
    private String fileOssId;

    /** 文件访问地址(由 fileOssId 翻译; 私有桶返回临时签名URL) */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "fileOssId")
    private String fileUrl;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    @ExcelProperty("文件类型")
    private String fileType;

    /** 说明 */
    private String description;

    /** 审批人ID */
    private Long approvalBy;

    /** 审批人姓名(由 approvalBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "approvalBy")
    private String approvalByName;

    /** 审批时间 */
    private Date approvalTime;

    /** 审批意见 */
    private String approvalRemark;

    /** 归档原因 */
    private String archiveReason;

    /** 创建者ID */
    private Long createBy;

    /** 创建者姓名(由 createBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}
