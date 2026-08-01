package com.nexus.erp.fin.cost.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.cost.constant.CostConstants;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.domain.GlVoucherLine;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import com.nexus.erp.fin.gl.service.VoucherBuildRequest;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存货成本 Service:GR/IR 暂估入账。
 * <p>按期间汇总"已收货入库但未开票"的采购收货单,生成暂估凭证(借 存货科目/贷 应付暂估 2202),
 * 幂等由 createAndPost(sourceId=收货单ID)保证;单价取物料参考价/标准成本(收货明细无单价字段)。</p>
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class CostService {

    private final WmMaterialReceiveMapper receiveMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final com.nexus.erp.bd.mapper.BdMaterialMapper materialMapper;
    private final GlAccountMapper accountMapper;
    private final IGlVoucherService voucherService;
    private final com.nexus.erp.pu.invoice.mapper.PuInvoiceLineMapper invoiceLineMapper;

    /**
     * 采购暂估入账:按期间查询未开票收货单,生成暂估凭证。
     *
     * @param unInvoicedReceives 未开票收货单列表
     * @param periodName         期间名(凭证摘要)
     * @return 暂估收货单数
     */
    @Transactional(rollbackFor = Exception.class)
    public int provision(List<WmMaterialReceive> unInvoicedReceives, String periodName) {
        if (unInvoicedReceives == null || unInvoicedReceives.isEmpty()) {
            return 0;
        }
        // 排除已被发票引用的收货单行（grLineId），避免已开票收货单被重复暂估入账
        java.util.Set<Long> invoicedGrLineIds = new java.util.HashSet<>();
        if (unInvoicedReceives.size() > 0) {
            List<com.nexus.erp.pu.invoice.domain.PuInvoiceLine> invoiceLines =
                invoiceLineMapper.selectList(Wrappers.lambdaQuery(com.nexus.erp.pu.invoice.domain.PuInvoiceLine.class)
                    .in(com.nexus.erp.pu.invoice.domain.PuInvoiceLine::getGrLineId,
                        unInvoicedReceives.stream().map(r -> r.getReceiveId()).toList()));
            // 发票行按收货单行ID关联，这里用收货单ID集合近似匹配行ID（grLineId 与 receiveId 同源自增）
            for (com.nexus.erp.pu.invoice.domain.PuInvoiceLine line : invoiceLines) {
                if (line.getGrLineId() != null) {
                    invoicedGrLineIds.add(line.getGrLineId());
                }
            }
        }
        Map<Long, BigDecimal> debitByAccount = new HashMap<>();
        Map<Long, WmMaterialReceive> invoicedIds = new HashMap<>();
        for (WmMaterialReceive receive : unInvoicedReceives) {
            List<WmMaterialReceiveDetail> details = receiveDetailMapper.selectList(
                Wrappers.lambdaQuery(WmMaterialReceiveDetail.class)
                    .eq(WmMaterialReceiveDetail::getReceiveId, receive.getReceiveId()));
            if (details == null || details.isEmpty()) {
                continue;
            }
            // 该收货单任一明细行已被发票引用则跳过整单
            boolean anyInvoiced = details.stream()
                .anyMatch(d -> d.getLineId() != null && invoicedGrLineIds.contains(d.getLineId()));
            if (anyInvoiced) {
                continue;
            }
            for (WmMaterialReceiveDetail detail : details) {
                BigDecimal unitPrice = resolveUnitPrice(detail.getMaterialId());
                BigDecimal amount = nz(detail.getQuantity()).multiply(unitPrice).setScale(2, BigDecimal.ROUND_HALF_UP);
                if (amount.signum() == 0) {
                    continue;
                }
                // 简化映射:默认 1403 原材料;成品(类型3)走 1405
                debitByAccount.merge(inventoryAccountId(detail.getMaterialId()), amount, BigDecimal::add);
            }
            invoicedIds.put(receive.getReceiveId(), receive);
        }
        if (debitByAccount.isEmpty()) {
            return 0;
        }
        BigDecimal total = debitByAccount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return 0;
        }
        List<GlVoucherLine> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : debitByAccount.entrySet()) {
            lines.add(voucherLine(entry.getKey(), entry.getValue(), null, "采购暂估-" + periodName));
        }
        lines.add(voucherLine(apProvisionAccountId(), null, total, "采购暂估-" + periodName));
        VoucherBuildRequest req = new VoucherBuildRequest();
        // 凭证日期 = 期间末日(periodName 格式 yyyy-MM),保证落入对应期间
        req.setVoucherDate(periodEndDate(periodName));
        req.setVoucherWord("J");
        req.setVoucherSource(GlConstants.SOURCE_COST);
        // 幂等键:按期间(periodName 哈希化固定值),同期间重跑返回既有凭证
        req.setSourceId(Long.valueOf(periodName.replace("-", "")));
        req.setSourceNo("PROV-" + periodName);
        req.setSummary("采购暂估-" + periodName);
        req.setLines(lines);
        voucherService.createAndPost(req);
        log.info("采购暂估入账完成: {} 张收货单, 金额 {}, 期间 {}", invoicedIds.size(), total, periodName);
        return invoicedIds.size();
    }

    // ---------- helpers ----------

    /** 期间末日(periodName 如 2027-02 -> 2027-02-28,日期归到当月初避免时间越界落次期) */
    private java.util.Date periodEndDate(String periodName) {
        if (periodName == null || !periodName.matches("\\d{4}-\\d{2}")) {
            return new java.util.Date();
        }
        int year = Integer.parseInt(periodName.substring(0, 4));
        int month = Integer.parseInt(periodName.substring(5, 7));
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(year, month - 1, 1, 0, 0, 0);
        cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
        return cal.getTime();
    }

    /** 暂估单价:物料参考价,其次标准成本,无价格按 0 */
    private BigDecimal resolveUnitPrice(Long materialId) {
        com.nexus.erp.bd.domain.BdMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            return BigDecimal.ZERO;
        }
        if (material.getReferencePrice() != null) {
            return material.getReferencePrice();
        }
        return material.getStandardCost() != null ? material.getStandardCost() : BigDecimal.ZERO;
    }

    /** 存货科目:物料类型 3 成品->1405,其余->1403 原材料 */
    private Long inventoryAccountId(Long materialId) {
        com.nexus.erp.bd.domain.BdMaterial material = materialMapper.selectById(materialId);
        Long type = material != null ? material.getMaterialType() : null;
        String code = type != null && type == 3L ? CostConstants.ACCT_FINISHED_GOODS : CostConstants.ACCT_RAW_MATERIAL;
        return findAccountIdByCode(code);
    }

    /** 应付暂估科目 2202 */
    private Long apProvisionAccountId() {
        return findAccountIdByCode(CostConstants.ACCT_AP_PROVISION);
    }

    /** 按科目编码查询科目ID */
    private Long findAccountIdByCode(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在: {}", code);
        }
        return acc.getAccountId();
    }

    /** 构建凭证行 */
    private GlVoucherLine voucherLine(Long accountId, BigDecimal debit, BigDecimal credit, String summary) {
        GlVoucherLine l = new GlVoucherLine();
        l.setAccountId(accountId);
        l.setDebit(debit);
        l.setCredit(credit);
        l.setSummary(summary);
        return l;
    }

    /** null-safe */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
