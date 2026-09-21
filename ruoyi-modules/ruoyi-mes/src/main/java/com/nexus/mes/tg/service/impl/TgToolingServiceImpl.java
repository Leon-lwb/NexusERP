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
import com.nexus.mes.tg.domain.bo.TgCalibrationBo;
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

import cn.hutool.core.date.DateUtil;

/**
 * MES工装 Service 业务层实现
 * <p>
 * 状态机: IDLE ⇄ IN_USE(领用/归还)
 *                 └-> CALIBRATING -> IDLE(校准登记, MES-TG-01)
 *                 └-> MAINTENANCE -> IDLE(保养归零)
 *                 └-> SCRAPPED(终态)
 * 寿命双轨: used_count(次数) + used_hours(时长), 归还累加, 保养归零
 * 校准维度: next_calibration_date 超期 + 保养计划到期 均在 checkToolingAvailable/checkout 拦截 (MES-TG-01/TG-02)
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
        // MES-TG-01: 校准维度(周期/上次校准日/下次校准日 + 校准结论)
        t.setCalibrationCycleDays(bo.getCalibrationCycleDays());
        t.setLastCalibrationDate(bo.getLastCalibrationDate());
        t.setNextCalibrationDate(resolveNextCalibration(bo.getCalibrationCycleDays(),
            bo.getLastCalibrationDate(), bo.getNextCalibrationDate()));
        t.setCalibrationResult(bo.getCalibrationResult());
        t.setCalibrationRemark(bo.getCalibrationRemark());
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
        // MES-TG-01: 校准周期/日期可编辑(仅 IDLE);下次校准日优先取显式传值, 否则按周期推算
        t.setCalibrationCycleDays(bo.getCalibrationCycleDays());
        t.setLastCalibrationDate(bo.getLastCalibrationDate());
        t.setNextCalibrationDate(resolveNextCalibration(bo.getCalibrationCycleDays(),
            bo.getLastCalibrationDate(), bo.getNextCalibrationDate()));
        t.setCalibrationResult(bo.getCalibrationResult());
        t.setCalibrationRemark(bo.getCalibrationRemark());
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
        // MES-TG-02: 领用前置同一套可用性校验(校准超期/保养到期即拒绝领用, 不只挡进站)
        Map<String, Object> usable = checkToolingAvailable(toolingId);
        if (!Boolean.TRUE.equals(usable.get("usable"))) {
            throw new RuntimeException("工装不可领用: " + usable.get("reason"));
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
        if (STATUS_CALIBRATING.equals(tooling.getStatus())) {
            throw new RuntimeException("工装校准中,请先完成校准再保养");
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
        // CAS IDLE/MAINTENANCE/CALIBRATING -> SCRAPPED
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_SCRAPPED)
            .set(StringUtils.isNotBlank(reason), TgTooling::getRemark, reason)
            .eq(TgTooling::getToolingId, toolingId)
            .in(TgTooling::getStatus, STATUS_IDLE, STATUS_MAINTENANCE, STATUS_CALIBRATING));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,报废失败");
        }
        log.info("工装报废: toolingId={}, reason={}", toolingId, reason);
        return true;
    }

    /**
     * 校验工装可用性(契约方法: 进站/出站校验 + 领用前置)
     * <p>MES-TG-01/TG-02 修复: 不再只看 status。校验 存在 + status=IDLE
     * + <b>校准未超期</b>(next_calibration_date) + <b>保养未到期</b>(已启用保养计划阈值)。</p>
     */
    @Override
    public Map<String, Object> checkToolingAvailable(Long toolingId) {
        Map<String, Object> result = new HashMap<>();
        result.put("usable", false);
        if (toolingId == null) {
            result.put("reason", "未提供工装ID");
            return result;
        }
        TgToolingVo tooling = baseMapper.selectVoById(toolingId);
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
        if (STATUS_CALIBRATING.equals(tooling.getStatus())) {
            result.put("reason", "工装校准中");
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
        Date now = new Date();
        // MES-TG-01: 校准有效期拦截(量具/检具超期不得上机)
        if (isCalibrationOverdue(tooling, now)) {
            result.put("calibrationOverdue", true);
            result.put("reason", "校准已超期(下次校准日 " + DateUtil.formatDate(tooling.getNextCalibrationDate())
                + "), 请先登记校准");
            return result;
        }
        // MES-TG-02: 保养到期拦截(原实现只判 status, 超寿命/超保养周期照样放行)
        fillRemaining(tooling);
        if (isMaintenanceDue(tooling, now)) {
            result.put("maintenanceDue", true);
            result.put("reason", "保养已到期(超出已启用保养计划的阈值), 请先完成保养归零");
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

    @Override
    public List<TgToolingVo> listCalibrationDue() {
        // 提醒窗口: 今天 ~ 今天 + CALIBRATION_DUE_SOON_DAYS(含已超期, 由 le 条件覆盖)
        Date window = new Date(System.currentTimeMillis()
            + (long) CALIBRATION_DUE_SOON_DAYS * 24 * 60 * 60 * 1000L);
        List<TgToolingVo> list = baseMapper.selectVoList(Wrappers.<TgTooling>lambdaQuery()
            .in(TgTooling::getStatus, STATUS_IDLE, STATUS_IN_USE)
            .isNotNull(TgTooling::getNextCalibrationDate)
            .le(TgTooling::getNextCalibrationDate, window));
        list.forEach(this::fillRemaining);
        return list;
    }

    /**
     * 登记校准(MES-TG-01): CAS IDLE -> CALIBRATING -> IDLE + 写校准留痕 + 服务端计算下次校准日
     */
    @Override
    @Lock4j(keys = {"#toolingId"})
    @Transactional(rollbackFor = Exception.class)
    public Boolean registerCalibration(Long toolingId, TgCalibrationBo bo) {
        TgTooling tooling = baseMapper.selectById(toolingId);
        if (tooling == null) {
            throw new RuntimeException("工装不存在: " + toolingId);
        }
        if (STATUS_IN_USE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装使用中,请先归还再登记校准");
        }
        if (STATUS_SCRAPPED.equals(tooling.getStatus())) {
            throw new RuntimeException("工装已报废,不可登记校准");
        }
        if (STATUS_MAINTENANCE.equals(tooling.getStatus())) {
            throw new RuntimeException("工装保养中,请先完成保养再登记校准");
        }
        // CAS IDLE -> CALIBRATING(校准期间不得领用/进站)
        int rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_CALIBRATING)
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_IDLE));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,登记校准失败");
        }
        TgCalibrationBo cmd = bo == null ? new TgCalibrationBo() : bo;
        Date calDate = cmd.getCalibrationDate() == null ? new Date() : cmd.getCalibrationDate();
        Integer cycleDays = cmd.getCalibrationCycleDays() != null
            ? cmd.getCalibrationCycleDays() : tooling.getCalibrationCycleDays();
        String calResult = StringUtils.isBlank(cmd.getCalibrationResult())
            ? CALIBRATION_PASS : cmd.getCalibrationResult();
        // 下次校准日必须服务端算: FAIL 视为本次校准未通过 -> 置为本次日期, 使工装立即处于
        // 「校准超期」不可用状态, 复校通过前不得领用/进站
        Date nextDate;
        if (CALIBRATION_FAIL.equals(calResult)) {
            nextDate = calDate;
        } else {
            nextDate = (cycleDays == null || cycleDays <= 0)
                ? null : new Date(calDate.getTime() + (long) cycleDays * 24 * 60 * 60 * 1000L);
        }
        Long operatorId = cmd.getCalibratedBy() != null ? cmd.getCalibratedBy() : LoginHelper.getUserId();
        // CAS CALIBRATING -> IDLE + 校准留痕
        rows = baseMapper.update(null, Wrappers.<TgTooling>lambdaUpdate()
            .set(TgTooling::getStatus, STATUS_IDLE)
            .set(TgTooling::getLastCalibrationDate, calDate)
            .set(TgTooling::getNextCalibrationDate, nextDate)
            .set(TgTooling::getCalibrationCycleDays, cycleDays)
            .set(TgTooling::getCalibratedBy, operatorId)
            .set(TgTooling::getCalibrationResult, calResult)
            .set(TgTooling::getCalibrationRemark, cmd.getCalibrationRemark())
            .set(StringUtils.isNotBlank(cmd.getRemark()), TgTooling::getRemark, cmd.getRemark())
            .eq(TgTooling::getToolingId, toolingId)
            .eq(TgTooling::getStatus, STATUS_CALIBRATING));
        if (rows == 0) {
            throw new RuntimeException("工装状态已变更,登记校准失败");
        }
        log.info("工装登记校准: toolingId={}, result={}, cycleDays={}, nextCalibrationDate={}",
            toolingId, calResult, cycleDays, nextDate);
        return true;
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
        // MES-TG-01: 校准超期标记(非库列, 供列表展示与提醒分级)
        vo.setCalibrationOverdue(isCalibrationOverdue(vo, new Date()));
    }

    /**
     * 校准是否已超期: next_calibration_date 为空表示未纳入校准管理(存量行), 不拦截;
     * 否则按「日期粒度」比较 —— 下次校准日当天仍可用, 次日起超期 (MES-TG-01)
     */
    private boolean isCalibrationOverdue(TgToolingVo vo, Date now) {
        Date next = vo.getNextCalibrationDate();
        if (next == null) {
            return false;
        }
        return next.before(DateUtil.beginOfDay(now));
    }

    /**
     * 计算下次校准日: 显式传值优先; 否则 周期 > 0 时按「上次校准日(或今天) + 周期」推算;
     * 周期为空/非正数返回 null(表示未纳入校准管理) (MES-TG-01)
     */
    private Date resolveNextCalibration(Integer cycleDays, Date lastDate, Date explicitNext) {
        if (explicitNext != null) {
            return explicitNext;
        }
        if (cycleDays == null || cycleDays <= 0) {
            return null;
        }
        Date base = lastDate == null ? new Date() : lastDate;
        return new Date(base.getTime() + (long) cycleDays * 24 * 60 * 60 * 1000L);
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
