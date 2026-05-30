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
 * 产能日历 Service 接口
 * <p>
 * 定义产能日历相关的业务逻辑操作，包括产能日历的查询、生成、占用与释放等。
 *
 * @author nexus
 */
public interface IPrdCapacityCalendarService {

    /**
     * 分页查询产能日历列表
     *
     * @param bo        查询参数，包含日期、对象类型、对象ID等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 产能日历分页列表
     */
    TableDataInfo<PrdCapacityCalendarVo> selectPageList(PrdCapacityCalendarBo bo, PageQuery pageQuery);

    /**
     * 查询产能日历列表（不分页）
     *
     * @param bo 查询参数，包含日期、对象类型、对象ID等筛选条件
     * @return 产能日历列表
     */
    List<PrdCapacityCalendarVo> selectList(PrdCapacityCalendarBo bo);

    /**
     * 根据 ID 查询产能日历详情
     *
     * @param calendarId 产能日历 ID
     * @return 产能日历详细信息
     */
    PrdCapacityCalendarVo selectById(Long calendarId);

    /**
     * 新增产能日历
     *
     * @param bo 产能日历业务对象，包含日期、班次、可用工时等信息
     * @return 是否新增成功
     */
    Boolean insertByBo(PrdCapacityCalendarBo bo);

    /**
     * 修改产能日历
     *
     * @param bo 产能日历业务对象，包含需要更新的字段信息
     * @return 是否更新成功
     */
    Boolean updateByBo(PrdCapacityCalendarBo bo);

    /**
     * 批量删除产能日历
     *
     * @param ids     要删除的产能日历 ID 集合
     * @param isValid 是否进行业务校验
     * @return 是否删除成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 根据排班计划生成产能日历
     * <p>
     * 根据指定的排班计划，在日期范围内为对象生成产能日历记录。
     *
     * @param planId     排班计划 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @param objectType 对象类型（WORKSHOP-车间, WORKSTATION-工位, LINE-生产线）
     * @param objectId   对象 ID
     * @param objectCode 对象编码
     * @param objectName 对象名称
     */
    void generateFromSchedule(Long planId, Date startDate, Date endDate, String objectType, Long objectId, String objectCode, String objectName);

    /**
     * 查询指定对象的可用产能列表
     *
     * @param objectType 对象类型
     * @param objectId   对象 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 可用产能日历列表
     */
    List<PrdCapacityCalendarVo> queryAvailableCapacity(String objectType, Long objectId,
                                                        Date startDate, Date endDate);

    /**
     * 占用产能
     * <p>
     * 将指定产能日历的剩余产能中占用指定工时数。
     *
     * @param calendarId 产能日历 ID
     * @param hours      占用工时数
     */
    void occupyCapacity(Long calendarId, BigDecimal hours);

    /**
     * 释放产能
     * <p>
     * 将指定产能日历中已占用的工时释放回剩余产能。
     *
     * @param calendarId 产能日历 ID
     * @param hours      释放工时数
     */
    void releaseCapacity(Long calendarId, BigDecimal hours);
}
