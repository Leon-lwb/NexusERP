package com.nexus.mes.tg.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.bo.TgCalibrationBo;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.bo.TgToolingBo;
import com.nexus.mes.tg.domain.vo.TgToolingVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * MES工装 Service 业务层接口
 * <p>
 * 状态机: IDLE ⇄ IN_USE(领用/归还)
 *                 └-> CALIBRATING -> IDLE(校准登记, MES-TG-01)
 *                 └-> MAINTENANCE -> IDLE(保养归零)
 *                 └-> SCRAPPED(终态)
 * 寿命双轨: used_count(次数) + used_hours(时长), 归还累加, 保养归零
 * 校准维度: calibration_cycle_days/last_calibration_date/next_calibration_date/calibrated_by/
 *          calibration_result/calibration_remark; next_calibration_date 超期即拦截领用与进站
 *
 * @author Nexus
 */
public interface ITgToolingService {

    /** 状态: 闲置 */
    String STATUS_IDLE = "IDLE";
    /** 状态: 使用中 */
    String STATUS_IN_USE = "IN_USE";
    /** 状态: 校准中(MES-TG-01) */
    String STATUS_CALIBRATING = "CALIBRATING";
    /** 状态: 保养中 */
    String STATUS_MAINTENANCE = "MAINTENANCE";
    /** 状态: 已报废 */
    String STATUS_SCRAPPED = "SCRAPPED";

    /** 校准结果: 合格 */
    String CALIBRATION_PASS = "PASS";
    /** 校准结果: 不合格(工装立即置为「校准超期」, 复校通过前不可用) */
    String CALIBRATION_FAIL = "FAIL";
    /** 校准结果: 调整后合格 */
    String CALIBRATION_ADJUSTED = "ADJUSTED";

    /** 校准到期提醒窗口(天): next_calibration_date 落在今天~今天+N 天内即纳入提醒 (MES-TG-05) */
    int CALIBRATION_DUE_SOON_DAYS = 7;

    /** 使用记录状态: 领出 */
    String USAGE_OUT = "OUT";
    /** 使用记录状态: 已归还 */
    String USAGE_RETURNED = "RETURNED";

    /**
     * 分页查询工装台账列表
     */
    TableDataInfo<TgToolingVo> selectPageList(TgToolingBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取工装详情
     */
    TgToolingVo selectById(Long toolingId);

    /**
     * 新增工装(自动生成编码,初始状态 IDLE)
     */
    Boolean insertByBo(TgToolingBo bo);

    /**
     * 修改工装(仅 IDLE 可编辑;used_count/status 等走专用接口)
     */
    Boolean updateByBo(TgToolingBo bo);

    /**
     * 批量删除工装(孤儿守卫:存在使用/保养记录则禁止删除)
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 领用: IDLE -> IN_USE, 写 tg_usage_record(status=OUT)
     * <p>使用 @Lock4j 防并发</p>
     */
    Boolean checkout(Long toolingId, TgCheckoutBo bo);

    /**
     * 归还: IN_USE -> IDLE, 更新 usage_record(RETURNED+usage_count/hours), 累加 tg_tooling.used_*
     * <p>使用 @Lock4j 防并发</p>
     *
     * @param toolingId   工装ID
     * @param usageCount  本次使用次数
     * @param usageHours  本次使用时长
     * @param remark      备注(可选)
     */
    Boolean doReturn(Long toolingId, Integer usageCount, BigDecimal usageHours, String remark);

    /**
     * 开始保养: IDLE/IN_USE -> MAINTENANCE
     */
    Boolean startMaintain(Long toolingId, String remark);

    /**
     * 完成保养: MAINTENANCE -> IDLE, 归零 used_count/used_hours, 更新 last_maintain_time,
     * 写 tg_maintenance_record(before 快照 + after=0)
     * <p>使用 @Lock4j 防并发</p>
     */
    Boolean completeMaintain(Long toolingId, TgMaintenanceRecordBo bo);

    /**
     * 报废: -> SCRAPPED (守卫: IN_USE 不可,需先归还)
     */
    Boolean scrap(Long toolingId, String reason);

    /**
     * 校验工装可用性(契约方法: 进站/出站校验 + 领用前置)
     * <p>校验: 存在 + status=IDLE(非报废/保养中/校准中/使用中) + <b>校准未超期</b> + <b>保养未到期</b>。
     * 校准/保养超期一律 usable=false 并给出可读原因(MES-TG-01/TG-02)。</p>
     *
     * @return { usable, reason, toolingId, toolingCode, status, calibrationOverdue?, maintenanceDue? }
     */
    Map<String, Object> checkToolingAvailable(Long toolingId);

    /**
     * 保养到期查询: 返回存在已启用保养计划且达到触发阈值的工装
     */
    List<TgToolingVo> listMaintenanceDue();

    /**
     * 校准到期/超期查询: 返回 next_calibration_date 落在「今天 ~ 今天+{@link #CALIBRATION_DUE_SOON_DAYS} 天」
     * 的闲置/使用中工装(含已超期) (MES-TG-01/TG-05)
     */
    List<TgToolingVo> listCalibrationDue();

    /**
     * 登记校准(MES-TG-01): CAS IDLE -> CALIBRATING -> IDLE,
     * 写 last_calibration_date/calibrated_by/calibration_result/calibration_remark,
     * 并按「校准日期 + 周期」由服务端计算 next_calibration_date(超期拦截依据)。
     * <p>校准结果为 FAIL 时 next_calibration_date 置为本次校准日期 —— 工装立即处于
     * 「校准超期」状态, 复校通过前不可领用/进站。</p>
     *
     * @param toolingId 工装ID
     * @param bo        校准入参(可为空, 全部取默认)
     * @return 登记成功返回 true
     */
    Boolean registerCalibration(Long toolingId, TgCalibrationBo bo);
}
