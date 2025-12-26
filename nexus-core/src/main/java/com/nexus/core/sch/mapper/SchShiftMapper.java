package com.nexus.core.sch.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.sch.domain.SchShift;

/**
 * 班次定义Mapper接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface SchShiftMapper extends BaseMapper<SchShift> {
    /**
     * 查询班次定义
     *
     * @param shiftId 班次定义主键
     * @return 班次定义
     */
    public SchShift selectSchShiftByShiftId(Long shiftId);

    /**
     * 查询班次定义列表
     *
     * @param schShift 班次定义
     * @return 班次定义集合
     */
    public List<SchShift> selectSchShiftList(SchShift schShift);

    /**
     * 新增班次定义
     *
     * @param schShift 班次定义
     * @return 结果
     */
    public int insertSchShift(SchShift schShift);

    /**
     * 修改班次定义
     *
     * @param schShift 班次定义
     * @return 结果
     */
    public int updateSchShift(SchShift schShift);

    /**
     * 删除班次定义
     *
     * @param shiftId 班次定义主键
     * @return 结果
     */
    public int deleteSchShiftByShiftId(Long shiftId);

    /**
     * 批量删除班次定义
     *
     * @param shiftIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchShiftByShiftIds(Long[] shiftIds);

    /**
     * 根据计划ID查询班次数量
     *
     * @param planId 计划ID
     * @return 班次数量
     */
    public int selectCountByPlanId(Long planId);
}
