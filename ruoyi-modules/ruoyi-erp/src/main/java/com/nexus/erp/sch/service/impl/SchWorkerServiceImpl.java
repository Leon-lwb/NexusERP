package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.domain.SchTeamMember;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.domain.bo.SchWorkerBo;
import com.nexus.erp.sch.domain.vo.SchWorkerVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchWorkerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 生产工人档案Service业务层处理
 *
 * @author nexus
 * @date 2025-12-19
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchWorkerServiceImpl implements ISchWorkerService {

    private final SchWorkerMapper baseMapper;
    private final SchTeamMemberMapper schTeamMemberMapper;
    private final SchTeamMapper schTeamMapper;
    private final SchAttendanceMapper schAttendanceMapper;
    private final SchEmployeeScheduleMapper schEmployeeScheduleMapper;

    /**
     * 分页查询生产工人档案列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产工人档案分页列表
     */
    @Override
    public TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchWorker> lqw = buildQueryWrapper(bo);
        Page<SchWorkerVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产工人档案列表
     *
     * @param bo 查询参数
     * @return 生产工人档案列表
     */
    @Override
    public List<SchWorkerVo> selectList(SchWorkerBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取生产工人档案详情
     *
     * @param id 工人档案ID
     * @return 生产工人档案详情
     */
    @Override
    public SchWorkerVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchWorkerBo bo) {
        checkUserIdUnique(bo.getUserId(), null);
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchWorkerBo bo) {
        checkUserIdUnique(bo.getUserId(), bo.getWorkerId());
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        int rows = baseMapper.updateById(entity);
        // 同步冗余字段：工人改名/改工号后，刷新班组成员表、班组长的冗余姓名/工号
        syncRedundantFields(entity);
        return rows;
    }

    /**
     * 批量删除生产工人档案
     * <p>删除前校验是否被班组（成员或班组长）、考勤、排班引用，被引用则拒绝</p>
     *
     * @param ids 工人档案ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 校验是否作为班组成员
        Long memberCount = schTeamMemberMapper.selectCount(new LambdaQueryWrapper<SchTeamMember>()
            .in(SchTeamMember::getWorkerId, idList));
        if (memberCount != null && memberCount > 0) {
            throw new ServiceException("存在作为班组成员的工人，不能删除（请先从班组移除）");
        }
        // 校验是否作为班组长
        Long leaderCount = schTeamMapper.selectCount(new LambdaQueryWrapper<SchTeam>()
            .in(SchTeam::getLeaderId, idList));
        if (leaderCount != null && leaderCount > 0) {
            throw new ServiceException("存在作为班组长的工人，不能删除（请先解除班组长身份）");
        }
        // 校验考勤引用
        Long attendanceCount = schAttendanceMapper.selectCount(new LambdaQueryWrapper<SchAttendance>()
            .in(SchAttendance::getWorkerId, idList));
        if (attendanceCount != null && attendanceCount > 0) {
            throw new ServiceException("存在考勤记录的工人，不能删除");
        }
        // 校验排班引用
        Long scheduleCount = schEmployeeScheduleMapper.selectCount(new LambdaQueryWrapper<SchEmployeeSchedule>()
            .in(SchEmployeeSchedule::getWorkerId, idList));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new ServiceException("存在排班记录的工人，不能删除");
        }
        return baseMapper.deleteBatchIds(idList);
    }

    /**
     * 查询已建档工人档案的系统用户ID集合
     *
     * @return 已建档的 userId 集合（无账号工人 userId 为空，不在结果中）
     */
    @Override
    public List<Long> selectBuiltUserIds() {
        LambdaQueryWrapper<SchWorker> lqw = Wrappers.lambdaQuery();
        lqw.isNotNull(SchWorker::getUserId);
        List<SchWorker> workers = baseMapper.selectList(lqw);
        if (workers.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = new ArrayList<>(workers.size());
        for (SchWorker w : workers) {
            if (w.getUserId() != null) {
                userIds.add(w.getUserId());
            }
        }
        return userIds;
    }

    /**
     * 同步冗余字段：工人姓名/工号变更后，刷新 sch_team_member.worker_name/worker_code
     * 与 sch_team.leader_name（若该工人是某班组的班组长）
     *
     * @param worker 更新后的工人实体
     */
    private void syncRedundantFields(SchWorker worker) {
        if (worker.getWorkerId() == null) {
            return;
        }
        // 刷新班组成员冗余字段
        SchTeamMember memberUpdate = new SchTeamMember();
        memberUpdate.setWorkerName(worker.getUserName());
        memberUpdate.setWorkerCode(worker.getEmployeeId());
        schTeamMemberMapper.update(memberUpdate, new LambdaQueryWrapper<SchTeamMember>()
            .eq(SchTeamMember::getWorkerId, worker.getWorkerId()));
        // 若该工人是班组长，刷新班组冗余字段
        SchTeam teamUpdate = new SchTeam();
        teamUpdate.setLeaderName(worker.getUserName());
        schTeamMapper.update(teamUpdate, new LambdaQueryWrapper<SchTeam>()
            .eq(SchTeam::getLeaderId, worker.getWorkerId()));
    }

    private LambdaQueryWrapper<SchWorker> buildQueryWrapper(SchWorkerBo bo) {
        LambdaQueryWrapper<SchWorker> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getUserName()), SchWorker::getUserName, bo.getUserName());
            lqw.eq(StringUtils.isNotBlank(bo.getEmployeeId()), SchWorker::getEmployeeId, bo.getEmployeeId());
            lqw.eq(bo.getUserId() != null, SchWorker::getUserId, bo.getUserId());
            lqw.eq(bo.getWorkshopId() != null, SchWorker::getWorkshopId, bo.getWorkshopId());
            lqw.eq(StringUtils.isNotBlank(bo.getWorkStatus()), SchWorker::getWorkStatus, bo.getWorkStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getWorkCategory()), SchWorker::getWorkCategory, bo.getWorkCategory());
        }
        return lqw;
    }

    /**
     * 校验一个系统用户只能建档一个工人档案（无账号工人 userId 为空，跳过校验）
     *
     * @param userId           关联的系统用户ID
     * @param excludeWorkerId  排除的工人档案ID（修改时排除自身）
     */
    private void checkUserIdUnique(Long userId, Long excludeWorkerId) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<SchWorker> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchWorker::getUserId, userId);
        lqw.ne(excludeWorkerId != null, SchWorker::getWorkerId, excludeWorkerId);
        Long count = baseMapper.selectCount(lqw);
        if (count != null && count > 0) {
            throw new ServiceException("该系统用户已建档工人档案，不能重复建档");
        }
    }
}
