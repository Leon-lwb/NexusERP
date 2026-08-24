package com.nexus.erp.sch.domain;

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
 * 外勤打卡记录对象 sch_outdoor_punch
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_outdoor_punch")
public class SchOutdoorPunch extends TenantEntity {

    /**
     * 外勤打卡ID
     */
    @TableId(value = "punch_id")
    private Long punchId;

    /**
     * 工人ID
     */
    private Long workerId;

    /**
     * 工人姓名
     */
    private String workerName;

    /**
     * 外勤打卡时间
     */
    private Date punchTime;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 打卡定位地址
     */
    private String address;

    /**
     * 拜访客户名称(选填)
     */
    private String customerName;

    /**
     * 外勤事由
     */
    private String reason;

    /**
     * 现场照片ossId(MinIO存证)
     */
    private Long photoOssId;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
