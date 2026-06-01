package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoQuotation;
import com.nexus.erp.so.domain.SoQuotationLine;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationVo;
import com.nexus.erp.so.mapper.SoQuotationLineMapper;
import com.nexus.erp.so.mapper.SoQuotationMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoQuotationService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 销售报价Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoQuotationServiceImpl implements ISoQuotationService {

    private final SoQuotationMapper baseMapper;
    private final SoQuotationLineMapper lineMapper;
    private final SoSalesOrderHeaderMapper salesOrderMapper;
    private final SoSalesOrderLineMapper salesOrderLineMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询销售报价列表
     */
    @Override
    public TableDataInfo<SoQuotationVo> selectPageList(SoQuotationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoQuotation> lqw = buildQueryWrapper(bo);
        Page<SoQuotationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售报价列表
     */
    @Override
    public List<SoQuotationVo> selectList(SoQuotationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售报价详情（包含明细行）
     */
    @Override
    public SoQuotationVo selectById(Long id) {
        SoQuotationVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getQuotationId() != null) {
            LambdaQueryWrapper<SoQuotationLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(SoQuotationLine::getQuotationId, vo.getQuotationId());
            lineLqw.orderByAsc(SoQuotationLine::getSortOrder);
            vo.setSoQuotationLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增销售报价（自动生成报价单号并保存明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoQuotationBo bo) {
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        entity.setQuotationNo(autoCodeUtil.genSerialCode("SO_QUOTATION_CODE", null));
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getQuotationId(), bo.getSoQuotationLineList());
        return rows;
    }

    /**
     * 修改销售报价（先删除原明细行再重新插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoQuotationBo bo) {
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        deleteLinesByQuotationId(entity.getQuotationId());
        insertOrUpdateLines(entity.getQuotationId(), bo.getSoQuotationLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售报价（同时删除明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByQuotationId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 确认销售报价（仅草稿状态可确认）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long quotationId) {
        SoQuotation quotation = baseMapper.selectById(quotationId);
        if (quotation == null) {
            throw new RuntimeException("报价单不存在");
        }
        if (!STATUS_DRAFT.equals(quotation.getStatus())) {
            throw new RuntimeException("只有草稿状态的报价单才能确认");
        }
        quotation.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(quotation);
    }

    /**
     * 将销售报价转换为销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToSalesOrder(Long quotationId) {
        SoQuotation quotation = baseMapper.selectById(quotationId);
        if (quotation == null) {
            throw new RuntimeException("报价单不存在");
        }
        if (STATUS_CONVERTED.equals(quotation.getStatus())) {
            throw new RuntimeException("报价单已转换，不能重复转换");
        }

        List<SoQuotationLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(SoQuotationLine.class)
                .eq(SoQuotationLine::getQuotationId, quotationId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("报价单无明细行");
        }

        // 创建销售订单
        SoSalesOrderHeader order = new SoSalesOrderHeader();
        order.setClientId(quotation.getClientId());
        order.setClientName(quotation.getClientName());
        order.setSalesRepId(quotation.getSalesRepId());
        order.setSalesRepName(quotation.getSalesRepName());
        order.setOrderDate(new Date());
        order.setCurrency(quotation.getCurrency());
        order.setExchangeRate(quotation.getExchangeRate());
        order.setTotalAmount(quotation.getTotalAmount());
        order.setTaxAmount(quotation.getTaxAmount());
        order.setDiscountAmount(quotation.getDiscountAmount());
        order.setDiscountRate(quotation.getDiscountRate());
        order.setPaymentTerms(quotation.getPaymentTerms());
        order.setSoNo(autoCodeUtil.genSerialCode("SO_ORDER_CODE", null));
        order.setOrderStatus(ISoSalesOrderHeaderService.STATUS_DRAFT);
        order.setRemark("从报价单【" + quotation.getQuotationNo() + "】转换");
        order.setAttr1(quotation.getQuotationNo());
        salesOrderMapper.insert(order);

        // 复制报价行到销售订单行
        List<SoSalesOrderLine> orderLines = new ArrayList<>();
        for (SoQuotationLine ql : lines) {
            SoSalesOrderLine ol = new SoSalesOrderLine();
            ol.setSoId(order.getSoId());
            ol.setMaterialId(ql.getMaterialId());
            ol.setMaterialCode(ql.getMaterialCode());
            ol.setMaterialName(ql.getMaterialName());
            ol.setMaterialSpec(ql.getMaterialSpec());
            ol.setUnit(ql.getBaseUomName());
            ol.setOrderedQty(ql.getQuantity() != null ? ql.getQuantity() : BigDecimal.ZERO);
            ol.setUnitPrice(ql.getUnitPrice());
            ol.setLineAmount(ql.getAmount());
            ol.setTaxRate(ql.getTaxRate());
            ol.setTaxAmount(ql.getTaxAmount());
            ol.setRequestedDate(ql.getDeliveryDate());
            ol.setSortOrder(ql.getSortOrder() != null ? ql.getSortOrder().longValue() : null);
            ol.setRemark(ql.getRemark());
            orderLines.add(ol);
        }
        salesOrderLineMapper.insertBatch(orderLines);

        // 更新报价单状态
        quotation.setStatus(STATUS_CONVERTED);
        baseMapper.updateById(quotation);

        log.info("报价单 {} 已转换为销售订单 {}", quotationId, order.getSoId());
        return order.getSoId();
    }

    private void insertOrUpdateLines(Long quotationId, List<SoQuotationLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (SoQuotationLine line : lineList) {
                line.setLineId(null);
                line.setQuotationId(quotationId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByQuotationId(Long quotationId) {
        if (quotationId != null) {
            LambdaQueryWrapper<SoQuotationLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoQuotationLine::getQuotationId, quotationId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<SoQuotation> buildQueryWrapper(SoQuotationBo bo) {
        LambdaQueryWrapper<SoQuotation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getQuotationNo()), SoQuotation::getQuotationNo, bo.getQuotationNo());
        lqw.eq(bo.getClientId() != null, SoQuotation::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoQuotation::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoQuotation::getStatus, bo.getStatus());
        lqw.orderByDesc(SoQuotation::getCreateTime);
        return lqw;
    }
}
