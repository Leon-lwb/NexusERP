package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchShift;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 班次定义Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchShiftService extends IService<SchShift> {
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
     * 批量删除班次定义
     *
     * @param shiftIds 需要删除的班次定义主键集合
     * @return 结果
     */
    public int deleteSchShiftByShiftIds(Long[] shiftIds);

    /**
     * 删除班次定义信息
     *
     * @param shiftId 班次定义主键
     * @return 结果
     */
    public int deleteSchShiftByShiftId(Long shiftId);
}
