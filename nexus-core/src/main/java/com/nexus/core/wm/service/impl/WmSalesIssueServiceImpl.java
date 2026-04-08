package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.wm.domain.WmSalesIssueLine;
import com.nexus.core.wm.mapper.WmSalesIssueMapper;
import com.nexus.core.wm.domain.WmSalesIssue;
import com.nexus.core.wm.service.IWmSalesIssueService;
import com.nexus.core.wm.service.IWmMaterialStockService;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.nexus.common.exception.ServiceException;
import java.math.BigDecimal;

/**
 * 销售出库Service业务层处理
 *
 * @author nexus
 * @date 2026-04-03
 */
@Service
public class WmSalesIssueServiceImpl extends ServiceImpl<WmSalesIssueMapper, WmSalesIssue> implements IWmSalesIssueService {

    @Autowired
    private WmSalesIssueMapper wmSalesIssueMapper;

    @Autowired
    private IWmMaterialStockService materialStockService;

    /**
     * 查询销售出库
     *
     * @param issueId 销售出库主键
     * @return 销售出库
     */
    @Override
    public WmSalesIssue selectWmSalesIssueByIssueId(Long issueId) {
        return wmSalesIssueMapper.selectWmSalesIssueByIssueId(issueId);
    }

    /**
     * 查询销售出库列表
     *
     * @param wmSalesIssue 销售出库
     * @return 销售出库
     */
    @Override
    public List<WmSalesIssue> selectWmSalesIssueList(WmSalesIssue wmSalesIssue) {
        return wmSalesIssueMapper.selectWmSalesIssueList(wmSalesIssue);
    }

    /**
     * 新增销售出库
     *
     * @param wmSalesIssue 销售出库
     * @return 结果
     */
        @Transactional
    @Override
    public int insertWmSalesIssue(WmSalesIssue wmSalesIssue) {
                wmSalesIssue.setCreateTime(DateUtils.getNowDate());
            int rows = wmSalesIssueMapper.insertWmSalesIssue(wmSalesIssue);
            insertWmSalesIssueLine(wmSalesIssue);
            return rows;
    }

    /**
     * 修改销售出库
     *
     * @param wmSalesIssue 销售出库
     * @return 结果
     */
        @Transactional
    @Override
    public int updateWmSalesIssue(WmSalesIssue wmSalesIssue) {
                wmSalesIssue.setUpdateTime(DateUtils.getNowDate());
                wmSalesIssueMapper.deleteWmSalesIssueLineByIssueId(wmSalesIssue.getIssueId());
            insertWmSalesIssueLine(wmSalesIssue);
        return wmSalesIssueMapper.updateWmSalesIssue(wmSalesIssue);
    }

    /**
     * 批量删除销售出库
     *
     * @param issueIds 需要删除的销售出库主键
     * @return 结果
     */
        @Transactional
    @Override
    public int deleteWmSalesIssueByIssueIds(Long[] issueIds) {
                wmSalesIssueMapper.deleteWmSalesIssueLineByIssueIds(issueIds);
        return wmSalesIssueMapper.deleteWmSalesIssueByIssueIds(issueIds);
    }

    /**
     * 删除销售出库信息
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
        @Transactional
    @Override
    public int deleteWmSalesIssueByIssueId(Long issueId) {
                wmSalesIssueMapper.deleteWmSalesIssueLineByIssueId(issueId);
        return wmSalesIssueMapper.deleteWmSalesIssueByIssueId(issueId);
    }

        /**
         * 新增销售出库明细信息
         *
         * @param wmSalesIssue 销售出库对象
         */
        public void insertWmSalesIssueLine(WmSalesIssue wmSalesIssue) {
            List<WmSalesIssueLine> wmSalesIssueLineList = wmSalesIssue.getWmSalesIssueLineList();
            Long issueId = wmSalesIssue.getIssueId();
            if (StringUtils.isNotNull(wmSalesIssueLineList))
            {
                List<WmSalesIssueLine> list = new ArrayList<WmSalesIssueLine>();
                for (WmSalesIssueLine wmSalesIssueLine : wmSalesIssueLineList)
                {
                    wmSalesIssueLine.setIssueId(issueId);
                    list.add(wmSalesIssueLine);
                }
                if (list.size() > 0)
                {
                        wmSalesIssueMapper.batchWmSalesIssueLine(list);
                }
            }
        }

    /**
     * 确认销售出库
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    @Transactional
    @Override
    public int confirmSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = wmSalesIssueMapper.selectWmSalesIssueByIssueId(issueId);
        if (salesIssue == null) {
            throw new ServiceException("销售出库单不存在");
        }
        if (!"PREPARE".equals(salesIssue.getStatus())) {
            throw new ServiceException("只有准备中的出库单才能确认");
        }
        salesIssue.setStatus("CONFIRMED");
        salesIssue.setUpdateTime(DateUtils.getNowDate());
        return wmSalesIssueMapper.updateWmSalesIssue(salesIssue);
    }

    /**
     * 完成销售出库（扣减库存）
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    @Transactional
    @Override
    public int completeSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = wmSalesIssueMapper.selectWmSalesIssueByIssueId(issueId);
        if (salesIssue == null) {
            throw new ServiceException("销售出库单不存在");
        }
        if (!"CONFIRMED".equals(salesIssue.getStatus())) {
            throw new ServiceException("只有已确认的出库单才能完成");
        }

        List<WmSalesIssueLine> lineList = salesIssue.getWmSalesIssueLineList();
        if (lineList == null || lineList.isEmpty()) {
            throw new ServiceException("出库单明细为空");
        }

        // 扣减库存
        for (WmSalesIssueLine line : lineList) {
            // 查询库存记录
            WmMaterialStock stockQuery = new WmMaterialStock();
            stockQuery.setMaterialId(line.getMaterialId());
            stockQuery.setWarehouseId(salesIssue.getWarehouseId());
            stockQuery.setBatchId(line.getBatchId());
            stockQuery.setLocationId(line.getLocationId());

            List<WmMaterialStock> stockList = materialStockService.selectWmMaterialStockList(stockQuery);
            if (stockList == null || stockList.isEmpty()) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】库存不存在");
            }

            WmMaterialStock stock = stockList.get(0);
            BigDecimal currentQty = stock.getStockQuantity();
            BigDecimal issueQty = line.getQuantityIssue();

            if (currentQty == null || currentQty.compareTo(issueQty) < 0) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】库存不足，当前库存：" 
                    + (currentQty != null ? currentQty : 0) + "，出库数量：" + issueQty);
            }

            // 扣减库存
            stock.setStockQuantity(currentQty.subtract(issueQty));
            stock.setUpdateTime(DateUtils.getNowDate());
            materialStockService.updateWmMaterialStock(stock);
        }

        // 更新出库单状态
        salesIssue.setStatus("COMPLETED");
        salesIssue.setUpdateTime(DateUtils.getNowDate());
        return wmSalesIssueMapper.updateWmSalesIssue(salesIssue);
    }
}
