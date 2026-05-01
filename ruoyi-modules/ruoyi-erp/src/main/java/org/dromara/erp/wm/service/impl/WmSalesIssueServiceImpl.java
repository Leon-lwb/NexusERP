package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmSalesIssue;
import org.dromara.erp.wm.domain.WmSalesIssueLine;
import org.dromara.erp.wm.domain.bo.WmSalesIssueBo;
import org.dromara.erp.wm.domain.bo.WmSalesIssueLineBo;
import org.dromara.erp.wm.domain.vo.WmSalesIssueVo;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmSalesIssueLineMapper;
import org.dromara.erp.wm.mapper.WmSalesIssueMapper;
import org.dromara.erp.wm.service.IWmSalesIssueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.util.List;

/**
 * 销售出库Service业务层处理
 *
 * @author ruoyi
 * @date 2026-04-03
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmSalesIssueServiceImpl implements IWmSalesIssueService {

    private final WmSalesIssueMapper baseMapper;
    private final WmSalesIssueLineMapper lineMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;

    @Override
    public TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmSalesIssue> lqw = buildQueryWrapper(bo);
        Page<WmSalesIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmSalesIssueVo> selectList(WmSalesIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmSalesIssueVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        int rows = baseMapper.insert(entity);
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .in(WmSalesIssueLine::getIssueId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!"PREPARE".equals(salesIssue.getStatus())) {
            throw new RuntimeException("只有准备中的出库单才能确认");
        }
        salesIssue.setStatus("CONFIRMED");
        return baseMapper.updateById(salesIssue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!"CONFIRMED".equals(salesIssue.getStatus())) {
            throw new RuntimeException("只有已确认的出库单才能完成");
        }

        List<WmSalesIssueLine> lineList = lineMapper.selectList(
            new LambdaQueryWrapper<WmSalesIssueLine>()
                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new RuntimeException("出库单明细为空");
        }

        // 扣减库存
        for (WmSalesIssueLine line : lineList) {
            LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
            stockLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            stockLqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
            stockLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            stockLqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());

            WmMaterialStock stock = wmMaterialStockMapper.selectOne(stockLqw);
            if (stock == null) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不存在");
            }

            BigDecimal currentQty = stock.getStockQuantity();
            BigDecimal issueQty = line.getQuantityIssue();

            if (currentQty == null || currentQty.compareTo(issueQty) < 0) {
                throw new RuntimeException("物料【" + line.getMaterialName() + "】库存不足，当前库存："
                    + (currentQty != null ? currentQty : BigDecimal.ZERO) + "，出库数量：" + issueQty);
            }

            stock.setStockQuantity(currentQty.subtract(issueQty));
            wmMaterialStockMapper.updateById(stock);
        }

        salesIssue.setStatus("COMPLETED");
        return baseMapper.updateById(salesIssue);
    }

    private void insertLines(Long issueId, List<WmSalesIssueLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmSalesIssueLine> lineList = MapstructUtils.convert(lineBoList, WmSalesIssueLine.class);
            for (WmSalesIssueLine line : lineList) {
                line.setIssueId(issueId);
            }
            lineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmSalesIssue> buildQueryWrapper(WmSalesIssueBo bo) {
        LambdaQueryWrapper<WmSalesIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmSalesIssue::getIssueCode, bo.getIssueCode());
        lqw.like(StringUtils.isNotBlank(bo.getIssueName()), WmSalesIssue::getIssueName, bo.getIssueName());
        lqw.eq(bo.getSoId() != null, WmSalesIssue::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), WmSalesIssue::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, WmSalesIssue::getClientId, bo.getClientId());
        lqw.eq(StringUtils.isNotBlank(bo.getClientCode()), WmSalesIssue::getClientCode, bo.getClientCode());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), WmSalesIssue::getClientName, bo.getClientName());
        lqw.eq(bo.getWarehouseId() != null, WmSalesIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmSalesIssue::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmSalesIssue::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getIssueDate() != null, WmSalesIssue::getIssueDate, bo.getIssueDate());
        lqw.eq(bo.getTotalQuantity() != null, WmSalesIssue::getTotalQuantity, bo.getTotalQuantity());
        lqw.eq(bo.getTotalAmount() != null, WmSalesIssue::getTotalAmount, bo.getTotalAmount());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSalesIssue::getStatus, bo.getStatus());
        return lqw;
    }

}
