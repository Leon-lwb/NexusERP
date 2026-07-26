package com.nexus.mes.sop.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.sop.domain.SopVersion;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * SOP版本视图对象 sop_version
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SopVersion.class)
public class SopVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    @ExcelProperty("版本ID")
    private Long versionId;

    /** SOP ID */
    private Long sopId;

    /** 版本号 */
    @ExcelProperty("版本号")
    private String versionNo;

    /** 该版本文件OSS ID */
    private String fileOssId;

    /** 文件访问地址(由 fileOssId 翻译) */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "fileOssId")
    private String fileUrl;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    @ExcelProperty("文件类型")
    private String fileType;

    /** 变更说明 */
    @ExcelProperty("变更说明")
    private String changeLog;

    /** 发布时间 */
    @ExcelProperty("发布时间")
    private Date publishedTime;

    /** 发布人ID */
    private Long publishedBy;

    /** 发布人姓名(由 publishedBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "publishedBy")
    private String publishedByName;

    /** 状态: PENDING/CURRENT/HISTORICAL */
    @ExcelProperty("版本状态")
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}
