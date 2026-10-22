package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.TgUsageRecord;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.bo.TgUsageRecordBo;
import com.nexus.mes.tg.domain.vo.TgUsageRecordVo;
import com.nexus.mes.tg.mapper.TgUsageRecordMapper;
import com.nexus.mes.tg.service.ITgToolingService;
import com.nexus.mes.tg.service.ITgUsageRecordService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * MES工装使用/领用记录 Service 业务层实现
 * <p><b>MES-TG-03 修复</b>：本类此前提供独立 CRUD，可凭空造 OUT 记录、把 OUT 直接改成
 * RETURNED 而不累加寿命、删掉未归还记录使工装永久卡死 IN_USE。现在：</p>
 * <ul>
 *   <li><b>新增</b>：不再自行落库，统一委托 {@link ITgToolingService#checkout} —— 走
 *       服务端状态机(CAS IDLE-&gt;IN_USE) + 校准/保养到期校验，寿命累加只发生在
 *       {@code doReturn}；</li>
 *   <li><b>修改</b>：已归还(RETURNED)记录禁止修改；未归还(OUT)记录仅允许补全工单/工位/
 *       操作员/备注，<b>status 不可改写</b>（归还只能走 {@code PUT /mes/tg/tooling/return}），
 *       并以 CAS({@code status=OUT}) 保证并发安全；</li>
 *   <li><b>删除</b>：使用记录是 append-only 追溯台账，一律禁止删除（否则工装会卡死 IN_USE
 *       且寿命计数与实物使用脱钩）。</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgUsageRecordService")
public class TgUsageRecordServiceImpl implements ITgUsageRecordService {

    private final TgUsageRecordMapper baseMapper;
    private final ITgToolingService tgToolingService;

    @Override
    public TableDataInfo<TgUsageRecordVo> selectPageList(TgUsageRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgUsageRecord> lqw = buildQueryWrapper(bo);
        Page<TgUsageRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TgUsageRecordVo selectById(Long usageId) {
        return baseMapper.selectVoById(usageId);
    }

    /**
     * 新增使用记录 = 受控领用（MES-TG-03）。
     * <p>不再直接 insert：委托 {@code tgToolingService.checkout} 在同一事务内完成
     * CAS IDLE-&gt;IN_USE + 写 OUT 记录 + 校准/保养到期前置校验。客户端传入的
     * {@code status}/{@code usageCount}/{@code usageHours} 一律忽略（服务端权威）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgUsageRecordBo bo) {
        if (bo == null || bo.getToolingId() == null) {
            throw new ServiceException("工装ID不能为空");
        }
        TgCheckoutBo checkout = new TgCheckoutBo();
        checkout.setOperatorId(bo.getOperatorId());
        checkout.setOperatorName(bo.getOperatorName());
        checkout.setWoId(bo.getWoId());
        checkout.setWoNo(bo.getWoNo());
        checkout.setWorkstationId(bo.getWorkstationId());
        checkout.setWorkstationName(bo.getWorkstationName());
        checkout.setRemark(bo.getRemark());
        log.info("工装使用记录新增收敛为受控领用: toolingId={}", bo.getToolingId());
        return tgToolingService.checkout(bo.getToolingId(), checkout);
    }

    /**
     * 修改使用记录（MES-TG-03）：仅未归还记录可补全非状态字段，且状态不可改写。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgUsageRecordBo bo) {
        if (bo == null || bo.getUsageId() == null) {
            throw new ServiceException("使用记录ID不能为空");
        }
        TgUsageRecord rec = baseMapper.selectById(bo.getUsageId());
        if (rec == null) {
            throw new ServiceException("使用记录不存在: " + bo.getUsageId());
        }
        if (ITgToolingService.USAGE_RETURNED.equals(rec.getStatus())) {
            throw new ServiceException("已归还的使用记录为追溯台账,不可修改（寿命计数已按归还快照累加，"
                + "改动会让台账与工装寿命脱钩）");
        }
        if (!ITgToolingService.USAGE_OUT.equals(rec.getStatus())) {
            throw new ServiceException("使用记录状态异常,不可修改: " + rec.getStatus());
        }
        // 只补全上下文信息；status/usageCount/usageHours/checkoutTime/returnTime 均不可通过本端点改写
        // （归还与寿命累加只能走 ITgToolingService#doReturn），CAS 守卫 status=OUT
        int rows = baseMapper.update(null, Wrappers.<TgUsageRecord>lambdaUpdate()
            .set(bo.getWoId() != null, TgUsageRecord::getWoId, bo.getWoId())
            .set(StringUtils.isNotBlank(bo.getWoNo()), TgUsageRecord::getWoNo, bo.getWoNo())
            .set(bo.getWorkstationId() != null, TgUsageRecord::getWorkstationId, bo.getWorkstationId())
            .set(StringUtils.isNotBlank(bo.getWorkstationName()), TgUsageRecord::getWorkstationName, bo.getWorkstationName())
            .set(bo.getOperatorId() != null, TgUsageRecord::getOperatorId, bo.getOperatorId())
            .set(StringUtils.isNotBlank(bo.getOperatorName()), TgUsageRecord::getOperatorName, bo.getOperatorName())
            .set(StringUtils.isNotBlank(bo.getRemark()), TgUsageRecord::getRemark, bo.getRemark())
            .eq(TgUsageRecord::getUsageId, bo.getUsageId())
            .eq(TgUsageRecord::getStatus, ITgToolingService.USAGE_OUT));
        if (rows == 0) {
            throw new ServiceException("使用记录状态已变更(可能已归还),修改失败");
        }
        log.info("工装使用记录补全: usageId={}", bo.getUsageId());
        return true;
    }

    /**
     * 删除使用记录（MES-TG-03）：append-only，一律禁止。
     * <p>删除未归还记录会让工装永久停在 IN_USE（{@code doReturn} 找不到领出记录），
     * 删除已归还记录则使寿命累加失去依据 —— 两者都破坏追溯链。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        Long exist = baseMapper.selectCount(Wrappers.<TgUsageRecord>lambdaQuery()
            .in(TgUsageRecord::getUsageId, idList));
        if (exist == null || exist == 0) {
            return false;
        }
        throw new ServiceException("工装使用记录为不可篡改的追溯台账,禁止删除：删除未归还记录会使工装永久卡在"
            + "「使用中」，删除已归还记录会使寿命计数失去依据。如需纠正请走归还接口。");
    }

    private LambdaQueryWrapper<TgUsageRecord> buildQueryWrapper(TgUsageRecordBo bo) {
        return Wrappers.<TgUsageRecord>lambdaQuery()
            .eq(bo.getToolingId() != null, TgUsageRecord::getToolingId, bo.getToolingId())
            .eq(bo.getWoId() != null, TgUsageRecord::getWoId, bo.getWoId())
            .eq(bo.getOperatorId() != null, TgUsageRecord::getOperatorId, bo.getOperatorId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), TgUsageRecord::getStatus, bo.getStatus())
            .orderByDesc(TgUsageRecord::getCheckoutTime);
    }
}
