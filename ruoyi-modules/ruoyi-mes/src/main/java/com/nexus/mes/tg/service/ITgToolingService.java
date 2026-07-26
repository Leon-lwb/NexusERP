package com.nexus.mes.tg.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
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
 *                 └-> MAINTENANCE -> IDLE(保养归零)
 *                 └-> SCRAPPED(终态)
 * 寿命双轨: used_count(次数) + used_hours(时长), 归还累加, 保养归零
 *
 * @author Nexus
 */
public interface ITgToolingService {

    /** 状态: 闲置 */
    String STATUS_IDLE = "IDLE";
    /** 状态: 使用中 */
    String STATUS_IN_USE = "IN_USE";
    /** 状态: 保养中 */
    String STATUS_MAINTENANCE = "MAINTENANCE";
    /** 状态: 已报废 */
    String STATUS_SCRAPPED = "SCRAPPED";

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
     * 校验工装可用性(契约方法: P0-3 进站出站校验调用)
     * <p>校验: 存在 + status=IDLE + 非报废/保养中。</p>
     *
     * @return { usable, reason, toolingId, toolingCode, status }
     */
    Map<String, Object> checkToolingAvailable(Long toolingId);

    /**
     * 保养到期查询: 返回存在已启用保养计划且达到触发阈值的工装
     */
    List<TgToolingVo> listMaintenanceDue();
}
