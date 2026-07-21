package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdReworkOrder;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdReworkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdReworkOrderVo;
import com.nexus.erp.prd.mapper.PrdReworkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdReworkOrderService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 返工订单 Service 业务层实现
 * <p>
 * 实现返工订单相关的业务逻辑，包括返工订单的查询、新增、修改、删除及状态流转等。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdReworkOrderServiceImpl implements IPrdReworkOrderService {

    private final PrdReworkOrderMapper baseMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final IWmStockOperationService stockOperationService;

    /**
     * 分页查询返工订单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 返工订单分页列表
     */
    @Override
    public TableDataInfo<PrdReworkOrderVo> selectPageList(PrdReworkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = buildQueryWrapper(bo);
        Page<PrdReworkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询返工订单列表（不分页）
     *
     * @param bo 查询参数
     * @return 返工订单列表
     */
    @Override
    public List<PrdReworkOrderVo> selectList(PrdReworkOrderBo bo) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据 ID 查询返工订单详情
     *
     * @param id 返工订单 ID
     * @return 返工订单详细信息
     */
    @Override
    public PrdReworkOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增返工订单
     * <p>若存在来源工单，自动带出来源工单的物料与编码信息。</p>
     *
     * @param bo 返工订单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdReworkOrderBo bo) {
        if (bo.getSourceWoId() != null) {
            PrdWorkOrder sourceWo = workOrderMapper.selectById(bo.getSourceWoId());
            if (sourceWo == null) {
                throw new ServiceException("来源工单不存在");
            }
            bo.setSourceWoNo(sourceWo.getWoNo());
            bo.setMaterialId(sourceWo.getMaterialId());
            bo.setMaterialCode(sourceWo.getMaterialCode());
            bo.setMaterialName(sourceWo.getMaterialName());
            bo.setMaterialSpec(sourceWo.getMaterialSpec());
            bo.setBaseUomName(sourceWo.getUnitName());
        }
        PrdReworkOrder rework = MapstructUtils.convert(bo, PrdReworkOrder.class);
        return baseMapper.insert(rework);
    }

    /**
     * 修改返工订单
     * <p>
     * 仅 DRAFT 状态可编辑，已开工时只允许修改结束日期和备注。
     *
     * @param bo 返工订单业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdReworkOrderBo bo) {
        PrdReworkOrder rework = MapstructUtils.convert(bo, PrdReworkOrder.class);
        return baseMapper.updateById(rework);
    }

    /**
     * 批量删除返工订单
     * <p>
     * 仅 DRAFT 或 CLOSED 状态的返工订单可被删除。
     *
     * @param ids 要删除的返工订单 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 开始返工订单
     * <p>
     * 将返工订单状态从 DRAFT 更新为 IN_PROGRESS（进行中）。
     *
     * @param reworkId 返工订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_DRAFT.equals(rework.getStatus())) {
            throw new ServiceException("仅草稿状态可开始，当前状态：" + rework.getStatus());
        }
        rework.setStatus(STATUS_IN_PROGRESS);
        baseMapper.updateById(rework);
    }

    /**
     * 完成返工订单
     * <p>
     * 将返工订单状态从 IN_PROGRESS 更新为 COMPLETED（已完成），
     * 并将报废数量回写到来源工单。
     *
     * @param reworkId 返工订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(rework.getStatus())) {
            throw new ServiceException("仅进行中状态可完工，当前状态：" + rework.getStatus());
        }
        rework.setStatus(STATUS_COMPLETED);
        baseMapper.updateById(rework);

        if (rework.getSourceWoId() != null && rework.getScrapQty() != null
            && rework.getScrapQty().compareTo(BigDecimal.ZERO) > 0) {
            PrdWorkOrder sourceWo = workOrderMapper.selectById(rework.getSourceWoId());
            if (sourceWo != null) {
                BigDecimal currentScrap = sourceWo.getScrapQty() != null ? sourceWo.getScrapQty() : BigDecimal.ZERO;
                sourceWo.setScrapQty(currentScrap.add(rework.getScrapQty()));
                workOrderMapper.updateById(sourceWo);
            }
        }
    }

    /**
     * 返工订单出库（领料）
     * <p>
     * 校验返工订单状态为进行中且物料信息完整，调用 {@link IWmStockOperationService#postReworkIssue}
     * 执行返工出库过账。
     *
     * @param reworkId 返工订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issueMaterial(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(rework.getStatus())) {
            throw new ServiceException("只有进行中的返工订单才能执行出库操作");
        }
        if (rework.getMaterialId() == null || rework.getReworkQty() == null) {
            throw new ServiceException("返工订单物料信息不完整");
        }
        stockOperationService.postReworkIssue(reworkId, rework.getMaterialId(), rework.getMaterialCode(),
            rework.getMaterialName(), rework.getReworkQty(), null, null, new Date());
        log.info("返工订单[{}]出库完成，物料[{}]，数量[{}]", rework.getReworkNo(), rework.getMaterialCode(), rework.getReworkQty());
    }

    /**
     * 返工订单入库（收货）
     * <p>
     * 校验返工订单状态为进行中且物料信息和实际数量完整，调用 {@link IWmStockOperationService#postReworkReceive}
     * 执行返工入库过账。
     *
     * @param reworkId 返工订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveMaterial(Long reworkId) {
        PrdReworkOrder rework = baseMapper.selectById(reworkId);
        if (rework == null) {
            throw new ServiceException("返工订单不存在");
        }
        if (!STATUS_IN_PROGRESS.equals(rework.getStatus())) {
            throw new ServiceException("只有进行中的返工订单才能执行入库操作");
        }
        if (rework.getMaterialId() == null || rework.getActualQty() == null) {
            throw new ServiceException("返工订单物料信息或实际数量不完整");
        }
        stockOperationService.postReworkReceive(reworkId, rework.getMaterialId(), rework.getMaterialCode(),
            rework.getMaterialName(), rework.getActualQty(), null, null, new Date());
        log.info("返工订单[{}]入库完成，物料[{}]，数量[{}]", rework.getReworkNo(), rework.getMaterialCode(), rework.getActualQty());
    }

    private LambdaQueryWrapper<PrdReworkOrder> buildQueryWrapper(PrdReworkOrderBo bo) {
        LambdaQueryWrapper<PrdReworkOrder> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getReworkNo()), PrdReworkOrder::getReworkNo, bo.getReworkNo());
        lqw.like(StringUtils.isNotBlank(bo.getSourceWoNo()), PrdReworkOrder::getSourceWoNo, bo.getSourceWoNo());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), PrdReworkOrder::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdReworkOrder::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdReworkOrder::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getReworkReason()), PrdReworkOrder::getReworkReason, bo.getReworkReason());
        lqw.orderByDesc(PrdReworkOrder::getCreateTime);
        return lqw;
    }
}
