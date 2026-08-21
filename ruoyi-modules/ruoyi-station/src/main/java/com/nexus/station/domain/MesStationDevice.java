package com.nexus.station.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 工位终端设备台账对象 mes_station_device
 * <p>心跳(C10) upsert 承载: device_code 唯一, 心跳时顺带维护 工位码↔工序码 绑定。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_station_device")
public class MesStationDevice extends TenantEntity {

    /** 设备ID(主键) */
    @TableId(value = "device_id")
    private Long deviceId;

    /** 设备编码(工控机) */
    private String deviceCode;

    /** 绑定工位编码 */
    private String stationCode;

    /** 绑定工序编码 */
    private String processCode;

    /** 终端版本 */
    private String appVersion;

    /** 最后心跳时间 */
    private Date lastHeartbeat;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
