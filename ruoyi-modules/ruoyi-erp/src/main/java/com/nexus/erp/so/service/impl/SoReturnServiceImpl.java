package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoReturn;
import com.nexus.erp.so.domain.SoReturnLine;
import com.nexus.erp.so.domain.bo.SoReturnBo;
import com.nexus.erp.so.domain.vo.SoReturnLineVo;
import com.nexus.erp.so.domain.vo.SoReturnVo;
import com.nexus.erp.so.mapper.SoReturnLineMapper;
import com.nexus.erp.so.mapper.SoReturnMapper;
import com.nexus.erp.so.service.ISoReturnService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售退货Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soReturnService")
public class SoReturnServiceImpl implements ISoReturnService {

    private final SoReturnMapper baseMapper;
    private final SoReturnLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;

    /**
     * 分页查询销售退货列表
     */
    @Override
    public TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoReturn> lqw = buildQueryWrapper(bo);
        Page<SoReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售退货列表
     */
    @Override
    public List<SoReturnVo> selectList(SoReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售退货详情（包含明细行）
     */
    @Override
    public SoReturnVo selectById(Long id) {
        SoReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getReturnId() != null) {
            LambdaQueryWrapper<SoReturnLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(SoReturnLine::getReturnId, vo.getReturnId());
            lineLqw.orderByAsc(SoReturnLine::getSortOrder);
            // 转 VO 返回，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端
            vo.setSoReturnLineList(MapstructUtils.convert(lineMapper.selectList(lineLqw), SoReturnLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售退货（状态强制草稿 + 自动生成退货单号 + 保存明细行）。
     * <p>原实现不校验状态，客户端可直接建出 {@code status=RECEIVED} 的退货单 ——
     * 界面显示「已收货」但库存从未加回（{@code execute()} 才会入库），
     * 且 T1.3 之后 {@code execute()} 要求 CONFIRMED，这张单永远无法真正执行，
     * 形成「看起来已完成、实际什么都没发生」的假数据。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoReturnBo bo) {
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        entity.setStatus(STATUS_DRAFT);
        if (StringUtils.isBlank(entity.getReturnNo())) {
            entity.setReturnNo(autoCodeUtil.genSerialCode("SO_RETURN_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        return rows;
    }

    /**
     * 修改销售退货（仅草稿可改；先删除原明细行再重新插入）。
     * <p>单号与状态由服务端权威维护，不接受客户端改写。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoReturnBo bo) {
        if (bo.getReturnId() == null) {
            throw new ServiceException("退货单ID不能为空");
        }
        SoReturn existing = baseMapper.selectById(bo.getReturnId());
        if (existing == null) {
            throw new ServiceException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("只有草稿状态的退货单才能修改，当前状态：" + existing.getStatus());
        }
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        entity.setReturnNo(existing.getReturnNo());
        entity.setStatus(existing.getStatus());
        // 明细为 null 表示「本次不提交明细」：明细与金额都保持库中原值；
        // 显式传空数组才是「清空全部明细」。原实现无条件「删行 + 重插」，
        // 传 null 会把所有明细删光且金额清零（与销售订单 T1.1 加固同源问题）。
        if (bo.getSoReturnLineList() != null) {
            deleteLinesByReturnId(entity.getReturnId());
            insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        } else {
            entity.setTotalAmount(existing.getTotalAmount());
            entity.setTaxAmount(existing.getTaxAmount());
            entity.setRefundAmount(existing.getRefundAmount());
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售退货（仅草稿可删除，同时删除明细行）。
     * <p>已确认/已收货的退货单意味着库存已加回或即将加回，删除会让库存回补失去凭证，
     * 因此一律拒绝；如确需撤销，应先走作废/红冲流程。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        // 先全量校验再执行，避免删一半失败留下半成品
        for (Long id : ids) {
            SoReturn existing = baseMapper.selectById(id);
            if (existing == null) {
                continue;
            }
            if (!STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("只有草稿状态的退货单才能删除，单号 "
                    + existing.getReturnNo() + " 当前状态：" + existing.getStatus());
            }
        }
        for (Long id : ids) {
            deleteLinesByReturnId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 确认销售退货单（仅草稿状态可确认）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(soReturn.getStatus())) {
            throw new RuntimeException("只有草稿状态的退货单才能确认");
        }
        soReturn.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(soReturn);
    }

    /**
     * 执行退货入库（扣减库存并生成库存交易记录）。
     * <p>仅「已确认」（CONFIRMED）可执行：原实现只拦 RECEIVED，导致 DRAFT/APPROVING/CLOSED
     * 状态都能直接入库，既绕过确认审批，也能对已关闭单据重复入库。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new ServiceException("退货单不存在");
        }
        if (!STATUS_CONFIRMED.equals(soReturn.getStatus())) {
            throw new ServiceException("只有已确认状态的退货单才能执行入库，当前状态：" + soReturn.getStatus());
        }

        List<SoReturnLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(SoReturnLine.class)
                .eq(SoReturnLine::getReturnId, returnId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("退货单无明细行");
        }

        // 条件更新抢占状态 → RECEIVED, 防并发双执行重复入库
        int updated = baseMapper.update(null,
            Wrappers.<SoReturn>lambdaUpdate()
                .set(SoReturn::getStatus, STATUS_RECEIVED)
                .eq(SoReturn::getReturnId, returnId)
                .eq(SoReturn::getStatus, soReturn.getStatus()));
        if (updated == 0) {
            throw new RuntimeException("退货单状态已被并发操作变更，请刷新后重试");
        }

        for (SoReturnLine line : lines) {
            postReturnStock(soReturn, line, new Date());
        }

        log.info("退货单执行完成, returnId={}", returnId);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交审批流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String returnIdStr, String selectFlowId) {
        Long returnId = Long.parseLong(returnIdStr);
        SoReturn entity = baseMapper.selectById(returnId);
        if (entity == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的退货单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", returnIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("returnId", entity.getReturnId());
        variables.put("returnNo", entity.getReturnNo());
        R<Void> result = processService.startProcessByDataId(returnIdStr, selectFlowId, "soReturnService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取流程实例ID
     */
    @Override
    public String getProcInsId(String returnIdStr) {
        return procInsIdHelper.resolve(returnIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(returnIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 流程审批后处理（更新退货单状态）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long returnId = Long.parseLong(business.getDataId());
        SoReturn entity = baseMapper.selectById(returnId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_CONFIRMED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(entity);
    }

    /**
     * 根据数据ID获取业务数据
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取流程任务变量
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取流程任务候选用户
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 销售退货入库：将退货行数量加回库存（无库存则新建），并登记库存交易流水（RETURN_IN）。
     * <p>退货数量必须 &gt;0，否则抛异常。</p>
     *
     * @param soReturn 销售退货单（提供仓库维度）
     * @param line     退货明细行（提供物料与退货数量）
     * @param now      操作时间
     */
    private void postReturnStock(SoReturn soReturn, SoReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退货数量必须大于0");
        }
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(soReturn.getWarehouseId() != null, WmMaterialStock::getWarehouseId, soReturn.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        java.math.BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
            ? java.math.BigDecimal.ZERO : stock.getStockQuantity();
        java.math.BigDecimal afterQuantity = beforeQuantity.add(line.getReturnQuantity());

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(line.getMaterialId());
            stock.setMaterialCode(line.getMaterialCode());
            stock.setMaterialName(line.getMaterialName());
            stock.setMaterialSpec(line.getMaterialSpec());
            stock.setBaseUomId(line.getBaseUomId());
            stock.setBaseUomName(line.getBaseUomName());
            stock.setWarehouseId(soReturn.getWarehouseId());
            stock.setWarehouseCode(soReturn.getWarehouseCode());
            stock.setWarehouseName(soReturn.getWarehouseName());
            stock.setStockQuantity(line.getReturnQuantity());
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            // 新建库存行必须显式置 0, 否则 version 为 null 时后续乐观锁更新不生效
            stock.setVersion(0);
            stockMapper.insert(stock);
        } else {
            // 原子累加(乐观锁 version 守卫), 替换读-改-写
            int rows = stockMapper.addStock(stock.getStockId(), line.getReturnQuantity(), stock.getVersion());
            if (rows == 0) {
                throw new RuntimeException("库存并发变更冲突，请重试: stockId=" + stock.getStockId());
            }
        }

        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType("RETURN_IN");
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxFlag(1);
        tx.setTxQuantity(line.getReturnQuantity());
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        tx.setSourceDocType("SO_RETURN");
        tx.setSourceDocId(soReturn.getReturnId());
        tx.setSourceDocCode(soReturn.getReturnNo());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("销售退货入库，原订单号：" + soReturn.getSoNo());
        stockTxMapper.insert(tx);
    }

    /**
     * 批量新增退货单明细行：清空行ID后按退货单ID归属并逐条插入。
     *
     * @param returnId 退货单ID
     * @param lineList 明细行列表
     */
    private void insertOrUpdateLines(Long returnId, List<SoReturnLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (SoReturnLine line : lineList) {
                line.setLineId(null);
                line.setReturnId(returnId);
                lineMapper.insert(line);
            }
        }
    }

    /**
     * 按退货单ID删除其所有明细行。
     *
     * @param returnId 退货单ID
     */
    private void deleteLinesByReturnId(Long returnId) {
        if (returnId != null) {
            LambdaQueryWrapper<SoReturnLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoReturnLine::getReturnId, returnId);
            lineMapper.delete(lqw);
        }
    }

    /**
     * 构建退货单分页/列表查询条件（按退货单号、销售订单号、客户、状态动态拼接，按创建时间倒序）。
     *
     * @param bo 查询参数
     * @return 查询条件构造器
     */
    private LambdaQueryWrapper<SoReturn> buildQueryWrapper(SoReturnBo bo) {
        LambdaQueryWrapper<SoReturn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReturnNo()), SoReturn::getReturnNo, bo.getReturnNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), SoReturn::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, SoReturn::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoReturn::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoReturn::getStatus, bo.getStatus());
        // 关键字模糊搜索：退货单号 / 来源销售订单号 / 客户名称 任一匹配（移动端单输入框搜索）
        lqw.and(StringUtils.isNotBlank(bo.getKeyword()),
            w -> w.like(SoReturn::getReturnNo, bo.getKeyword())
                .or()
                .like(SoReturn::getSoNo, bo.getKeyword())
                .or()
                .like(SoReturn::getClientName, bo.getKeyword()));
        lqw.orderByDesc(SoReturn::getCreateTime);
        return lqw;
    }
}
