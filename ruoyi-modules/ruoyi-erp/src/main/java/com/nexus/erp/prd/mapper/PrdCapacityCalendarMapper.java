package com.nexus.erp.prd.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 产能日历Mapper接口
 *
 * @author nexus
 */
public interface PrdCapacityCalendarMapper extends BaseMapperPlus<PrdCapacityCalendar, PrdCapacityCalendarVo> {

    /**
     * 查询指定日期范围和对象的产能日历
     */
    List<PrdCapacityCalendarVo> selectAvailableCapacity(@Param("objectType") String objectType,
                                                         @Param("objectId") Long objectId,
                                                         @Param("startDate") Date startDate,
                                                         @Param("endDate") Date endDate);
}
