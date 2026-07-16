package com.nexus.mes.eq.service;

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
 * 状态机: REPORTED -> REPAIRING -> COMPLETED
 * 设备状态联动: 报修复设备FAULT; 完成且无其他活跃报修复NORMAL
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
     * 分页查询维修记录列表
     */
    TableDataInfo<EqRepairVo> selectPageList(EqRepairBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取维修详情
     */
    EqRepairVo selectById(Long repairId);

    /**
     * 不分页查询维修记录(导出用)
     */
    List<EqRepairVo> selectList(EqRepairBo bo);

    /**
     * 报修(新增维修单 REPORTED), 联动置设备FAULT
     */
    EqRepairVo insertByBo(EqRepairBo bo);

    /**
     * 开工(REPORTED -> REPAIRING), 置维修人/维修开始时间
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
     * @return {reported, repairing, completed, active(=reported+repairing), total}
     */
    Map<String, Object> statusSummary();
}
