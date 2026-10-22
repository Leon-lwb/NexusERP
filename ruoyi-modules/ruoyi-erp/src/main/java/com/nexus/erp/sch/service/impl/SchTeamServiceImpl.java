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
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlanTeam;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.domain.SchTeamMember;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.domain.bo.SchTeamBo;
import com.nexus.erp.sch.domain.bo.SchTeamMemberBo;
import com.nexus.erp.sch.domain.vo.SchTeamMemberVo;
import com.nexus.erp.sch.domain.vo.SchTeamVo;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchTeamService;
import com.nexus.system.strategy.AutoCodeUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 班组设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-23
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchTeamServiceImpl implements ISchTeamService {

    private final SchTeamMapper baseMapper;
    private final SchTeamMemberMapper schTeamMemberMapper;
    private final SchWorkerMapper schWorkerMapper;
    private final SchTeamScheduleMapper schTeamScheduleMapper;
    private final SchPlanTeamMapper schPlanTeamMapper;
    private final SchEmployeeScheduleMapper schEmployeeScheduleMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询班组设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组设置分页列表
     */
    @Override
    public TableDataInfo<SchTeamVo> selectPageList(SchTeamBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchTeam> lqw = buildQueryWrapper(bo);
        Page<SchTeamVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询班组设置列表
     *
     * @param bo 查询参数
     * @return 班组设置列表
     */
    @Override
    public List<SchTeamVo> selectList(SchTeamBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取班组设置详情（含班组成员列表）
     *
     * @param id 班组ID
     * @return 班组设置详情
     */
    @Override
    public SchTeamVo selectById(Long id) {
        SchTeamVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<SchTeamMemberVo> memberVoList = schTeamMemberMapper.selectVoList(
                new LambdaQueryWrapper<SchTeamMember>().eq(SchTeamMember::getTeamId, id));
            vo.setSchTeamMemberList(memberVoList);
        }
        return vo;
    }

    /**
     * 新增班组设置
     * <p>班组编码留空时按编码规则 SCH_TEAM_CODE 自动生成(原实现为 "SC"+时间戳,
     * 超长且与规则计数器脱节); 编码唯一性前置校验。</p>
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        // 编码: 留空走 SCH_TEAM_CODE 规则生成; 传入则校验唯一(空串视为未填)
        if (StringUtils.isBlank(entity.getTeamCode())) {
            entity.setTeamCode(autoCodeUtil.genSerialCode("SCH_TEAM_CODE", null));
        }
        Long exist = baseMapper.selectCount(Wrappers.<SchTeam>lambdaQuery()
            .eq(SchTeam::getTeamCode, entity.getTeamCode()));
        if (exist != null && exist > 0) {
            throw new ServiceException("班组编码已存在: " + entity.getTeamCode());
        }
        // 校验班组长存在并回填冗余姓名
        resolveLeader(entity);
        int rows = baseMapper.insert(entity);
        if (rows > 0) {
            saveTeamMembers(entity.getTeamId(), bo.getSchTeamMemberList(), entity);
        }
        return rows;
    }

    /**
     * 修改班组设置
     * <p>成员列表非空(null)时才重建——先删后插; 前端仅编辑班组主信息不带成员列表的请求
     * 不得清空既有成员(原实现无条件删除导致数据丢失); 成员传空数组 [] 视为显式清空。</p>
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        // 编码被修改时校验唯一
        if (StringUtils.isNotBlank(entity.getTeamCode())) {
            Long exist = baseMapper.selectCount(Wrappers.<SchTeam>lambdaQuery()
                .eq(SchTeam::getTeamCode, entity.getTeamCode())
                .ne(SchTeam::getTeamId, entity.getTeamId()));
            if (exist != null && exist > 0) {
                throw new ServiceException("班组编码已存在: " + entity.getTeamCode());
            }
        }
        resolveLeader(entity);
        if (bo.getSchTeamMemberList() != null) {
            schTeamMemberMapper.delete(new LambdaQueryWrapper<SchTeamMember>()
                .eq(SchTeamMember::getTeamId, entity.getTeamId()));
            saveTeamMembers(entity.getTeamId(), bo.getSchTeamMemberList(), entity);
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除班组设置（同时删除班组成员）
     * <p>被排班模板(sch_team_schedule)/计划班组(sch_plan_team)/员工排班(sch_employee_schedule)
     * 引用的班组禁止删除(原实现直接删产生悬挂引用)。</p>
     *
     * @param ids 班组ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        Long scheduled = schTeamScheduleMapper.selectCount(Wrappers.<SchTeamSchedule>lambdaQuery()
            .in(SchTeamSchedule::getTeamId, idList));
        if (scheduled != null && scheduled > 0) {
            throw new ServiceException("班组已被排班模板引用, 请先删除对应排班记录");
        }
        Long inPlan = schPlanTeamMapper.selectCount(Wrappers.<SchPlanTeam>lambdaQuery()
            .in(SchPlanTeam::getTeamId, idList));
        if (inPlan != null && inPlan > 0) {
            throw new ServiceException("班组已被排班计划引用, 请先在计划中移除该班组");
        }
        Long inEmployee = schEmployeeScheduleMapper.selectCount(Wrappers.<SchEmployeeSchedule>lambdaQuery()
            .in(SchEmployeeSchedule::getTeamId, idList));
        if (inEmployee != null && inEmployee > 0) {
            throw new ServiceException("班组已生成员工排班明细, 请先删除对应排班明细");
        }
        schTeamMemberMapper.delete(new LambdaQueryWrapper<SchTeamMember>()
            .in(SchTeamMember::getTeamId, idList));
        return baseMapper.deleteBatchIds(idList);
    }

    /**
     * 校验班组长存在并回填冗余姓名
     */
    private void resolveLeader(SchTeam entity) {
        if (entity.getLeaderId() == null) {
            entity.setLeaderName(null);
            return;
        }
        SchWorker leader = schWorkerMapper.selectById(entity.getLeaderId());
        if (leader == null) {
            throw new ServiceException("班组长对应的工人档案不存在");
        }
        entity.setLeaderName(leader.getUserName());
    }

    /**
     * 保存班组成员列表：按 workerId 回查工人档案刷新姓名/工号，回填班组ID后逐条插入，
     * 并按实际成员数回写 memberCount
     *
     * @param teamId       班组ID
     * @param memberList   班组成员业务对象列表
     * @param teamEntity   班组实体（用于回写成员数量）
     */
    private void saveTeamMembers(Long teamId, List<SchTeamMemberBo> memberList, SchTeam teamEntity) {
        if (memberList == null || memberList.isEmpty()) {
            teamEntity.setMemberCount(0L);
            baseMapper.updateById(teamEntity);
            return;
        }
        // 同组去重(同一工人重复提交会产生重复排班行, 且无 (team_id,worker_id) 唯一键兜底)
        List<SchTeamMemberBo> deduped = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (SchTeamMemberBo m : memberList) {
            if (m.getWorkerId() != null && seen.add(m.getWorkerId())) {
                deduped.add(m);
            }
        }
        if (deduped.isEmpty()) {
            throw new ServiceException("班组成员未指定工人");
        }
        // 批量查询工人档案，以最新姓名/工号覆盖前端提交值，保证冗余字段准确
        List<Long> workerIds = deduped.stream().map(SchTeamMemberBo::getWorkerId).toList();
        Map<Long, SchWorker> workerMap = new HashMap<>();
        List<SchWorker> workers = schWorkerMapper.selectBatchIds(workerIds);
        for (SchWorker w : workers) {
            workerMap.put(w.getWorkerId(), w);
        }
        List<SchTeamMember> rows = new ArrayList<>(deduped.size());
        for (SchTeamMemberBo memberBo : deduped) {
            SchWorker worker = workerMap.get(memberBo.getWorkerId());
            if (worker == null) {
                throw new ServiceException("班组成员对应的工人档案不存在: " + memberBo.getWorkerName());
            }
            SchTeamMember member = MapstructUtils.convert(memberBo, SchTeamMember.class);
            member.setMemberId(null);
            member.setTeamId(teamId);
            // 以工人档案为准刷新冗余字段
            member.setWorkerName(worker.getUserName());
            member.setWorkerCode(worker.getEmployeeId());
            rows.add(member);
        }
        schTeamMemberMapper.insertBatch(rows);
        teamEntity.setMemberCount((long) rows.size());
        baseMapper.updateById(teamEntity);
    }

    private LambdaQueryWrapper<SchTeam> buildQueryWrapper(SchTeamBo bo) {
        LambdaQueryWrapper<SchTeam> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getTeamCode()), SchTeam::getTeamCode, bo.getTeamCode());
            lqw.like(StringUtils.isNotBlank(bo.getTeamName()), SchTeam::getTeamName, bo.getTeamName());
            lqw.eq(StringUtils.isNotBlank(bo.getTeamCategory()), SchTeam::getTeamCategory, bo.getTeamCategory());
            lqw.eq(bo.getWorkshopId() != null, SchTeam::getWorkshopId, bo.getWorkshopId());
            lqw.eq(bo.getStatus() != null, SchTeam::getStatus, bo.getStatus());
        }
        return lqw;
    }
}
