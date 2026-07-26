package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.tg.domain.TgMaintenancePlan;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;
import com.nexus.mes.tg.domain.TgTooling;
import com.nexus.mes.tg.domain.TgUsageRecord;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.bo.TgToolingBo;
import com.nexus.mes.tg.domain.vo.TgMaintenancePlanVo;
import com.nexus.mes.tg.domain.vo.TgToolingVo;
import com.nexus.mes.tg.mapper.TgMaintenancePlanMapper;
import com.nexus.mes.tg.mapper.TgMaintenanceRecordMapper;
import com.nexus.mes.tg.mapper.TgToolingMapper;
import com.nexus.mes.tg.mapper.TgUsageRecordMapper;
import com.nexus.mes.tg.service.ITgMaintenancePlanService;
import com.nexus.mes.tg.service.ITgToolingService;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES工装 Service 业务层实现
 * <p>
 * 状态机: IDLE ⇄ IN_USE(领用/归还)
 *                 └-> MAINTENANCE -> IDLE(保养归零)
 *                 └-> SCRAPPED(终态)
 * 寿命双轨: used_count(次数) + used_hours(时长), 归还累加, 保养归零
 * 编码生成: autoCodeUtil.genSerialCode("MES_TOOLING_CODE", null) -> TG+yyyyMMdd+3位日循环
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgToolingService")
public class TgToolingServiceImpl implements ITgToolingService {

    private final TgToolingMapper baseMapper;
    private final TgUsageRecordMapper usageRecordMapper;
    private final TgMaintenanceRecordMapper maintenanceRecordMapper;
    private final TgMaintenancePlanMapper maintenancePlanMapper;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<TgToolingVo> selectPageList(TgToolingBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgTooling> lqw = buildQueryWrapper(bo);
        Page<TgToolingVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        page.getRecords().forEach(this::fillRemaining);
        return TableDataInfo.build(page);
    }

    @Override
    public TgToolingVo selectById(Long toolingId) {
        TgToolingVo vo = baseMapper.selectVoById(toolingId);
        if (vo != null) {
            fillRemaining(vo);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgToolingBo bo) {
        if (StringUtils.isBlank(bo.getToolingCode())) {
            bo.setToolingCode(generateToolingCode());
        }
        Long existCount = baseMapper.selectCount(Wrappers.<TgTooling>lambdaQuery()
            .eq(TgTooling::getToolingCode, bo.getToolingCode()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("工装编码已存在: " + bo.getToolingCode());
        }
        TgTooling t = new TgTooling();
        t.setToolingCode(bo.getToolingCode());
        t.setToolingName(bo.getToolingName());
        t.setToolingType(bo.getToolingType());
        t.setSpec(bo.getSpec());
        t.setWorkshopId(bo.getWorkshopId());
        t.setWorkshopName(bo.getWorkshopName());
        t.setStatus(STATUS_IDLE);
        t.setTotalLifeCount(bo.getTotalLifeCount());
        t.setTotalLifeHours(bo.getTotalLifeHours());
        t.setUsedCount(0);
        t.setUsedHours(BigDecimal.ZERO);
        t.setPurchaseDate(bo.getPurchaseDate());
        t.setPurchaseCost(bo.getPurchaseCost());
        t.setRemark(bo.getRemark());
        int rows = baseMapper.insert(t);
        log.info("新增工装成功: code={}, type={}", bo.getToolingCode(), bo.getToolingType());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgToolingBo bo) {
        TgTooling t = baseMapper.selectById(bo.getToolingId());
        if (t == null) {
            throw new RuntimeException("工装不存在: " + bo.getToolingId());
        }
        // 仅 IDLE 可编辑;used_count/used_hours/status 不允许通过编辑修改(走专用接口)
        if (!STATUS_IDLE.equals(t.getStatus())) {
            throw new RuntimeException("仅闲置状态可编辑,当前: " + t.getStatus());
        }
        t.setToolingName(bo.getToolingName());
        t.setToolingType(bo.getToolingType());
        t.setSpec(bo.getSpec());
        t.setWorkshopId(bo.getWorkshopId());
        t.setWorkshopName(bo.getWorkshopName());
        t.setTotalLifeCount(bo.getTotalLifeCount());
        t.setTotalLifeHours(bo.getTotalLifeHours());
        t.setPurchaseDate(bo.getPurchaseDate());
        t.setPurchaseCost(bo.getPurchaseCost());
        t.setRemark(bo.getRemark());
        int rows = baseMapper.updateById(t);
        log.info("修改工装成功: toolingId={}", bo.getToolingId());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        // 孤儿守卫: 存在使用/保养记录则禁止删除(无 FK/级联,删除会断裂追溯链)
        Long usageCount = usageRecordMapper.selectCount(Wrappers.<TgUsageRecord>lambdaQuery()
            .in(TgUsageRecord::getToolingId, idList));
        if (usageCount != null && usageCount > 0) {
            throw new RuntimeException("存在使用记录,不可删除(追溯完整性)");
        }
        Long planCount = maintenancePlanMapper.selectCount(Wrappers.<TgMaintenancePlan>lambdaQuery()
            .in(TgMaintenancePlan::getToolingId, idList));
        if (planCount != null && planCount > 0) {
            throw new RuntimeException("存在保养计划,不可删除");
        }
        Long recordCount = maintenanceRecordMapper.selectCount(Wrappers.<TgMaintenanceRecord>lambdaQuery()
            .in(TgMaintenanceRecord::getToolingId, idList));
        if (recordCount != null && recordCount > 0) {
            throw new RuntimeException("存在保养记录,不可删除(追溯完整性)");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除工装: count={}", ids.length);
        return rows > 0;
    }

    @Override
    @Lock4j(keys = {"#toolingId"})
    @Transactional(rollbackFor = Exception.class)
    public Boolean checkout(Long toolingId, TgCheckoutBo bo) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (!STATUS_IDLE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装非闲置状态,不可领用: " + tooling.getStatus());
        }
        // CAS IDLE -> IN_USE
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_IN_USE)
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_IDLE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,领用失败");
        }
        // 写 tg_usage_record(status=OUT)
        TgUsageRecord rec = new TgUsageRecord();
        rec.setToolingId(toolingId);
        rec.setOperatorId(bo.getOperatorId());
        rec.setOperatorName(bo.getOperatorName());
        rec.setWoId(bo.getWoId());
        rec.setWoNo(bo.getWoNo());
        rec.setWorkstationId(bo.getWorkstationId());
        rec.setWorkstationName(bo.getWorkstationName());
        rec.setCheckoutTime(new Date());
        rec.setUsageCount(1);
        rec.setStatus(USAGE_OUT);
        rec.setRemark(bo.getRemark());
        usageRecordMapper.insert(rec);
        log.info("工装领用: toolingId={}, operatorId={}", toolingId, bo.getOperatorId());
        return true;
    }

    @Override
    @Lock4j(keys = {"#toolingId"})
    @Transactional(rollbackFor = Exception.class)
    public Boolean doReturn(Long toolingId, Integer usageCount, BigDecimal usageHours, String remark) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (!STATUS_IN_USE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装非使用中,不可归还: " + tooling.getStatus());
        }
        // 找到该工装最近的 OUT 记录(未归还)
        TgUsageRecord rec = usageRecordMapper.selectOne(Wrappers.<TgUsageRecord>lambdaQuery()
            .eq(TgUsageRecord::getToolingId, toolingId)
            .eq(TgUsageRecord::getStatus, USAGE_OUT)
            .orderByDesc(TgUsageRecord::getCheckoutTime)
            .last("LIMIT 1"));
        if (rec == null) {
            throw new RuntimeException("未找到领出记录,无法归还");
        }
        // 更新 usage_record(RETURNED + return_time + usage_count/hours)
        usageRecordMapper.update(null, Wrappers.<TgUsageRecord>lambdaUpdate()
            .set(TgUsageRecord::getStatus, USAGE_RETURNED)
            .set(TgUsageRecord::getReturnTime, new Date())
            .set(usageCount != null, TgUsageRecord::getUsageCount, usageCount)
            .set(usageHours != null, TgUsageRecord::getUsageHours, usageHours)
            .set(StringUtils.isNotBlank(remark), TgUsageRecord::getRemark, remark)
            .eq(TgUsageRecord::getUsageId, rec.getUsageId()));
        // 累加 tg_tooling.used_count / used_hours
        int addCount = usageCount == null ? 0 : usageCount;
        BigDecimal addHours = usageHours == null ? BigDecimal.ZERO : usageHours;
        int newUsedCount = (tooling.getUsedCount() == null ? 0 : tooling.getUsedCount()) + addCount;
        BigDecimal newUsedHours = (tooling.getUsedHours() == null ? BigDecimal.ZERO : tooling.getUsedHours()).add(addHours);
        // CAS IN_USE -> IDLE + 累加计数
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_IDLE)
            .set(TgTooling::getUsedCount, newUsedCount)
            .set(TgTooling::getUsedHours, newUsedHours)
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_IN_USE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,归还失败");
        }
        log.info("工装归还: toolingId={}, addCount={}, addHours={}, newUsedCount={}",
            toolingId, addCount, addHours, newUsedCount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean startMaintain(Long toolingId, String remark) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (STATUS_IN_USE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装使用中,请先归还再保养");
        }
        if (STATUS_SCRAPPED.equals(tooling.getStatus())) {
            throw new RuntimeException("工装已报废,不可保养");
        }
        if (STATUS_MAINTENANCE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装已在保养中");
        }
        // CAS IDLE -> MAINTENANCE
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_MAINTENANCE)
            .set(StringUtils.isNotBlank(remark), TgTooling::getRemark, remark)
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_IDLE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,开始保养失败");
        }
        log.info("工装开始保养: toolingId={}", toolingId);
        return true;
    }

    @Override
    @Lock4j(keys = {"#toolingId"})
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeMaintain(Long toolingId, TgMaintenanceRecordBo bo) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (!STATUS_MAINTENANCE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装非保养中,无法完成保养: " + tooling.getStatus());
        }
        // 快照保养前计数
        int beforeCount = tooling.getUsedCount() == null ? 0 : tooling.getUsedCount();
        BigDecimal beforeHours = tooling.getUsedHours() == null ? BigDecimal.ZERO : tooling.getUsedHours();
        Date now = new Date();
        // 归零 + 状态 MAINTENANCE -> IDLE + last_maintain_time
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_IDLE)
            .set(TgTooling::getUsedCount, 0)
            .set(TgTooling::getUsedHours, BigDecimal.ZERO)
            .set(TgTooling::getLastMaintainTime, now)
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_MAINTENANCE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,完成保养失败");
        }
        // 写保养记录(before 快照 + after=0)
        TgMaintenanceRecord rec = new TgMaintenanceRecord();
        rec.setToolingId(toolingId);
        rec.setPlanId(bo.getPlanId());
        rec.setMaintainType(bo.getMaintainType() == null ? "PLANNED" : bo.getMaintainType());
        rec.setMaintainTime(bo.getMaintainTime() == null ? now : bo.getMaintainTime());
        rec.setMaintainerId(bo.getMaintainerId() != null ? bo.getMaintainerId() : LoginHelper.getUserId());
        rec.setMaintainerName(bo.getMaintainerName());
        rec.setUsedCountBefore(beforeCount);
        rec.setUsedCountAfter(0);
        rec.setUsedHoursBefore(beforeHours);
        rec.setUsedHoursAfter(BigDecimal.ZERO);
        rec.setDescription(bo.getDescription());
        rec.setCost(bo.getCost());
        rec.setRemark(bo.getRemark());
        maintenanceRecordMapper.insert(rec);
        // 更新关联计划的 last_maintain_time / next 阈值
        if (bo.getPlanId() != null) {
            updatePlanAfterMaintain(bo.getPlanId(), now);
        }
        log.info("工装完成保养(归零): toolingId={}, beforeCount={}, beforeHours={}",
            toolingId, beforeCount, beforeHours);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean scrap(Long toolingId, String reason) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (STATUS_IN_USE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装使用中,请先归还再报废");
        }
        if (STATUS_SCRAPPED.equals(tooling.getStatus())) {
            throw new RuntimeException("工装已报废");
        }
        // CAS IDLE/MAINTENANCE -> SCRAPPED
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_SCRAPPED)
            .set(StringUtils.isNotBlank(reason), TgTooling::getRemark, reason)
            .eq(TgTooling::getToolingId, toolingId)
            .in(TgTooling::getStatus, STATUS_IDLE, STATUS_MAINTENANCE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,报废失败");
        }
        log.info("工装报废: toolingId={}, reason={}", toolingId, reason);
        return true;
    }

    /**
     * 校验工装可用性(契约方法: P0-3 进站出站校验调用)
     * <p>当前 P0-3 未建,此方法作为门禁入口预留。校验: 存在 + status=IDLE。</p>
     */
    @Override
    public Map<String, Object> checkToolingAvailable(Long toolingId) {
        Map<String, Object> result = new HashMap<>();
        result.put("usable", false);
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            result.put("reason", "工装不存在: " + toolingId);
            return result;
        }
        result.put("toolingId", tooling.getToolingId());
        result.put("toolingCode", tooling.getToolingCode());
        result.put("status", tooling.getStatus());
        if (STATUS_SCRAPPED.equals(tooling.getStatus())) {
            result.put("reason", "工装已报废");
            return result;
        }
        if (STATUS_MAINTENANCE.equals(tooling.getStatus())) {
            result.put("reason", "工装保养中");
            return result;
        }
        if (STATUS_IN_USE.equals(tooling.getStatus())) {
            result.put("reason", "工装使用中");
            return result;
        }
        if (!STATUS_IDLE.equals(tooling.getStatus())) {
            result.put("reason", "工装状态非闲置: " + tooling.getStatus());
            return result;
        }
        result.put("usable", true);
        result.put("reason", "可用");
        return result;
    }

    @Override
    public List<TgToolingVo> listMaintenanceDue() {
        List<TgToolingVo> all = baseMapper.selectVoList(Wrappers.<TgTooling>lambdaQuery()
            .in(TgTooling::getStatus, STATUS_IDLE, STATUS_IN_USE));
        List<TgToolingVo> due = new ArrayList<>();
        Date now = new Date();
        for (TgToolingVo vo : all) {
            fillRemaining(vo);
            if (isMaintenanceDue(vo, now)) {
                due.add(vo);
            }
        }
        return due;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成工装编码: 走系统自动编码规则 MES_TOOLING_CODE (TG + yyyyMMdd + 3位日循环流水号)
     * <p>对齐 P0-9 MES_LOT_CODE 模式: synchronized + REQUIRES_NEW 落 sys_auto_code_result,
     * 流水号日循环归零, uk_tooling_code 兜底。</p>
     */
    private String generateToolingCode() {
        return autoCodeUtil.genSerialCode("MES_TOOLING_CODE", null);
    }

    /**
     * 填充剩余寿命(次数/小时, 非库列)
     */
    private void fillRemaining(TgToolingVo vo) {
        if (vo == null) {
            return;
        }
        if (vo.getTotalLifeCount() != null) {
            int used = vo.getUsedCount() == null ? 0 : vo.getUsedCount();
            vo.setRemainingCount(Math.max(0, vo.getTotalLifeCount() - used));
        }
        if (vo.getTotalLifeHours() != null) {
            BigDecimal used = vo.getUsedHours() == null ? BigDecimal.ZERO : vo.getUsedHours();
            BigDecimal rem = vo.getTotalLifeHours().subtract(used);
            vo.setRemainingHours(rem.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rem);
        }
    }

    /**
     * 判断工装是否保养到期(查已启用计划,按 trigger_type 比较阈值)
     */
    private boolean isMaintenanceDue(TgToolingVo vo, Date now) {
        List<TgMaintenancePlanVo> plans = maintenancePlanMapper.selectVoList(
            Wrappers.<TgMaintenancePlan>lambdaQuery()
                .eq(TgMaintenancePlan::getToolingId, vo.getToolingId())
                .eq(TgMaintenancePlan::getEnabled, "1"));
        int usedCount = vo.getUsedCount() == null ? 0 : vo.getUsedCount();
        BigDecimal usedHours = vo.getUsedHours() == null ? BigDecimal.ZERO : vo.getUsedHours();
        for (TgMaintenancePlanVo plan : plans) {
            String tt = plan.getTriggerType();
            Integer tv = plan.getTriggerValue();
            if (ITgMaintenancePlanService.TRIGGER_BY_COUNT.equals(tt) && plan.getNextMaintainCount() != null
                && usedCount >= plan.getNextMaintainCount()) {
                return true;
            }
            if (ITgMaintenancePlanService.TRIGGER_BY_HOURS.equals(tt) && plan.getNextMaintainHours() != null
                && usedHours.compareTo(plan.getNextMaintainHours()) >= 0) {
                return true;
            }
            if (ITgMaintenancePlanService.TRIGGER_PERIODIC.equals(tt) && plan.getNextMaintainTime() != null
                && plan.getNextMaintainTime().before(now) && tv != null && tv > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 保养完成后更新关联计划: last_maintain_time + 重算 next 阈值/时间
     * <p>归零后: BY_COUNT 下次到期=trigger_value; BY_HOURS 下次到期=trigger_value;
     * PERIODIC 下次=now+trigger_value 天。</p>
     */
    private void updatePlanAfterMaintain(Long planId, Date now) {
        TgMaintenancePlan plan = maintenancePlanMapper.selectById(planId);
        if (plan == null) {
            return;
        }
        Integer tv = plan.getTriggerValue();
        Integer newNextCount = plan.getNextMaintainCount();
        BigDecimal newNextHours = plan.getNextMaintainHours();
        Date newNextTime = plan.getNextMaintainTime();
        String tt = plan.getTriggerType();
        if (ITgMaintenancePlanService.TRIGGER_BY_COUNT.equals(tt) && tv != null) {
            newNextCount = tv;
        } else if (ITgMaintenancePlanService.TRIGGER_BY_HOURS.equals(tt) && tv != null) {
            newNextHours = BigDecimal.valueOf(tv);
        } else if (ITgMaintenancePlanService.TRIGGER_PERIODIC.equals(tt) && tv != null && tv > 0) {
            newNextTime = new Date(now.getTime() + (long) tv * 24 * 60 * 60 * 1000L);
        }
        maintenancePlanMapper.update(null, Wrappers.<TgMaintenancePlan>lambdaUpdate()
            .set(TgMaintenancePlan::getLastMaintainTime, now)
            .set(newNextCount != null, TgMaintenancePlan::getNextMaintainCount, newNextCount)
            .set(newNextHours != null, TgMaintenancePlan::getNextMaintainHours, newNextHours)
            .set(newNextTime != null, TgMaintenancePlan::getNextMaintainTime, newNextTime)
            .eq(TgMaintenancePlan::getPlanId, planId));
    }

    private LambdaQueryWrapper<TgTooling> buildQueryWrapper(TgToolingBo bo) {
        return Wrappers.<TgTooling>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getToolingCode()), TgTooling::getToolingCode, bo.getToolingCode())
            .like(StringUtils.isNotBlank(bo.getToolingName()), TgTooling::getToolingName, bo.getToolingName())
            .eq(StringUtils.isNotBlank(bo.getToolingType()), TgTooling::getToolingType, bo.getToolingType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), TgTooling::getStatus, bo.getStatus())
            .eq(bo.getWorkshopId() != null, TgTooling::getWorkshopId, bo.getWorkshopId())
            .orderByDesc(TgTooling::getCreateTime);
    }
}
