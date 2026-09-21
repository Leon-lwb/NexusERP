package com.nexus.mes.dhr.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DHR 批记录查询业务对象。
 *
 * <p>列表/导出共用：dhrNo/workOrderNo/materialCode/lotNo/sn/status 为等值或模糊条件，
 * beginTime/endTime 为 create_time 时间窗（闭区间）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DhrRecordBo extends BaseEntity {

    /** DHR 主键（详情/操作定位） */
    private Long dhrId;

    /** DHR 编号（模糊） */
    private String dhrNo;

    /** 工单ID */
    private Long workOrderId;

    /** 工单编号（模糊） */
    private String workOrderNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（模糊） */
    private String materialCode;

    /** 批次号（模糊） */
    private String lotNo;

    /** SN（模糊） */
    private String sn;

    /** 状态（等值） */
    private String status;

    /** 创建时间起（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd） */
    private String beginTime;

    /** 创建时间止 */
    private String endTime;

    /** 批量删除ID集合 */
    private Long[] ids;
}
