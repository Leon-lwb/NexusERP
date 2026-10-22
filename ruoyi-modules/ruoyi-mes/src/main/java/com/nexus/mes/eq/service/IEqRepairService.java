package com.nexus.mes.eq.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqRepairBo;
import com.nexus.mes.eq.domain.bo.EqRepairCompleteBo;
import com.nexus.mes.eq.domain.vo.EqRepairStatisticsVo;
import com.nexus.mes.eq.domain.vo.EqRepairVo;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES设备维修记录 Service 业务层接口
 * <p>
 * 状态机: REPORTED -> REPAIRING -> COMPLETED, 以及 REPORTED/REPAIRING -> CANCELLED(作废/撤销)。
 * 活跃报修 = status IN (REPORTED, REPAIRING); CANCELLED 与 COMPLETED 均视为已闭环。
 * 设备状态联动: 报修复设备FAULT; 完成/作废且无其他活跃报修复NORMAL
 *
 * @author Nexus
 */
public interface IEqRepairService {

    /** 状态: 已报修 */
    String STATUS_REPORTED = "REPORTED";
    /** 状态: 维修中 */
    String STATUS_REPAIRING = "REPAIRING";
    /** 状态: 已完成 */
    String STATUS_COMPLETED = "COMPLETED";
    /**
     * 状态: 已作废(误报修/重复报修撤销)。
     * <p>与 ry-vue.sql 字典 mes_repair_status 的取值对齐(增量脚本补第 4 个值)。
     * 作废单不再是"活跃报修", 因此设备可被释放回 NORMAL。
     */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询维修记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqRepairVo> selectPageList(EqRepairBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取维修详情
     *
     * @param repairId 维修记录ID
     * @return 返回结果
     */
    EqRepairVo selectById(Long repairId);

    /**
     * 不分页查询维修记录(导出用)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqRepairVo> selectList(EqRepairBo bo);

    /**
     * 报修(新增维修单 REPORTED), 联动置设备FAULT
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqRepairVo insertByBo(EqRepairBo bo);

    /**
     * 开工(REPORTED -> REPAIRING), 置维修人/维修开始时间
     *
     * @param repairId 维修记录ID
     */
    void startRepair(Long repairId);

    /**
     * 完成(REPAIRING -> COMPLETED), 置维修结束时间/停机时长/维修成本,
     * 联动: 该设备无其他活跃报修则恢复NORMAL;
     * 若携带更换备件明细, 事务内写 eq_repair_spare 并扣减备件库存(P1-2 R1)。
     *
     * @param repairId 维修ID
     * @param bo       完成信息(停机时长/维修成本/备注/更换备件明细)
     */
    void completeRepair(Long repairId, EqRepairCompleteBo bo);

    /**
     * 作废/撤销维修单(误报修/重复报修闭环出口)。
     *
     * <p>状态守卫用条件更新(CAS)限定 {@code status IN ('REPORTED','REPAIRING')}, 已完工/已作废 的单不可作废(影响 0 行抛 {@link
     * ServiceException})。 作废成功后调用 {@code resetToNormalIfNoActiveRepair}: 若该设备已无其他活跃报修则 释放 FAULT 回
     * NORMAL, 消除"误报修使设备永久 FAULT"的问题(MES-EQ-06)。
     *
     * @param repairId 维修ID
     * @param reason 作废原因(可空, 非空时写入 remark 留痕)
     */
    void cancelRepair(Long repairId, String reason);

    /**
     * 故障统计分析(KPI + 趋势 + Pareto + 设备TOP)
     *
     * @param startDate 统计开始日期(null 默认最近30天)
     * @param endDate   统计结束日期(null 默认今天)
     * @return 统计结果
     */
    EqRepairStatisticsVo statistics(Date startDate, Date endDate);

    /**
     * 维修状态计数汇总(监控大屏用, 全量 COUNT 不受分页 pageSize 限制)
     *
     * @return {reported, repairing, completed, cancelled, active(=reported+repairing), total}
     *
     * @return 结果映射
     */
    Map<String, Object> statusSummary();
}
