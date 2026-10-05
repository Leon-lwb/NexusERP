package com.nexus.mes.pro.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.mapper.ProWorkOrderStepProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Objects;

/** 工单工序进度只由同租户完整执行来源覆盖重算，重建不是再次报工或完工。 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class ProWorkOrderStepProgressService {
    private final ProWorkOrderStepProgressMapper mapper;
    private final MesProgressTransactionGuard transactionGuard;

    /** 在原写事务中先取得工单父锁，禁止下游卡写者先占用跨卡执行行。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void lockForWorkOrder(Long woId) {
        lockParent(tenant(), woId);
    }

    /** 返回已验证的当前卡；后续业务判定不再用等待父锁之前的 RR 旧快照。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ProFlowCard lockForCard(Long cardId) {
        String tenant = tenant();
        require(positive(cardId), "流转卡ID无效");
        Long woId = mapper.findCardParent(tenant, cardId);
        lockParent(tenant, woId);
        ProFlowCard card = mapper.lockCard(tenant, cardId);
        require(card != null && tenant.equals(card.getTenantId()) && Objects.equals(woId, card.getMesWoId())
            && Objects.equals(cardId, card.getCardId()) && "0".equals(card.getDelFlag()), "锁定后的流转卡父来源不一致");
        return card;
    }

    /** 单件报工在 SN 锁之前取得父锁，避免工单完工/跨 SN 更新形成逆序。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long lockForSn(String sn) {
        String tenant = tenant();
        require(sn != null && !sn.isBlank(), "SN编码为空");
        List<Long> parents = mapper.findSnParents(tenant, sn);
        require(parents != null && parents.size() == 1 && positive(parents.get(0)), "SN工单来源缺失或不唯一");
        Long woId = parents.get(0);
        lockParent(tenant, woId);
        return woId;
    }

    /** 原卡数量或状态变更与步骤投影在同一实际 MES 事务内完成。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void rebuildForCard(Long cardId) {
        ProFlowCard card = lockForCard(cardId);
        rebuildLocked(tenant(), card.getMesWoId());
    }

    /** 独立受权重建只改步骤投影，原已入账工单/库存/报工/消息不重演。 */
    @Transactional(rollbackFor = Exception.class)
    public void rebuild(Long woId) {
        String tenant = tenant();
        lockParent(tenant, woId);
        rebuildLocked(tenant, woId);
    }

    private void rebuildLocked(String tenant, Long woId) {
        ProWorkOrder wo = lockParent(tenant, woId);
        List<ProWorkOrderStep> steps = mapper.lockWorkOrderSteps(tenant, woId);
        require(steps != null && !steps.isEmpty(), "工单步骤来源缺失，禁止猜零重建");
        var projections = WorkOrderStepProgressCalculator.calculate(wo, steps,
            mapper.lockCards(tenant, woId), mapper.lockCardSteps(tenant, woId));
        for (var p : projections) {
            ProWorkOrderStep old = steps.stream().filter(s -> Objects.equals(s.getStepId(), p.stepId())).findFirst().orElseThrow();
            // 同值重建保持原更新时间，不能让重复请求制造虚假的执行变更。
            if (same(old.getCompletedQty(), p.completedQty()) && same(old.getScrapQty(), p.scrapQty())
                && Objects.equals(old.getStepStatus(), p.stepStatus()) && sameDate(old.getStartTime(), p.startTime())
                && sameDate(old.getEndTime(), p.endTime())) continue;
            require(mapper.updateProgress(tenant, woId, p.stepId(), p.completedQty(), p.scrapQty(),
                p.stepStatus(), p.startTime(), p.endTime()) == 1, "工单步骤投影写入失败，原执行事务回滚");
        }
    }

    private ProWorkOrder lockParent(String tenant, Long woId) {
        require(positive(woId), "工单父来源无效");
        ProWorkOrder wo = mapper.lockWorkOrder(tenant, woId);
        require(wo != null && tenant.equals(wo.getTenantId()) && Objects.equals(woId, wo.getMesWoId())
            && "0".equals(wo.getDelFlag()), "工单父来源不存在或租户不匹配");
        return wo;
    }

    private String tenant() {
        require(TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            && "mes".equals(DynamicDataSourceContextHolder.peek()), "工序进度必须在实际 MES 写事务内更新");
        transactionGuard.requireMesConnection();
        String tenant = TenantHelper.getTenantId();
        require(tenant != null && tenant.matches("[0-9]{6,20}"), "可信租户上下文缺失");
        return tenant;
    }

    private boolean sameDate(java.util.Date a, java.util.Date b) { return a == null ? b == null : b != null && a.getTime() == b.getTime(); }
    private boolean positive(Long id) { return id != null && id > 0; }
    private boolean same(java.math.BigDecimal a, java.math.BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private void require(boolean ok, String message) { if (!ok) throw new ServiceException(message); }
}
