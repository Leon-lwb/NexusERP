package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqMaintenancePlanBo;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;

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
     */
    TableDataInfo<EqMaintenancePlanVo> selectPageList(EqMaintenancePlanBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取保养计划详情
     */
    EqMaintenancePlanVo selectById(Long planId);

    /**
     * 新增保养计划(自动计算下次保养日期)
     */
    EqMaintenancePlanVo insertByBo(EqMaintenancePlanBo bo);

    /**
     * 编辑保养计划(自动计算下次保养日期)
     */
    EqMaintenancePlanVo updateByBo(EqMaintenancePlanBo bo);

    /**
     * 批量删除保养计划
     */
    Boolean deleteByIds(Long[] ids);
}
