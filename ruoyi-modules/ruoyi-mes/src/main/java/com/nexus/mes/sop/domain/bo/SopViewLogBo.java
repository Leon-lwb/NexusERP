package com.nexus.mes.sop.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.sop.domain.SopViewLog;

/**
 * SOP查阅记录业务对象 sop_view_log
 * <p>主要为查询条件(查阅登记由 recordView 内部写入, 不走 BO 校验)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SopViewLog.class, reverseConvertGenerate = false)
public class SopViewLogBo extends BaseEntity {

    /** 查阅记录ID */
    private Long logId;

    /** SOP ID */
    private Long sopId;

    /** 查阅的版本号 */
    private String versionNo;

    /** 查阅人ID */
    private Long viewerId;

    /** 关联工单ID */
    private Long woId;

    /** 关联工序ID */
    private Long processId;

    /** 工位ID */
    private Long workstationId;

    /** 批量删除ID集合 */
    private Long[] ids;
}
