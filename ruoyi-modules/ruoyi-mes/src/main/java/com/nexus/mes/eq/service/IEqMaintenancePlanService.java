package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.bo.EqMaintenancePlanBo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;

import java.util.List;

/**
 * MES设备保养计划 Service 业务层接口
 * <p>
 * 保养类型: DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY
 * 下次保养日期按周期自动计算(用户未显式指定时)
 *
 * @author Nexus
 */
public interface IEqMaintenancePlanService {

    /** 保养类型: 日 */
    String TYPE_DAILY = "DAILY";
    /** 保养类型: 周 */
    String TYPE_WEEKLY = "WEEKLY";
    /** 保养类型: 月 */
    String TYPE_MONTHLY = "MONTHLY";
    /** 保养类型: 季 */
    String TYPE_QUARTERLY = "QUARTERLY";
    /** 保养类型: 年 */
    String TYPE_YEARLY = "YEARLY";

    /** 状态: 启用 */
    String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 停用 */
    String STATUS_INACTIVE = "INACTIVE";

    /**
     * 分页查询保养计划列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqMaintenancePlanVo> selectPageList(EqMaintenancePlanBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取保养计划详情
     *
     * @param planId 计划ID
     * @return 返回结果
     */
    EqMaintenancePlanVo selectById(Long planId);

    /**
     * 不分页查询保养计划(导出用)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqMaintenancePlanVo> selectList(EqMaintenancePlanBo bo);

    /**
     * 新增保养计划(自动计算下次保养日期)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqMaintenancePlanVo insertByBo(EqMaintenancePlanBo bo);

    /**
     * 编辑保养计划(自动计算下次保养日期)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqMaintenancePlanVo updateByBo(EqMaintenancePlanBo bo);

    /**
     * 批量删除保养计划
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 完成保养计划: 写入保养执行记录 + 推进 nextDate = today + cycleDays,
     * 若保养结果异常则置设备 MAINTENANCE
     *
     * @param planId 计划ID
     * @param bo     保养执行情况(内容/结果/备注)
     * @return 保养执行记录
     */
    EqMaintenanceRecordVo completePlan(Long planId, EqMaintenanceRecordBo bo);
}
