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
        LambdaQueryWrapper<BdClientCredit> creditLqw = Wrappers.lambdaQuery();
        creditLqw.eq(BdClientCredit::getClientId, clientId);
        BdClientCredit credit = bdClientCreditMapper.selectOne(creditLqw);

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
     * 释放已冻结的信用额度
     *
     * @param soId 销售订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long soId) {
        LambdaQueryWrapper<SoCreditUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(SoCreditUsage::getSoId, soId);
        lqw.eq(SoCreditUsage::getStatus, "FROZEN");
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
     * 获取客户已冻结的信用额度总额
     *
     * @param clientId 客户ID
     * @return 已冻结额度
     */
    @Override
    public BigDecimal getUsedAmount(Long clientId) {
        LambdaQueryWrapper<SoCreditUsage> lqw = Wrappers.lambdaQuery();
        lqw.eq(SoCreditUsage::getClientId, clientId);
        lqw.eq(SoCreditUsage::getStatus, "FROZEN");
        List<SoCreditUsage> usageList = creditUsageMapper.selectList(lqw);

        BigDecimal total = BigDecimal.ZERO;
        for (SoCreditUsage usage : usageList) {
            if (usage.getAmount() != null) {
                total = total.add(usage.getAmount());
            }
        }
        return total;
    }
}
