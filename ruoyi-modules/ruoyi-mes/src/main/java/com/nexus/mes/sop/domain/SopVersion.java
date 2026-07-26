package com.nexus.mes.sop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * SOP版本历史对象 sop_version
 * <p>状态机: PENDING(新建未发布) -> CURRENT(发布中) -> HISTORICAL(被新版本取代)</p>
 * <p>doPublish 时: 最新 PENDING -> CURRENT, 旧 CURRENT -> HISTORICAL</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sop_version")
public class SopVersion extends TenantEntity {

    /** 版本ID(主键) */
    @TableId(value = "version_id")
    private Long versionId;

    /** SOP ID */
    private Long sopId;

    /** 版本号(如1.0/1.1) */
    private String versionNo;

    /** 该版本文件OSS ID(对齐 EqPatrolRecord.photoOssId 用 String) */
    private String fileOssId;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    private String fileType;

    /** 变更说明 */
    private String changeLog;

    /** 发布时间 */
    private Date publishedTime;

    /** 发布人ID */
    private Long publishedBy;

    /** 状态: PENDING/CURRENT/HISTORICAL */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
