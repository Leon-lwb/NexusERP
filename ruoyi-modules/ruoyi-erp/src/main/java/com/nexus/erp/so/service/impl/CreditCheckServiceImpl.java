package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdClientCredit;
import com.nexus.erp.bd.mapper.BdClientCreditMapper;
import com.nexus.erp.so.domain.SoCreditUsage;
import com.nexus.erp.so.mapper.SoCreditUsageMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 信用检查Service实现类，负责信用额度校验、冻结和释放
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class CreditCheckServiceImpl implements ICreditCheckService {

    private final SoCreditUsageMapper creditUsageMapper;
    private final BdClientCreditMapper bdClientCreditMapper;

    /**
     * 信用检查并冻结额度，若信用不足则抛出异常
     *
     * @param clientId    客户ID
     * @param soId        销售订单ID
     * @param orderAmount 订单金额
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndFreeze(Long clientId, Long soId, BigDecimal orderAmount) {
        // ERP-BD-09: bd_client_credit 的 (client_id, tenant_id) 唯一键新装定义见
        // script/sql/ry-erp.sql。为兼容尚未完成相应唯一约束迁移的存量库
        // (历史上可由手工 SQL 引入同客户多行信用记录), 这里不使用 selectOne —— 多行会抛
        // TooManyResultsException 导致销售订单确认 500; 改为按 credit_id 倒序取最新一行并对重复行告警。
        List<BdClientCredit> credits = bdClientCreditMapper.selectList(
            Wrappers.<BdClientCredit>lambdaQuery()
                .eq(BdClientCredit::getClientId, clientId)
                .orderByDesc(BdClientCredit::getCreditId));
        if (credits.size() > 1) {
            log.warn("客户[{}]存在 {} 条信用额度记录, 取最新一条(creditId={}); 请执行增量脚本补唯一键并清理重复行",
                clientId, credits.size(), credits.get(0).getCreditId());
        }
        BdClientCredit credit = credits.isEmpty() ? null : credits.get(0);

        if (credit == null || credit.getClientCredit() == null) {
            log.info("客户未配置信用额度，跳过信用检查, clientId={}", clientId);
            return;
        }

        BigDecimal usedAmount = getUsedAmount(clientId);
        BigDecimal creditLimit = BigDecimal.valueOf(credit.getClientCredit());

        if (usedAmount.add(orderAmount).compareTo(creditLimit) > 0) {
            log.warn("信用额度不足, clientId={}, 额度={}, 已用={}, 本次={}",
                clientId, creditLimit, usedAmount, orderAmount);
            throw new ServiceException("信用额度不足（额度:" + creditLimit
                + ", 已用:" + usedAmount + ", 本次:" + orderAmount + "）");
        }

        BigDecimal warningThreshold = creditLimit.multiply(new BigDecimal("0.8"));
        if (usedAmount.add(orderAmount).compareTo(warningThreshold) > 0) {
            log.warn("信用额度使用超过80%, clientId={}, 额度={}, 已用={}, 本次={}",
                clientId, creditLimit, usedAmount, orderAmount);
        }

        // 幂等防重：该订单已有 FROZEN 记录则跳过（防止订单重复确认/并发双击导致额度双占）
        Long frozenCount = creditUsageMapper.selectCount(Wrappers.<SoCreditUsage>lambdaQuery()
            .eq(SoCreditUsage::getSoId, soId)
            .eq(SoCreditUsage::getStatus, "FROZEN"));
        if (frozenCount != null && frozenCount > 0) {
            log.info("订单[{}]信用额度已冻结,跳过重复冻结", soId);
            return;
        }

        SoCreditUsage usage = new SoCreditUsage();
        usage.setClientId(clientId);
        usage.setSoId(soId);
        usage.setAmount(orderAmount);
        usage.setStatus("FROZEN");
        usage.setFreezeTime(new Date());
        creditUsageMapper.insert(usage);

        log.info("信用额度冻结成功, clientId={}, soId={}, amount={}", clientId, soId, orderAmount);
    }

    /**
     * 释放已冻结/已用的信用额度（close/cancel 调用）。
     * <p>FROZEN（未交付承诺）与 USED（已交付应收）一并释放回 RELEASED。</p>
     *
     * @param soId 销售订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long soId) {
        LambdaQueryWrapper<SoCreditUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(SoCreditUsage::getSoId, soId);
        lqw.in(SoCreditUsage::getStatus, "FROZEN", "USED");
        List<SoCreditUsage> usageList = creditUsageMapper.selectList(lqw);

        for (SoCreditUsage usage : usageList) {
            usage.setStatus("RELEASED");
            usage.setReleaseTime(new Date());
            creditUsageMapper.updateById(usage);
        }

        if (!usageList.isEmpty()) {
            log.info("信用额度释放成功, soId={}, 释放记录数={}", soId, usageList.size());
        }
    }

    /**
     * 将已冻结的信用额度转为已用（fulfill 调用）。
     * <p>订单已交付形成应收：FROZEN → USED，记录 usedTime。信用额度仍占用，
     * 待 close/cancel 时才释放。</p>
     *
     * @param soId 销售订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void convertToUsed(Long soId) {
        LambdaQueryWrapper<SoCreditUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(SoCreditUsage::getSoId, soId);
        lqw.eq(SoCreditUsage::getStatus, "FROZEN");
        List<SoCreditUsage> usageList = creditUsageMapper.selectList(lqw);

        for (SoCreditUsage usage : usageList) {
            usage.setStatus("USED");
            usage.setUsedTime(new Date());
            creditUsageMapper.updateById(usage);
        }

        if (!usageList.isEmpty()) {
            log.info("信用额度转为已用, soId={}, 记录数={}", soId, usageList.size());
        }
    }

    /**
     * 获取客户已占用的信用额度总额（FROZEN + USED）。
     * <p>未交付承诺(FROZEN)与已交付应收(USED)均占用信用额度。</p>
     *
     * @param clientId 客户ID
     * @return 已占用额度
     */
    @Override
    public BigDecimal getUsedAmount(Long clientId) {
        // ERP-SO-08: 改为数据库端 SUM, 避免把该客户全部占用记录拉进内存循环累加
        // (so_credit_usage 随订单线性增长)。聚合查询走 MP 包装器, 由多租户插件自动补 tenant_id 条件。
        List<Map<String, Object>> rows = creditUsageMapper.selectMaps(Wrappers.<SoCreditUsage>query()
            .select("COALESCE(SUM(amount), 0) AS total")
            .eq("client_id", clientId)
            .in("status", "FROZEN", "USED"));
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return BigDecimal.ZERO;
        }
        Object total = rows.get(0).get("total");
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total instanceof BigDecimal decimal ? decimal : new BigDecimal(total.toString());
    }
}
