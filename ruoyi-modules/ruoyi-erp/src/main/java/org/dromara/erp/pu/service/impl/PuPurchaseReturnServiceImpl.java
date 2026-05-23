package org.dromara.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.PuPurchaseReturn;
import org.dromara.erp.pu.domain.PuPurchaseReturnLine;
import org.dromara.erp.pu.domain.bo.PuPurchaseReturnBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseReturnVo;
import org.dromara.erp.pu.mapper.PuPurchaseReturnLineMapper;
import org.dromara.erp.pu.mapper.PuPurchaseReturnMapper;
import org.dromara.erp.pu.service.IPuPurchaseReturnService;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购退货服务实现类
 * <p>
 * 实现采购退货单的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增（自动编号/默认状态）、修改（状态校验）、删除</li>
 *   <li>状态流转：确认（草稿→已确认）、执行退货出库（扣减库存+生成交易记录）</li>
 * </ul>
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuPurchaseReturnServiceImpl implements IPuPurchaseReturnService {

    private final PuPurchaseReturnMapper baseMapper;
    private final PuPurchaseReturnLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询采购退货单列表
     */
    @Override
    public TableDataInfo<PuPurchaseReturnVo> selectPageList(PuPurchaseReturnBo bo, PageQuery pageQuery) {
        Page<PuPurchaseReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询采购退货单列表（不分页）
     */
    @Override
    public List<PuPurchaseReturnVo> selectList(PuPurchaseReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询采购退货单，附带明细行列表（按排序号升序）
     */
    @Override
    public PuPurchaseReturnVo selectById(Long id) {
        PuPurchaseReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getReturnId() != null) {
            LambdaQueryWrapper<PuPurchaseReturnLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuPurchaseReturnLine::getReturnId, vo.getReturnId());
            lqw.orderByAsc(PuPurchaseReturnLine::getSortOrder);
            vo.setPuPurchaseReturnLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增采购退货单
     * <p>
     * 自动生成退货单号（如为空）、默认设为草稿状态、默认退货日期为当天，同时保存明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuPurchaseReturnBo bo) {
        PuPurchaseReturn entity = MapstructUtils.convert(bo, PuPurchaseReturn.class);
        if (StringUtils.isBlank(entity.getReturnNo())) {
            entity.setReturnNo(autoCodeUtil.genSerialCode("PU_RETURN_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getReturnDate() == null) {
            entity.setReturnDate(new Date());
        }
        int rows = baseMapper.insert(entity);
        insertLines(entity.getReturnId(), bo.getPuPurchaseReturnLineList());
        return rows;
    }

    /**
     * 修改采购退货单
     * <p>
     * 仅草稿状态的退货单允许编辑。修改时先删除全部旧明细行，再插入新明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseReturnBo bo) {
        PuPurchaseReturn entity = MapstructUtils.convert(bo, PuPurchaseReturn.class);
        PuPurchaseReturn current = baseMapper.selectById(entity.getReturnId());
        if (current == null) {
            throw new RuntimeException("采购退货单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus())) {
            throw new RuntimeException("只有草稿状态的采购退货单才能编辑");
        }
        deleteLines(entity.getReturnId());
        insertLines(entity.getReturnId(), bo.getPuPurchaseReturnLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除采购退货单，同时删除关联的明细行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLines(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 确认退货单：仅草稿状态允许确认，将状态改为已确认
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        PuPurchaseReturn purchaseReturn = ensureReturn(returnId);
        if (!STATUS_DRAFT.equals(purchaseReturn.getStatus())) {
            throw new RuntimeException("只有草稿状态的采购退货单才能确认");
        }
        purchaseReturn.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(purchaseReturn);
    }

    /**
     * 执行退货出库
     * <p>
     * 核心逻辑：
     * <ol>
     *   <li>校验退货单状态，已执行的跳过</li>
     *   <li>查询退货明细行，无明细则抛异常</li>
     *   <li>对每一条明细行调用 {@link #postReturnOut} 执行库存扣减和交易记录生成</li>
     *   <li>更新退货单状态为"已执行"</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        PuPurchaseReturn purchaseReturn = ensureReturn(returnId);
        if (STATUS_EXECUTED.equals(purchaseReturn.getStatus())) {
            return;
        }
        List<PuPurchaseReturnLine> lines = lineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("采购退货单无明细行");
        }
        for (PuPurchaseReturnLine line : lines) {
            postReturnOut(purchaseReturn, line, new Date());
        }
        purchaseReturn.setStatus(STATUS_EXECUTED);
        baseMapper.updateById(purchaseReturn);
    }

    /**
     * 执行退货出库操作
     * <p>
     * 校验退货数量 > 0，查询物料库存是否足够，扣减库存并插入库存交易记录。
     * 交易类型为 PUR_RETURN_OUT（采购退货出库），交易标志 -1 表示减少库存。
     *
     * @param purchaseReturn 退货单主表
     * @param line           退货明细行
     * @param now            当前时间
     */
    private void postReturnOut(PuPurchaseReturn purchaseReturn, PuPurchaseReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退货数量必须大于0");
        }
        // 查询未冻结的物料库存
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);
        if (stock == null || stock.getStockQuantity() == null || stock.getStockQuantity().compareTo(line.getReturnQuantity()) < 0) {
            throw new RuntimeException("库存不足，无法执行采购退货");
        }
        // 扣减库存
        BigDecimal beforeQuantity = stock.getStockQuantity();
        BigDecimal afterQuantity = beforeQuantity.subtract(line.getReturnQuantity());
        stock.setStockQuantity(afterQuantity);
        stock.setRecptDate(now);
        stockMapper.updateById(stock);

        // 生成库存交易记录
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType("PUR_RETURN_OUT");
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
        tx.setTxFlag(-1);               // -1 表示出库/减少
        tx.setTxQuantity(line.getReturnQuantity());
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        tx.setSourceDocType("PU_PURCHASE_RETURN");
        tx.setSourceDocId(purchaseReturn.getReturnId());
        tx.setSourceDocCode(purchaseReturn.getReturnNo());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("采购退货出库，采购单号：" + purchaseReturn.getOrderNo());
        stockTxMapper.insert(tx);
    }

    /**
     * 批量插入退货明细行
     */
    private void insertLines(Long returnId, List<PuPurchaseReturnLine> lineList) {
        if (returnId == null || lineList == null || lineList.isEmpty()) {
            return;
        }
        for (PuPurchaseReturnLine line : lineList) {
            line.setReturnId(returnId);
            lineMapper.insert(line);
        }
    }

    /**
     * 删除指定退货单的所有明细行
     */
    private void deleteLines(Long returnId) {
        if (returnId == null) {
            return;
        }
        lineMapper.delete(Wrappers.lambdaQuery(PuPurchaseReturnLine.class).eq(PuPurchaseReturnLine::getReturnId, returnId));
    }

    /**
     * 确保退货单存在，否则抛出异常
     */
    private PuPurchaseReturn ensureReturn(Long returnId) {
        PuPurchaseReturn purchaseReturn = baseMapper.selectById(returnId);
        if (purchaseReturn == null) {
            throw new RuntimeException("采购退货单不存在");
        }
        return purchaseReturn;
    }

    /**
     * 构建查询条件 Wrapper
     * <p>
     * 支持按退货单号精确匹配、按来源订单号精确匹配、按状态精确匹配、按供应商名称模糊匹配，
     * 默认按创建时间降序排列。
     */
    private LambdaQueryWrapper<PuPurchaseReturn> buildQueryWrapper(PuPurchaseReturnBo bo) {
        LambdaQueryWrapper<PuPurchaseReturn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReturnNo()), PuPurchaseReturn::getReturnNo, bo.getReturnNo());
        lqw.eq(StringUtils.isNotBlank(bo.getOrderNo()), PuPurchaseReturn::getOrderNo, bo.getOrderNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseReturn::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuPurchaseReturn::getVendorName, bo.getVendorName());
        lqw.orderByDesc(PuPurchaseReturn::getCreateTime);
        return lqw;
    }
}
