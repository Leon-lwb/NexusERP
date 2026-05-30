package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.bo.SchShiftBo;
import com.nexus.erp.sch.domain.vo.SchShiftVo;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.service.ISchShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 班次定义Service业务层处理
 *
 * @author nexus
 * @date 2025-12-25
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchShiftServiceImpl implements ISchShiftService {

    private final SchShiftMapper baseMapper;
    private final SchPlanMapper schPlanMapper;

    /**
     * 分页查询班次定义列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班次定义分页列表
     */
    @Override
    public TableDataInfo<SchShiftVo> selectPageList(SchShiftBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchShift> lqw = buildQueryWrapper(bo);
        Page<SchShiftVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询班次定义列表
     *
     * @param bo 查询参数
     * @return 班次定义列表
     */
    @Override
    public List<SchShiftVo> selectList(SchShiftBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取班次定义详情
     *
     * @param id 班次ID
     * @return 班次定义详情
     */
    @Override
    public SchShiftVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增班次定义
     * <p>新增时自动生成班次编码，规则为：计划编码 + "-" + 序号（如 PLAN001-1）</p>
     *
     * @param bo 班次定义业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchShiftBo bo) {
        SchShift entity = MapstructUtils.convert(bo, SchShift.class);
        // 新增班次自动生成班次编码,规则：plan_code+"-"+1 、2、3...
        SchPlan schPlan = schPlanMapper.selectById(entity.getPlanId());
        if (schPlan != null) {
            String planCode = schPlan.getPlanCode();
            long shiftCount = baseMapper.selectCount(Wrappers.<SchShift>lambdaQuery().eq(SchShift::getPlanId, entity.getPlanId()));
            String num = String.valueOf(shiftCount + 1);
            entity.setShiftCode(planCode + "-" + num);
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改班次定义
     *
     * @param bo 班次定义业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchShiftBo bo) {
        SchShift entity = MapstructUtils.convert(bo, SchShift.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除班次定义
     *
     * @param ids 班次ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchShift> buildQueryWrapper(SchShiftBo bo) {
        LambdaQueryWrapper<SchShift> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getPlanId() != null, SchShift::getPlanId, bo.getPlanId());
            lqw.like(StringUtils.isNotBlank(bo.getShiftName()), SchShift::getShiftName, bo.getShiftName());
            lqw.eq(StringUtils.isNotBlank(bo.getShiftCategory()), SchShift::getShiftCategory, bo.getShiftCategory());
        }
        return lqw;
    }
}
