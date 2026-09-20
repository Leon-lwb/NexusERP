package com.nexus.iot.alert.service;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.domain.bo.IotAlertRecordBo;
import com.nexus.iot.alert.domain.vo.IotAlertNotifyLogVo;
import com.nexus.iot.alert.domain.vo.IotAlertRecordVo;
import com.nexus.iot.device.domain.IotDeviceData;

import java.math.BigDecimal;
import java.util.List;

/**
 * IoT告警记录Service接口（IOT-ALERT-01）
 * <p>
 * 关键约束：
 * <ul>
 *   <li>{@link #trigger} 在无登录上下文的消费线程上执行，内部自行传播租户上下文并落库；</li>
 *   <li>同一 (租户, 设备, 属性) 的**未恢复**告警唯一，重复触发只合并更新，不新增；</li>
 *   <li>确认/恢复使用条件更新（CAS），状态非法流转抛 {@code ServiceException}。</li>
 * </ul>
 *
 * @author nexus
 */
public interface IIotAlertRecordService {

    /**
     * 分页查询告警记录。
     *
     * @param bo        查询条件（状态/级别/设备/时间区间等）
     * @param pageQuery 分页参数
     * @return 告警分页结果
     */
    TableDataInfo<IotAlertRecordVo> selectPageList(IotAlertRecordBo bo, PageQuery pageQuery);

    /**
     * 查询告警记录列表（不分页，供导出）。
     *
     * @param bo 查询条件
     * @return 告警列表
     */
    List<IotAlertRecordVo> selectList(IotAlertRecordBo bo);

    /**
     * 查询告警详情。
     *
     * @param alertId 告警ID
     * @return 告警VO
     */
    IotAlertRecordVo selectById(Long alertId);

    /**
     * 查询某条告警的通知投递留痕。
     *
     * @param alertId 告警ID
     * @return 投递留痕列表（按时间正序）
     */
    List<IotAlertNotifyLogVo> selectNotifyLogs(Long alertId);

    /**
     * 确认告警：ACTIVE → ACKNOWLEDGED（CAS，记录操作人与时间）。
     *
     * @param alertId 告警ID
     * @param remark  确认备注
     * @return 是否成功
     */
    boolean acknowledge(Long alertId, String remark);

    /**
     * 恢复/关闭告警：ACTIVE|ACKNOWLEDGED → RESOLVED（CAS，写恢复值与时间）。
     *
     * @param alertId      告警ID
     * @param resolveValue 恢复值（可空，空则沿用最近触发值）
     * @param remark       恢复备注
     * @return 是否成功
     */
    boolean resolve(Long alertId, BigDecimal resolveValue, String remark);

    /**
     * 消费端入口：触发/合并一条告警并落库 + 通知 + 投递留痕。
     *
     * @param rule   命中规则（可为 null：YAML 兜底默认或 EWMA 异常）
     * @param data   触发该告警的设备数据
     * @param alert  告警内容（类型/级别/描述/触发值/阈值）
     * @param source 来源: {@code RULE}/{@code DEFAULT}/{@code ANOMALY}
     * @return 落库后的告警ID（无设备/无租户等无法落库时返回 null）
     */
    Long trigger(IotAlertRule rule, IotDeviceData data, IotAlert alert, String source);

    /**
     * 消费端入口：属性值回到正常范围时，把该 (设备, 属性) 的未恢复告警置 RESOLVED
     * （写恢复值/时间，清 active_flag 以便同一异常再次触发时产生新记录）。
     *
     * @param data 当前设备数据
     */
    void autoResolve(IotDeviceData data);

}
