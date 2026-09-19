package com.nexus.erp.so.service.impl;

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
import com.nexus.erp.so.domain.SoQuotation;
import com.nexus.erp.so.domain.SoQuotationLine;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationLineVo;
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
import java.math.RoundingMode;
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

    /** 百分数换算基数（税率以「百分数」存储：13 表示 13%） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
            // 转 VO 返回，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端
            vo.setSoQuotationLineList(MapstructUtils.convert(lineMapper.selectList(lineLqw), SoQuotationLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售报价（强制草稿 + 自动生成报价单号 + 白名单落库明细行）。
     * <p>状态由服务端强制为 {@code DRAFT}：报价状态只能经 confirm / convertToSalesOrder 流转，
     * 不接受客户端直写（原实现直接落客户端传入的 status）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoQuotationBo bo) {
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        entity.setStatus(STATUS_DRAFT);
        if (StringUtils.isBlank(entity.getQuotationNo())) {
            entity.setQuotationNo(autoCodeUtil.genSerialCode("SO_QUOTATION_CODE", null));
        }
        List<SoQuotationLine> lines = normalizeLines(bo.getSoQuotationLineList());
        applyTotals(entity, lines);
        int rows = baseMapper.insert(entity);
        insertLines(entity.getQuotationId(), lines);
        return rows;
    }

    /**
     * 修改销售报价（仅草稿可改；单号与状态服务端权威；明细行白名单重建）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoQuotationBo bo) {
        if (bo.getQuotationId() == null) {
            throw new ServiceException("报价单ID不能为空");
        }
        SoQuotation existing = baseMapper.selectById(bo.getQuotationId());
        if (existing == null) {
            throw new ServiceException("报价单不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("只有草稿状态的报价单才能修改，当前状态：" + existing.getStatus());
        }
        SoQuotation entity = MapstructUtils.convert(bo, SoQuotation.class);
        entity.setQuotationNo(existing.getQuotationNo());
        entity.setStatus(existing.getStatus());
        List<SoQuotationLine> lines = normalizeLines(bo.getSoQuotationLineList());
        applyTotals(entity, lines);
        deleteLinesByQuotationId(entity.getQuotationId());
        insertLines(entity.getQuotationId(), lines);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售报价（仅草稿可删除，同时删除明细行）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            SoQuotation existing = baseMapper.selectById(id);
            if (existing == null) {
                continue;
            }
            if (!STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("只有草稿状态的报价单才能删除，单号 "
                    + existing.getQuotationNo() + " 当前状态：" + existing.getStatus());
            }
        }
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
            throw new ServiceException("报价单不存在");
        }
        if (!STATUS_DRAFT.equals(quotation.getStatus())) {
            throw new ServiceException("只有草稿状态的报价单才能确认，当前状态：" + quotation.getStatus());
        }
        quotation.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(quotation);
    }

    /**
     * 将销售报价转换为销售订单（仅「已确认」可转，且不可重复转换）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToSalesOrder(Long quotationId) {
        SoQuotation quotation = baseMapper.selectById(quotationId);
        if (quotation == null) {
            throw new ServiceException("报价单不存在");
        }
        if (STATUS_CONVERTED.equals(quotation.getStatus())) {
            throw new ServiceException("报价单已转换，不能重复转换");
        }
        // 原实现只拦 CONVERTED，草稿可跳过 confirm 直接转单，绕过报价确认环节
        if (!STATUS_CONFIRMED.equals(quotation.getStatus())) {
            throw new ServiceException("只有已确认状态的报价单才能转换为销售订单，当前状态：" + quotation.getStatus());
        }

        List<SoQuotationLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(SoQuotationLine.class)
                .eq(SoQuotationLine::getQuotationId, quotationId));

        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("报价单无明细行");
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

    /**
     * 明细行白名单重建：逐字段拷贝可编辑字段，丢弃客户端传入的 {@code lineId / tenantId / delFlag /
     * createBy / createTime / updateBy / updateTime}，并校验数量与单价非负、税率在 0~100，
     * 最后按「金额 = 数量 × 单价、税额 = 金额 × 税率% / 100」服务端重算。
     *
     * <p><b>为什么必须白名单</b>：{@code SoQuotationBo.soQuotationLineList} 直接复用实体类型
     * {@code List<SoQuotationLine>}，若原样落库，客户端可注入 {@code tenantId} 把明细写进其它租户
     * —— 多租户插件不会覆盖 insert 语句中已显式出现的 {@code tenant_id} 列。
     * 合同侧（{@code SoContractServiceImpl.normalizeLines}）已是该写法，此处对齐。</p>
     *
     * @param lines 客户端提交的明细行（可为 null，表示不处理）
     * @return 净化后的明细行列表（入参 null 时返回 null）
     */
    private List<SoQuotationLine> normalizeLines(List<SoQuotationLine> lines) {
        if (lines == null) {
            return null;
        }
        List<SoQuotationLine> normalized = new ArrayList<>(lines.size());
        int seq = 1;
        for (SoQuotationLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getQuantity() != null && line.getQuantity().signum() < 0) {
                throw new ServiceException("报价明细数量不能为负数");
            }
            if (line.getUnitPrice() != null && line.getUnitPrice().signum() < 0) {
                throw new ServiceException("报价明细单价不能为负数");
            }
            if (line.getTaxRate() != null
                && (line.getTaxRate().signum() < 0 || line.getTaxRate().compareTo(HUNDRED) > 0)) {
                throw new ServiceException("报价明细税率必须在 0~100 之间");
            }
            SoQuotationLine clean = new SoQuotationLine();
            clean.setMaterialId(line.getMaterialId());
            clean.setMaterialCode(line.getMaterialCode());
            clean.setMaterialName(line.getMaterialName());
            clean.setMaterialSpec(line.getMaterialSpec());
            clean.setBaseUomId(line.getBaseUomId());
            clean.setBaseUomName(line.getBaseUomName());
            clean.setQuantity(line.getQuantity());
            clean.setUnitPrice(line.getUnitPrice());
            clean.setTaxRate(line.getTaxRate());
            clean.setDeliveryDate(line.getDeliveryDate());
            clean.setSortOrder(line.getSortOrder() != null ? line.getSortOrder() : seq);
            clean.setRemark(line.getRemark());
            clean.setAttr1(line.getAttr1());
            clean.setAttr2(line.getAttr2());
            clean.setAttr3(line.getAttr3());
            clean.setAttr4(line.getAttr4());
            if (clean.getQuantity() != null && clean.getUnitPrice() != null) {
                BigDecimal amount = clean.getUnitPrice().multiply(clean.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);
                clean.setAmount(amount);
                clean.setTaxAmount(clean.getTaxRate() == null ? BigDecimal.ZERO
                    : amount.multiply(clean.getTaxRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            } else {
                clean.setAmount(BigDecimal.ZERO);
                clean.setTaxAmount(BigDecimal.ZERO);
            }
            normalized.add(clean);
            seq++;
        }
        return normalized;
    }

    /**
     * 明细非空时以行合计覆盖表头金额（总金额 = Σ行金额，税额 = Σ行税额），
     * 避免客户端伪造表头金额导致报价与明细不一致。
     *
     * @param entity 报价单实体（原地覆盖 totalAmount / taxAmount）
     * @param lines  净化后的明细行（null 或空表示本次未提交明细，保持原值不动）
     */
    private void applyTotals(SoQuotation entity, List<SoQuotationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SoQuotationLine line : lines) {
            if (line.getAmount() != null) {
                amount = amount.add(line.getAmount());
            }
            if (line.getTaxAmount() != null) {
                tax = tax.add(line.getTaxAmount());
            }
        }
        entity.setTotalAmount(amount);
        entity.setTaxAmount(tax);
    }

    /**
     * 落库净化后的明细行：归属到报价单并逐条插入。
     *
     * @param quotationId 报价单ID
     * @param lines       已净化的明细行
     */
    private void insertLines(Long quotationId, List<SoQuotationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (SoQuotationLine line : lines) {
            line.setQuotationId(quotationId);
            lineMapper.insert(line);
        }
    }

    /**
     * 按报价单ID删除其所有明细行。
     *
     * @param quotationId 报价单ID
     */
    private void deleteLinesByQuotationId(Long quotationId) {
        if (quotationId != null) {
            LambdaQueryWrapper<SoQuotationLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoQuotationLine::getQuotationId, quotationId);
            lineMapper.delete(lqw);
        }
    }

    /**
     * 构建报价单分页/列表查询条件（按单号、客户、状态动态拼接，按创建时间倒序）。
     *
     * @param bo 查询参数
     * @return 查询条件构造器
     */
    private LambdaQueryWrapper<SoQuotation> buildQueryWrapper(SoQuotationBo bo) {
        LambdaQueryWrapper<SoQuotation> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getQuotationNo()), SoQuotation::getQuotationNo, bo.getQuotationNo());
        lqw.eq(bo.getClientId() != null, SoQuotation::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoQuotation::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoQuotation::getStatus, bo.getStatus());
        // 关键字模糊搜索：报价单号 或 客户名称 任一匹配（移动端单输入框搜索）
        lqw.and(StringUtils.isNotBlank(bo.getKeyword()),
            w -> w.like(SoQuotation::getQuotationNo, bo.getKeyword())
                .or()
                .like(SoQuotation::getClientName, bo.getKeyword()));
        lqw.orderByDesc(SoQuotation::getCreateTime);
        return lqw;
    }
}
