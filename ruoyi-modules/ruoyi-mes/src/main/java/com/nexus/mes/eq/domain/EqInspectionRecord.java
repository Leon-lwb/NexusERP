package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES设备点检记录对象 eq_inspection_record
 * <p>
 * 每次点检执行的历史留痕(eq_equipment.last_inspection 仅保留最近一次, 无历史)。
 * PC 端与工位终端(nexus-station, M3 设备通道 C15)提交均落本表,
 * 同时同步刷新 eq_equipment.last_inspection。
 * 当日是否已检 = 本表该设备 inspect_time >= 当日 0 点的最新一条。
 * </p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_inspection_record")
public class EqInspectionRecord extends TenantEntity {

    /** 记录ID(主键) */
    @TableId(value = "record_id")
    private Long recordId;

    /** 设备ID */
    private Long equipmentId;

    /** 工位编码(工位终端提交时留痕, PC 端为空) */
    private String stationCode;

    /** 点检人ID */
    private Long inspectorId;

    /** 点检人姓名 */
    private String inspectorName;

    /** 点检人工号(设备通道免登录, 操作员留痕) */
    private String operatorCode;

    /** 点检时间 */
    private Date inspectTime;

    /** 逐项点检明细 JSON: [{item,standard,actual,pass}] */
    private String results;

    /** 总判定: Y合格/N不合格 */
    private String overallPass;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
