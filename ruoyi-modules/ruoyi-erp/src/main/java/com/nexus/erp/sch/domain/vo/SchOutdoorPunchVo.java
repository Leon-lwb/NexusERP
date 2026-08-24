package com.nexus.erp.sch.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchOutdoorPunch;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外勤打卡记录视图对象 sch_outdoor_punch
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
@AutoMapper(target = SchOutdoorPunch.class)
public class SchOutdoorPunchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 外勤打卡ID */
    private Long punchId;

    /** 工人ID */
    private Long workerId;

    /** 工人姓名 */
    private String workerName;

    /** 外勤打卡时间 */
    private Date punchTime;

    /** 纬度 */
    private BigDecimal latitude;

    /** 经度 */
    private BigDecimal longitude;

    /** 打卡定位地址 */
    private String address;

    /** 拜访客户名称 */
    private String customerName;

    /** 外勤事由 */
    private String reason;

    /** 现场照片ossId */
    private Long photoOssId;
}
