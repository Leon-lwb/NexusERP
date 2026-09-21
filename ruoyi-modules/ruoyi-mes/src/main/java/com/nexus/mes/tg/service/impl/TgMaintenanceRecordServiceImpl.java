package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.vo.TgMaintenanceRecordVo;
import com.nexus.mes.tg.mapper.TgMaintenanceRecordMapper;
import com.nexus.mes.tg.service.ITgMaintenanceRecordService;
import com.nexus.mes.tg.service.ITgToolingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MES工装保养记录 Service 业务层实现
 * <p><b>MES-TG-04 修复</b>：此前本类可绕过状态机直接新增/修改/删除保养记录，
 * 出现「账面已保养、实物未保养」（客户端传 usedCountBefore=1500/usedCountAfter=0 即造出
 * 假归零，而 tg_tooling.used_count 仍为 1500、last_maintain_time 不更新、计划阈值不推进）。
 * 现在：</p>
 * <ul>
 *   <li><b>新增</b>：统一委托状态机 {@link ITgToolingService#startMaintain} +
 *       {@link ITgToolingService#completeMaintain} —— CAS IDLE-&gt;MAINTENANCE-&gt;IDLE、
 *       归零 used_count/used_hours、更新 last_maintain_time、推进关联计划阈值，
 *       保养前后快照由<b>服务端</b>回填（不再采信客户端入参）；</li>
 *   <li><b>修改/删除</b>：保养记录是不可篡改的历史快照（成本统计与追溯依据），一律禁止。</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgMaintenanceRecordService")
public class TgMaintenanceRecordServiceImpl implements ITgMaintenanceRecordService {

    private final TgMaintenanceRecordMapper baseMapper;
    private final ITgToolingService tgToolingService;

    @Override
    public TableDataInfo<TgMaintenanceRecordVo> selectPageList(TgMaintenanceRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgMaintenanceRecord> lqw = buildQueryWrapper(bo);
        Page<TgMaintenanceRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TgMaintenanceRecordVo selectById(Long recordId) {
        return baseMapper.selectVoById(recordId);
    }

    /**
     * 新增保养记录 = 受控保养（MES-TG-04）。
     * <p>先 {@code startMaintain}（CAS IDLE-&gt;MAINTENANCE，校准中/使用中会拒绝），
     * 再 {@code completeMaintain}（CAS MAINTENANCE-&gt;IDLE + 归零 + 服务端回填快照 + 写记录 +
     * 推进计划阈值）。两步同事务，任一步失败整体回滚。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgMaintenanceRecordBo bo) {
        if (bo == null || bo.getToolingId() == null) {
            throw new ServiceException("工装ID不能为空");
        }
        log.info("工装保养记录新增收敛为受控保养(状态机+归零): toolingId={}", bo.getToolingId());
        tgToolingService.startMaintain(bo.getToolingId(), bo.getRemark());
        return tgToolingService.completeMaintain(bo.getToolingId(), bo);
    }

    /**
     * 修改保养记录（MES-TG-04）：历史快照不可篡改。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgMaintenanceRecordBo bo) {
        throw new ServiceException("保养记录为不可篡改的历史快照（含保养前后寿命快照与成本），禁止修改："
            + "如需纠正请重新执行保养并保留两次记录");
    }

    /**
     * 删除保养记录（MES-TG-04）：追溯台账，一律禁止。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = java.util.Arrays.asList(ids);
        Long exist = baseMapper.selectCount(Wrappers.<TgMaintenanceRecord>lambdaQuery()
            .in(TgMaintenanceRecord::getRecordId, idList));
        if (exist == null || exist == 0) {
            return false;
        }
        throw new ServiceException("保养记录为不可篡改的追溯台账,禁止删除：删除会使「工装是否真的保养过」"
            + "失去证据，且成本统计与寿命归零依据一并丢失");
    }

    private LambdaQueryWrapper<TgMaintenanceRecord> buildQueryWrapper(TgMaintenanceRecordBo bo) {
        return Wrappers.<TgMaintenanceRecord>lambdaQuery()
            .eq(bo.getToolingId() != null, TgMaintenanceRecord::getToolingId, bo.getToolingId())
            .eq(bo.getPlanId() != null, TgMaintenanceRecord::getPlanId, bo.getPlanId())
            .eq(StringUtils.isNotBlank(bo.getMaintainType()), TgMaintenanceRecord::getMaintainType, bo.getMaintainType())
            .orderByDesc(TgMaintenanceRecord::getMaintainTime);
    }
}
