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
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.domain.SchTeamMember;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.domain.bo.SchTeamBo;
import com.nexus.erp.sch.domain.bo.SchTeamMemberBo;
import com.nexus.erp.sch.domain.vo.SchTeamMemberVo;
import com.nexus.erp.sch.domain.vo.SchTeamVo;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchTeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * <p>若未指定班组编码，则自动生成以 "SC" 开头的时间戳编码；同时保存班组成员列表并回写成员数量</p>
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        // 自动生成班组编码（简化处理，保留原逻辑框架）
        if (entity.getTeamCode() == null) {
            entity.setTeamCode("SC" + System.currentTimeMillis());
        }
        // 校验班组长存在并回填冗余姓名
        resolveLeader(entity);
        int rows = baseMapper.insert(entity);
        saveTeamMembers(entity.getTeamId(), bo.getSchTeamMemberList(), entity);
        return rows;
    }

    /**
     * 修改班组设置
     * <p>先删除原班组成员再重新插入，并按实际成员数回写成员数量</p>
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        resolveLeader(entity);
        schTeamMemberMapper.delete(new LambdaQueryWrapper<SchTeamMember>()
            .eq(SchTeamMember::getTeamId, entity.getTeamId()));
        saveTeamMembers(entity.getTeamId(), bo.getSchTeamMemberList(), entity);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除班组设置（同时删除班组成员）
     *
     * @param ids 班组ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        schTeamMemberMapper.delete(new LambdaQueryWrapper<SchTeamMember>()
            .in(SchTeamMember::getTeamId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
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
        // 批量查询工人档案，以最新姓名/工号覆盖前端提交值，保证冗余字段准确
        List<Long> workerIds = memberList.stream().map(SchTeamMemberBo::getWorkerId).toList();
        Map<Long, SchWorker> workerMap = new HashMap<>();
        if (!workerIds.isEmpty()) {
            List<SchWorker> workers = schWorkerMapper.selectBatchIds(workerIds);
            for (SchWorker w : workers) {
                workerMap.put(w.getWorkerId(), w);
            }
        }
        for (SchTeamMemberBo memberBo : memberList) {
            if (memberBo.getWorkerId() == null) {
                throw new ServiceException("班组成员未指定工人");
            }
            SchWorker worker = workerMap.get(memberBo.getWorkerId());
            if (worker == null) {
                throw new ServiceException("班组成员对应的工人档案不存在：" + memberBo.getWorkerName());
            }
            SchTeamMember member = MapstructUtils.convert(memberBo, SchTeamMember.class);
            member.setMemberId(null);
            member.setTeamId(teamId);
            // 以工人档案为准刷新冗余字段
            member.setWorkerName(worker.getUserName());
            member.setWorkerCode(worker.getEmployeeId());
            schTeamMemberMapper.insert(member);
        }
        teamEntity.setMemberCount((long) memberList.size());
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
