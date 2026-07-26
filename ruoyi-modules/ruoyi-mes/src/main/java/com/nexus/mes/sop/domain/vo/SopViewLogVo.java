package com.nexus.mes.sop.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.sop.domain.SopViewLog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * SOP查阅记录视图对象 sop_view_log
 * <p>sopName 由 Service 联表填充。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SopViewLog.class)
public class SopViewLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 查阅记录ID */
    private Long logId;

    /** SOP ID */
    private Long sopId;

    /** SOP名称(Service 联表填充) */
    @ExcelProperty("SOP名称")
    private String sopName;

    /** 查阅的版本号 */
    @ExcelProperty("版本号")
    private String versionNo;

    /** 查阅人ID */
    private Long viewerId;

    /** 查阅人姓名(由 viewerId 翻译) */
    @ExcelProperty("查阅人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "viewerId")
    private String viewerName;

    /** 查阅时间 */
    @ExcelProperty("查阅时间")
    private Date viewTime;

    /** 关联工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 关联工序ID */
    private Long processId;

    /** 工序名称(冗余) */
    @ExcelProperty("工序")
    private String processName;

    /** 工位ID */
    private Long workstationId;

    /** 电子签名记录ID(预留,P0-6接入) */
    private Long signatureId;

    /** IP地址 */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 创建时间 */
    private Date createTime;
}
