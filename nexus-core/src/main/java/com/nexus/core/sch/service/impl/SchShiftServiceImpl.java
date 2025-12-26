package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.utils.DateUtils;
import com.nexus.core.sch.domain.SchPlan;
import com.nexus.core.sch.mapper.SchPlanMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchShiftMapper;
import com.nexus.core.sch.domain.SchShift;
import com.nexus.core.sch.service.ISchShiftService;

/**
 * 班次定义Service业务层处理
 *
 * @author nexus
 * @date 2025-12-25
 */
@Service
public class SchShiftServiceImpl extends ServiceImpl<SchShiftMapper, SchShift> implements ISchShiftService {

    @Autowired
    private SchShiftMapper schShiftMapper;

    @Autowired
    private SchPlanMapper schPlanMapper;

    /**
     * 查询班次定义
     *
     * @param shiftId 班次定义主键
     * @return 班次定义
     */
    @Override
    public SchShift selectSchShiftByShiftId(Long shiftId) {
        return schShiftMapper.selectSchShiftByShiftId(shiftId);
    }

    /**
     * 查询班次定义列表
     *
     * @param schShift 班次定义
     * @return 班次定义
     */
    @Override
    public List<SchShift> selectSchShiftList(SchShift schShift) {
        return schShiftMapper.selectSchShiftList(schShift);
    }

    /**
     * 新增班次定义
     *
     * @param schShift 班次定义
     * @return 结果
     */
    @Override
    public int insertSchShift(SchShift schShift) {
        schShift.setCreateTime(DateUtils.getNowDate());
        // 新增班次自动生成班次编码,规则：plan_code+"-"+1 、2、3...
        // 查询排班计划表中的计划编码
        SchPlan schPlan = schPlanMapper.selectSchPlanByPlanId(schShift.getPlanId());
        if (schPlan != null) {
            String planCode = schPlan.getPlanCode();
            // 查询当前计划下已有的班次数量，以生成下一个编号
            int shiftCount = schShiftMapper.selectCountByPlanId(schShift.getPlanId());
            String num = String.valueOf(shiftCount + 1);
            schShift.setShiftCode(planCode + "-" + num);
        }

        return schShiftMapper.insertSchShift(schShift);
    }

    /**
     * 修改班次定义
     *
     * @param schShift 班次定义
     * @return 结果
     */
    @Override
    public int updateSchShift(SchShift schShift) {
        schShift.setUpdateTime(DateUtils.getNowDate());
        return schShiftMapper.updateSchShift(schShift);
    }

    /**
     * 批量删除班次定义
     *
     * @param shiftIds 需要删除的班次定义主键
     * @return 结果
     */
    @Override
    public int deleteSchShiftByShiftIds(Long[] shiftIds) {
        return schShiftMapper.deleteSchShiftByShiftIds(shiftIds);
    }

    /**
     * 删除班次定义信息
     *
     * @param shiftId 班次定义主键
     * @return 结果
     */
    @Override
    public int deleteSchShiftByShiftId(Long shiftId) {
        return schShiftMapper.deleteSchShiftByShiftId(shiftId);
    }
}
