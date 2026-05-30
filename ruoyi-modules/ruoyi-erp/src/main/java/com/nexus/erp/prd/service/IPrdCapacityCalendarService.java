package com.nexus.erp.prd.service;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 产能日历Service接口
 *
 * @author nexus
 */
public interface IPrdCapacityCalendarService {

    /**
     * 查询产能日历分页列表
     */
    TableDataInfo<PrdCapacityCalendarVo> selectPageList(PrdCapacityCalendarBo bo, PageQuery pageQuery);

    /**
     * 查询产能日历列表
     */
    List<PrdCapacityCalendarVo> selectList(PrdCapacityCalendarBo bo);

    /**
     * 根据ID查询产能日历
     */
    PrdCapacityCalendarVo selectById(Long calendarId);

    /**
     * 新增产能日历
     */
    Boolean insertByBo(PrdCapacityCalendarBo bo);

    /**
     * 修改产能日历
     */
    Boolean updateByBo(PrdCapacityCalendarBo bo);

    /**
     * 删除产能日历
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 根据排班计划生成产能日历
     */
    void generateFromSchedule(Long planId, Date startDate, Date endDate, String objectType, Long objectId, String objectCode, String objectName);

    /**
     * 查询可用产能
     */
    List<PrdCapacityCalendarVo> queryAvailableCapacity(String objectType, Long objectId,
                                                        Date startDate, Date endDate);

    /**
     * 占用产能
     */
    void occupyCapacity(Long calendarId, BigDecimal hours);

    /**
     * 释放产能
     */
    void releaseCapacity(Long calendarId, BigDecimal hours);
}
