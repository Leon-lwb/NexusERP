package com.nexus.mes.sop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * SOP查阅记录对象 sop_view_log
 * <p>signature_id 预留 (P0-6 ESignatureService 接入后回填, 本周仅记录查阅)</p>
 * <p>查看器打开时登记: viewer/view_time/wo/process/IP/UA</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sop_view_log")
public class SopViewLog extends TenantEntity {

    /** 查阅记录ID(主键) */
    @TableId(value = "log_id")
    private Long logId;

    /** SOP ID */
    private Long sopId;

    /** 查阅的版本号 */
    private String versionNo;

    /** 查阅人ID */
    private Long viewerId;

    /** 查阅人姓名(冗余) */
    private String viewerName;

    /** 查阅时间 */
    private Date viewTime;

    /** 关联工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 关联工序ID */
    private Long processId;

    /** 工序名称(冗余) */
    private String processName;

    /** 工位ID */
    private Long workstationId;

    /** 电子签名记录ID(预留,P0-6接入) */
    private Long signatureId;

    /** IP地址 */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
