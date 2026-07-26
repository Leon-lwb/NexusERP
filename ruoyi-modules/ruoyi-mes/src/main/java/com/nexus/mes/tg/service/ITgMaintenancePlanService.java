package com.nexus.mes.tg.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.bo.TgMaintenancePlanBo;
import com.nexus.mes.tg.domain.vo.TgMaintenancePlanVo;

import java.util.Collection;

/**
 * MES工装保养计划 Service 业务层接口
 *
 * @author Nexus
 */
public interface ITgMaintenancePlanService {

    /** 触发类型: 按次数 */
    String TRIGGER_BY_COUNT = "BY_COUNT";
    /** 触发类型: 按时长 */
    String TRIGGER_BY_HOURS = "BY_HOURS";
    /** 触发类型: 按周期 */
    String TRIGGER_PERIODIC = "PERIODIC";

    /**
     * 分页查询保养计划列表
     */
    TableDataInfo<TgMaintenancePlanVo> selectPageList(TgMaintenancePlanBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取保养计划详情
     */
    TgMaintenancePlanVo selectById(Long planId);

    /**
     * 新增保养计划(按 trigger_type 自动计算下次阈值/时间)
     */
    Boolean insertByBo(TgMaintenancePlanBo bo);

    /**
     * 修改保养计划
     */
    Boolean updateByBo(TgMaintenancePlanBo bo);

    /**
     * 批量删除保养计划
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 查询工装关联的已启用保养计划(供到期检测)
     */
    Collection<TgMaintenancePlanVo> listEnabledByToolingId(Long toolingId);
}
