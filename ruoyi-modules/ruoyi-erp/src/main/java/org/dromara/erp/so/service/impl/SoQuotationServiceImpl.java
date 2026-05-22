package org.dromara.erp.so.service.impl;

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
import org.dromara.erp.so.domain.SoQuotation;
import org.dromara.erp.so.domain.SoQuotationLine;
import org.dromara.erp.so.domain.SoSalesOrderHeader;
import org.dromara.erp.so.domain.SoSalesOrderLine;
import org.dromara.erp.so.domain.bo.SoQuotationBo;
import org.dromara.erp.so.domain.vo.SoQuotationVo;
import org.dromara.erp.so.mapper.SoQuotationLineMapper;
import org.dromara.erp.so.mapper.SoQuotationMapper;
import org.dromara.erp.so.mapper.SoSalesOrderHeaderMapper;
import org.dromara.erp.so.mapper.SoSalesOrderLineMapper;
import org.dromara.erp.so.service.ISoQuotationService;
import org.dromara.erp.so.service.ISoSalesOrderHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoQuotationServiceImpl implements ISoQuotationService {

    private final SoQuotationMapper baseMapper;
    private final SoQuotationLineMapper lineMapper;
    private final SoSalesOrderHeaderMapper salesOrderMapper;
    private final SoSalesOrderLineMapper salesOrderLineMapper;

    @Override
    public TableDataInfo<SoQuotationVo> selectPageList(SoQuotationBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoQuotation> lqw = buildQueryWrapper(bo);
        Page<SoQuotationVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SoQuotationVo> selectList(SoQuotationBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoQuotationBo bo) {
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getQuotationId(), bo.getSoQuotationLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoQuotationBo bo) {
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        deleteLinesByQuotationId(entity.getQuotationId());
        insertOrUpdateLines(entity.getQuotationId(), bo.getSoQuotationLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByQuotationId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

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
