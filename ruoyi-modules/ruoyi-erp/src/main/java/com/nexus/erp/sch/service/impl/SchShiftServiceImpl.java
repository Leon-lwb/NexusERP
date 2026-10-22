package com.nexus.erp.sch.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.domain.bo.SchShiftBo;
import com.nexus.erp.sch.domain.vo.SchShiftVo;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.ISchShiftService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 班次定义服务业务层处理
 *
 * @author nexus
 * @date 2025-12-25
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchShiftServiceImpl implements ISchShiftService {

    private final SchShiftMapper baseMapper;
    private final PrdCapacityCalendarMapper capacityCalendarMapper;
    private final SchPlanMapper schPlanMapper;
    private final SchTeamScheduleMapper schTeamScheduleMapper;
    private final SchEmployeeScheduleMapper schEmployeeScheduleMapper;

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
        /** 计划必须存在(原实现 planId 无效时 shiftCode 为 null, 违反 NOT NULL 报 SQL 异常) */
        SchPlan schPlan = schPlanMapper.selectById(entity.getPlanId());
        if (schPlan == null) {
            throw new ServiceException("所属排班计划不存在: planId=" + entity.getPlanId());
        }
        if (StringUtils.isBlank(entity.getShiftCode())) {
            /** 编码 = 计划编码-序号; 取现存最大序号+1(原 count+1 在删除过班次后必与现存编码撞 uk_shift_code) */
            long maxNo = baseMapper.selectList(Wrappers.<SchShift>lambdaQuery()
                    .eq(SchShift::getPlanId, entity.getPlanId()))
                .stream().map(SchShift::getShiftCode)
                .filter(code -> code != null && code.startsWith(schPlan.getPlanCode() + "-"))
                .mapToLong(code -> {
                    try { return Long.parseLong(code.substring(code.lastIndexOf('-') + 1)); }
                    catch (NumberFormatException e) { return 0L; }
                }).max().orElse(0L);
            entity.setShiftCode(schPlan.getPlanCode() + "-" + (maxNo + 1));
        }
        Long exist = baseMapper.selectCount(Wrappers.<SchShift>lambdaQuery()
            .eq(SchShift::getShiftCode, entity.getShiftCode()));
        if (exist != null && exist > 0) {
            throw new ServiceException("班次编码已存在: " + entity.getShiftCode());
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
        if (bo == null || bo.getShiftId() == null || bo.getShiftId() <= 0)
            throw new ServiceException("班次ID无效");
        lockUnboundShift(bo.getShiftId());
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
        if (ids == null || ids.length == 0 || Arrays.stream(ids).anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("班次ID无效");
        List<Long> idList = Arrays.stream(ids).distinct().sorted().toList();
        /** 与生成产能日历的班次锁保持相同顺序，已绑定任何日历的班次不能编辑或删除。 */
        for (Long id : idList) lockUnboundShift(id);
        /** 被排班模板/员工排班引用的班次禁止删除(原实现直接删产生悬挂 shiftId) */
        Long inTeamSchedule = schTeamScheduleMapper.selectCount(Wrappers.<SchTeamSchedule>lambdaQuery()
            .in(SchTeamSchedule::getShiftId, idList));
        if (inTeamSchedule != null && inTeamSchedule > 0) {
            throw new ServiceException("班次已被班组排班模板引用, 请先删除对应排班");
        }
        Long inEmployee = schEmployeeScheduleMapper.selectCount(Wrappers.<SchEmployeeSchedule>lambdaQuery()
            .in(SchEmployeeSchedule::getShiftId, idList));
        if (inEmployee != null && inEmployee > 0) {
            throw new ServiceException("班次已被员工排班明细引用, 请先删除对应排班明细");
        }
        return baseMapper.deleteBatchIds(idList);
    }

    private void lockUnboundShift(Long shiftId) {
        SchShift current = baseMapper.selectOne(Wrappers.lambdaQuery(SchShift.class)
            .eq(SchShift::getShiftId, shiftId).last("FOR UPDATE"));
        String tenant = TenantHelper.isEnable() ? TenantHelper.getTenantId() : "000000";
        if (current == null || !shiftId.equals(current.getShiftId())
            || StringUtils.isBlank(tenant) || !tenant.equals(current.getTenantId()))
            throw new ServiceException("班次不存在或租户不一致");
        List<PrdCapacityCalendar> bound = capacityCalendarMapper.selectList(
            Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getTenantId, tenant)
                .eq(PrdCapacityCalendar::getShiftId, shiftId)
                .orderByAsc(PrdCapacityCalendar::getCalendarId).last("FOR UPDATE"));
        if (bound == null || !bound.isEmpty())
            throw new ServiceException("班次已有产能日历或预约，不能修改或删除");
    }

    private LambdaQueryWrapper<SchShift> buildQueryWrapper(SchShiftBo bo) {
        LambdaQueryWrapper<SchShift> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getPlanId() != null, SchShift::getPlanId, bo.getPlanId());
            lqw.like(StringUtils.isNotBlank(bo.getShiftCode()), SchShift::getShiftCode, bo.getShiftCode());
            lqw.like(StringUtils.isNotBlank(bo.getShiftName()), SchShift::getShiftName, bo.getShiftName());
            lqw.eq(StringUtils.isNotBlank(bo.getShiftCategory()), SchShift::getShiftCategory, bo.getShiftCategory());
        }
        return lqw;
    }
}
