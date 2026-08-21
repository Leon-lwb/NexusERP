package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationBatchReportDto;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationReportMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工位终端过站服务 (M2 契约 C2/C3/C4)
 * <p>
 * SN 单件过站模型: 设备通道报工只做 step 数量增量累加 + mes_station_report 幂等留痕,
 * 不触碰既有 completeStep 的"卡级全数分流"校验路径(PC 端语义不变)。
 * step 累计良品+不良+报废 >= planned_qty 时由本服务直接 CAS 收口并复用
 * {@link IProFlowCardService#completeCard} 做卡完工聚合。
 * </p>
 *
 * <p>四重校验(C2): PREV_STEP 前道工序完成 / STATION_MATCH 工位工序匹配 /
 * DUPLICATE 重复过站 / QUALITY_HOLD 质量扣押(最新综测 FAIL 或未关闭 NCR 或 SN 状态异常)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationPassService {

    /** 校验码: 前道工序 */
    public static final String CHECK_PREV_STEP = "PREV_STEP";
    /** 校验码: 工位/工序匹配 */
    public static final String CHECK_STATION_MATCH = "STATION_MATCH";
    /** 校验码: 重复过站 */
    public static final String CHECK_DUPLICATE = "DUPLICATE";
    /** 校验码: 质量扣押 */
    public static final String CHECK_QUALITY_HOLD = "QUALITY_HOLD";

    private final StationReportMapper reportMapper;
    private final StationSchWorkerMapper schWorkerMapper;
    private final StationDeviceMapper deviceMapper;
    private final ProSnMapper proSnMapper;
    private final ProFlowCardMapper flowCardMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final TestRecordMapper testRecordMapper;
    private final IProFlowCardService flowCardService;
    private final IProSnService proSnService;
    private final NcrOrderMapper ncrOrderMapper;

    // ==================== C2 过站四重校验 ====================

    /**
     * 过站四重校验(C2): 聚合 SN/工序/测试记录/报工记录一次返回全部校验结果。
     *
     * @param snCode      SN编码
     * @param stationCode 工位编码
     * @return {pass, snInfo, checks:[{code, ok, message}]}
     */
    public Map<String, Object> validate(String snCode, String stationCode) {
        String sn = trim(snCode);
        String station = trim(stationCode);
        if (StringUtils.isBlank(sn) || StringUtils.isBlank(station)) {
            throw new ServiceException("snCode 与 stationCode 不能为空");
        }

        Map<String, Object> data = new HashMap<>(4);
        List<Map<String, Object>> checks = new ArrayList<>(4);
        Map<String, Object> snInfo = new LinkedHashMap<>(8);

        ProSn proSn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, sn).last("LIMIT 1"));
        if (proSn == null) {
            snInfo.put("currentProcessName", null);
            checks.add(check(CHECK_STATION_MATCH, false, "SN不存在: " + sn));
            data.put("pass", false);
            data.put("snInfo", snInfo);
            data.put("checks", checks);
            return data;
        }

        // SN 冗余字段回填 snInfo
        snInfo.put("woNo", proSn.getWoNo());
        snInfo.put("cardNo", proSn.getCardNo());
        snInfo.put("materialName", proSn.getMaterialName());

        // 定位 SN 当前工序(按 current_step_seq 定位工序记录)
        ProFlowCardStep currentStep = selectStepBySeq(proSn.getCardId(), proSn.getCurrentStepSeq());
        snInfo.put("currentProcessName", currentStep != null ? currentStep.getProcessName() : null);

        // 工位码 → 工序码: ① mes_station_device 心跳绑定 ② 工位码格式 STN|车间|产线|工序|形态
        String stationProcessCode = resolveProcessCode(station);

        // --- 校验1 PREV_STEP: 当前工序之前所有工序均已完成/跳过 ---
        boolean prevOk = true;
        String prevMsg = null;
        if (currentStep == null) {
            prevOk = false;
            prevMsg = "SN 未找到当前工序记录(seq=" + proSn.getCurrentStepSeq() + ")";
        } else {
            List<ProFlowCardStep> prevSteps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
                .eq(ProFlowCardStep::getCardId, proSn.getCardId())
                .lt(ProFlowCardStep::getSequenceNum, currentStep.getSequenceNum())
                .notIn(ProFlowCardStep::getStepStatus, "COMPLETED", "SKIPPED"));
            if (!prevSteps.isEmpty()) {
                prevOk = false;
                List<String> names = prevSteps.stream()
                    .map(s -> s.getProcessName() + "(" + s.getStepStatus() + ")")
                    .toList();
                prevMsg = "前道工序未完成: " + String.join(", ", names);
            }
        }
        checks.add(check(CHECK_PREV_STEP, prevOk, prevMsg));

        // --- 校验2 STATION_MATCH: SN 当前工序 = 工位工序, 且 SN 在制 ---
        boolean matchOk;
        String matchMsg = null;
        if (stationProcessCode == null) {
            matchOk = false;
            matchMsg = "无法解析工位工序编码(工位未心跳注册且工位码格式不符): " + station;
        } else if (currentStep == null || !stationProcessCode.equals(currentStep.getProcessCode())) {
            matchOk = false;
            matchMsg = String.format("工位工序(%s)与SN当前工序(%s)不匹配", stationProcessCode,
                currentStep != null ? currentStep.getProcessCode() : "无");
        } else if (!"IN_PRODUCTION".equals(proSn.getSnStatus())) {
            matchOk = false;
            matchMsg = "SN 状态为 " + proSn.getSnStatus() + ", 非在制状态";
        } else {
            matchOk = true;
        }
        checks.add(check(CHECK_STATION_MATCH, matchOk, matchMsg));

        // --- 校验3 DUPLICATE: 该 SN 已在该工位报过 PASS ---
        Long dupCount = reportMapper.selectCount(Wrappers.<MesStationReport>lambdaQuery()
            .eq(MesStationReport::getSnCode, sn)
            .eq(MesStationReport::getStationCode, station)
            .eq(MesStationReport::getResult, "PASS"));
        boolean dupOk = dupCount == 0;
        checks.add(check(CHECK_DUPLICATE, dupOk, dupOk ? null : "该 SN 已在此工位报工过站(PASS), 禁止重复过站"));

        // --- 校验4 QUALITY_HOLD: 最新综测 FAIL / 未关闭 NCR ---
        boolean holdOk = true;
        String holdMsg = null;
        TestRecord latestTest = testRecordMapper.selectOne(Wrappers.<TestRecord>lambdaQuery()
            .eq(TestRecord::getSnCode, sn)
            .eq(TestRecord::getResult, "FAIL")
            .orderByDesc(TestRecord::getTestTime)
            .orderByDesc(TestRecord::getTestRecordId)
            .last("LIMIT 1"));
        if (latestTest != null) {
            holdOk = false;
            holdMsg = "最新综测结果 FAIL(测试单 " + latestTest.getTestNo() + "), 质量扣押";
        } else if (proSn.getCardId() != null) {
            Long openNcr = ncrOrderMapper.selectCount(Wrappers.<NcrOrder>lambdaQuery()
                .eq(NcrOrder::getCardId, proSn.getCardId())
                .in(NcrOrder::getStatus,
                    INcrOrderService.STATUS_OPEN,
                    INcrOrderService.STATUS_REVIEW,
                    INcrOrderService.STATUS_DISPOSITION,
                    INcrOrderService.STATUS_IN_REWORK));
            if (openNcr != null && openNcr > 0) {
                holdOk = false;
                holdMsg = "该 SN 所在流转卡存在未关闭 NCR 单(" + openNcr + " 张), 质量扣押";
            }
        }
        checks.add(check(CHECK_QUALITY_HOLD, holdOk, holdMsg));

        boolean pass = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("ok")));
        data.put("pass", pass);
        data.put("snInfo", snInfo);
        data.put("checks", checks);
        return data;
    }

    // ==================== C3 单件过站报工 ====================

    /**
     * SN 单件过站报工(C3): 幂等键 SINGLE|sn|station|clientTs 落唯一索引,
     * 重复上报(离线队列重放)直接返回成功。
     *
     * @param dto 报工请求
     * @return 空对象(契约 data 为 {})
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportSingle(StationPassReportDto dto) {
        String sn = trim(dto.getSnCode());
        String station = trim(dto.getStationCode());
        String result = trim(dto.getResult());
        Long clientTs = dto.getClientTs();
        if (StringUtils.isBlank(sn) || StringUtils.isBlank(station)) {
            throw new ServiceException("snCode 与 stationCode 不能为空");
        }
        if (!"PASS".equals(result) && !"FAIL".equals(result)) {
            throw new ServiceException("result 仅允许 PASS/FAIL: " + result);
        }
        if (clientTs == null) {
            throw new ServiceException("clientTs 不能为空(幂等键组成部分)");
        }

        // 1. 幂等预检 + 唯一索引兜底: 重复上报直接成功
        String clientKey = "SINGLE|" + sn + "|" + station + "|" + clientTs;
        if (reportMapper.selectByClientKey(clientKey) != null) {
            log.info("过站报工幂等命中(重放): clientKey={}", clientKey);
            return new HashMap<>();
        }

        // 2. 定位 SN 与当前工序
        ProSn proSn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, sn).last("LIMIT 1"));
        if (proSn == null) {
            throw new ServiceException("SN不存在: " + sn);
        }
        if (!"IN_PRODUCTION".equals(proSn.getSnStatus())) {
            throw new ServiceException("SN 状态为 " + proSn.getSnStatus() + ", 非在制状态, 禁止过站报工");
        }
        ProFlowCardStep step = selectStepBySeq(proSn.getCardId(), proSn.getCurrentStepSeq());
        if (step == null) {
            throw new ServiceException("SN 未找到当前工序记录(seq=" + proSn.getCurrentStepSeq() + ")");
        }
        ProFlowCard card = flowCardMapper.selectById(proSn.getCardId());
        if (card == null) {
            throw new ServiceException("SN 所属流转卡不存在: " + proSn.getCardNo());
        }

        // 3. 操作员回填(operatorCode → sch_worker 档案)
        OperatorInfo operator = resolveOperator(dto.getOperatorId(), dto.getOperatorCode());

        // 4. 幂等行先落库(同事务; 并发重放撞唯一索引整体返回成功)
        MesStationReport report = new MesStationReport();
        report.setReportType("SINGLE");
        report.setSnCode(sn);
        report.setStationCode(station);
        report.setCardId(proSn.getCardId());
        report.setStepRecordId(step.getStepRecordId());
        report.setMesWoId(proSn.getMesWoId());
        report.setWoNo(proSn.getWoNo());
        report.setOperatorId(operator.userId());
        report.setOperatorCode(operator.code());
        report.setOperatorName(operator.name());
        report.setResult(result);
        report.setDefectCodes(joinDefectCodes(dto.getDefectCodes()));
        report.setGoodQty("PASS".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO);
        report.setDefectQty("FAIL".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO);
        report.setClientTs(clientTs);
        report.setClientKey(clientKey);
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            log.info("过站报工幂等命中(唯一索引): clientKey={}", clientKey);
            return new HashMap<>();
        }

        // 5. 工序自动开工(PENDING -> IN_PROGRESS; 已 IN_PROGRESS 则补操作员)
        autoStartStep(step, operator);

        // 6. 数量增量累加: PASS → good_qty+1; FAIL → defect_qty+1
        boolean isPass = "PASS".equals(result);
        stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .setSql(isPass ? "good_qty = good_qty + 1" : "defect_qty = defect_qty + 1")
            .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId()));

        // 7. PASS: SN 流转到下一工序/末道完工; FAIL: SN 停留当前工序待处理
        if (isPass) {
            advanceSn(proSn, step, card);
        }
        log.info("过站报工完成: sn={}, station={}, result={}, stepRecordId={}", sn, station, result, step.getStepRecordId());
        return new HashMap<>();
    }

    /**
     * SN 前移: 末道工序 → SN 完工 + 卡工序收口; 非末道 → current_step_seq+1。
     */
    private void advanceSn(ProSn proSn, ProFlowCardStep step, ProFlowCard card) {
        Integer maxSeq = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
                .eq(ProFlowCardStep::getCardId, proSn.getCardId()))
            .stream().map(ProFlowCardStep::getSequenceNum)
            .filter(java.util.Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(null);
        boolean isLastStep = maxSeq != null && maxSeq.equals(step.getSequenceNum());

        // 工序收口: 累计良品+不良+报废 >= 计划数量 → CAS COMPLETED 并触发卡完工聚合
        completeStepIfQtyReached(step, card);

        if (isLastStep) {
            proSnService.completeSn(proSn.getSnId());
            log.info("SN 末道工序过站完成, SN 完工: snId={}, snCode={}", proSn.getSnId(), proSn.getSnCode());
        } else {
            proSnMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
                .set(ProSn::getCurrentStepSeq, step.getSequenceNum() + 1)
                .eq(ProSn::getSnId, proSn.getSnId()));
        }
    }

    /**
     * 工序数量收口: 累计 good+defect+scrap >= planned_qty 时 CAS IN_PROGRESS→COMPLETED
     * (重查累计值, 避免内存旧值), 全工序完成后复用 completeCard 做卡完工聚合。
     */
    private void completeStepIfQtyReached(ProFlowCardStep step, ProFlowCard card) {
        ProFlowCardStep fresh = stepMapper.selectById(step.getStepRecordId());
        if (fresh == null || !"IN_PROGRESS".equals(fresh.getStepStatus())) {
            return;
        }
        BigDecimal good = nvl(fresh.getGoodQty());
        BigDecimal defect = nvl(fresh.getDefectQty());
        BigDecimal scrap = nvl(fresh.getScrapQty());
        BigDecimal planned = nvl(fresh.getPlannedQty());
        if (planned.compareTo(BigDecimal.ZERO) <= 0
            || good.add(defect).add(scrap).compareTo(planned) < 0) {
            return;
        }
        int rows = stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .set(ProFlowCardStep::getStepStatus, "COMPLETED")
            .set(ProFlowCardStep::getActualEndTime, new Date())
            .eq(ProFlowCardStep::getStepRecordId, fresh.getStepRecordId())
            .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        if (rows > 0) {
            log.info("设备通道报工触发工序完工: stepRecordId={}, good={}, defect={}, scrap={}",
                fresh.getStepRecordId(), good, defect, scrap);
            // 卡上所有工序完成 → 复用既有 completeCard 聚合(卡完工 → 工单数量聚合/完工判定)
            Long pending = stepMapper.selectCount(Wrappers.<ProFlowCardStep>lambdaQuery()
                .eq(ProFlowCardStep::getCardId, card.getCardId())
                .notIn(ProFlowCardStep::getStepStatus, "COMPLETED", "SKIPPED"));
            if (pending == 0) {
                flowCardService.completeCard(card.getCardId());
            }
        }
    }

    /**
     * 工序自动开工: PENDING → IN_PROGRESS(写操作员/开工时间, 联动卡与工单状态);
     * 已 IN_PROGRESS 且未记录操作员时补记。
     */
    private void autoStartStep(ProFlowCardStep step, OperatorInfo operator) {
        if ("PENDING".equals(step.getStepStatus())) {
            flowCardService.startStep(step.getCardId(), step.getStepRecordId(), operator.userId());
        } else if ("IN_PROGRESS".equals(step.getStepStatus()) && step.getOperatorId() == null && operator.userId() != null) {
            stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
                .set(ProFlowCardStep::getOperatorId, operator.userId())
                .set(ProFlowCardStep::getOperatorName, operator.name())
                .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId())
                .isNull(ProFlowCardStep::getOperatorId));
        }
    }

    // ==================== C4 批量计数报工 ====================

    /**
     * 批量计数报工(C4): 幂等键 BATCH|station|clientTs; 按工位工序定位当前唯一在制工序,
     * goodQty/defectQty 增量累加(GENERAL 计数工位无 SN 粒度)。
     *
     * @param dto 批量报工请求
     * @return 空对象(契约 data 为 {})
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportBatch(StationBatchReportDto dto) {
        String station = trim(dto.getStationCode());
        Long clientTs = dto.getClientTs();
        if (StringUtils.isBlank(station)) {
            throw new ServiceException("stationCode 不能为空");
        }
        if (clientTs == null) {
            throw new ServiceException("clientTs 不能为空(幂等键组成部分)");
        }
        BigDecimal goodQty = dto.getGoodQty() != null ? dto.getGoodQty() : BigDecimal.ZERO;
        Map<String, BigDecimal> defectMap = new LinkedHashMap<>();
        BigDecimal defectTotal = BigDecimal.ZERO;
        if (dto.getDefects() != null) {
            for (StationBatchReportDto.DefectItem item : dto.getDefects()) {
                if (item == null || StringUtils.isBlank(item.getCode()) || item.getQty() == null) {
                    continue;
                }
                defectMap.merge(item.getCode().trim(), item.getQty(), BigDecimal::add);
                defectTotal = defectTotal.add(item.getQty());
            }
        }
        if (goodQty.compareTo(BigDecimal.ZERO) < 0 || defectTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("报工数量不能为负数");
        }
        if (goodQty.add(defectTotal).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("报工数量合计必须大于0");
        }

        // 1. 幂等预检 + 唯一索引兜底
        String clientKey = "BATCH|" + station + "|" + clientTs;
        if (reportMapper.selectByClientKey(clientKey) != null) {
            log.info("批量报工幂等命中(重放): clientKey={}", clientKey);
            return new HashMap<>();
        }

        // 2. 按工位工序定位当前唯一 IN_PROGRESS 工序(计数工位语义: 工位同一时刻仅一张在制卡)
        String processCode = resolveProcessCode(station);
        if (processCode == null) {
            throw new ServiceException("无法解析工位工序编码(工位未心跳注册且工位码格式不符): " + station);
        }
        List<ProFlowCardStep> inProgressSteps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getProcessCode, processCode)
            .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        if (inProgressSteps.size() != 1) {
            throw new ServiceException(String.format(
                "工位工序(%s)当前在制工序数=%d(应为1), 无法定位报工目标; 请先开工流转卡工序",
                processCode, inProgressSteps.size()));
        }
        ProFlowCardStep step = inProgressSteps.get(0);
        ProFlowCard card = flowCardMapper.selectById(step.getCardId());
        if (card == null) {
            throw new ServiceException("工序所属流转卡不存在: stepRecordId=" + step.getStepRecordId());
        }

        // 3. 操作员回填 + 幂等行落库
        OperatorInfo operator = resolveOperator(null, dto.getOperatorCode());
        MesStationReport report = new MesStationReport();
        report.setReportType("BATCH");
        report.setStationCode(station);
        report.setCardId(card.getCardId());
        report.setStepRecordId(step.getStepRecordId());
        report.setMesWoId(card.getMesWoId());
        report.setWoNo(card.getWoNo());
        report.setOperatorId(operator.userId());
        report.setOperatorCode(operator.code());
        report.setOperatorName(operator.name());
        report.setDefectCodes(defectMap.isEmpty() ? null : String.join(",", defectMap.keySet()));
        report.setGoodQty(goodQty);
        report.setDefectQty(defectTotal);
        report.setClientTs(clientTs);
        report.setClientKey(clientKey);
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            log.info("批量报工幂等命中(唯一索引): clientKey={}", clientKey);
            return new HashMap<>();
        }

        // 4. 数量增量累加
        stepMapper.update(null, Wrappers.<ProFlowCardStep>lambdaUpdate()
            .setSql("good_qty = good_qty + {0}", goodQty)
            .setSql("defect_qty = defect_qty + {0}", defectTotal)
            .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId()));

        // 5. 数量达标则收口(与单件路径同一收口逻辑)
        completeStepIfQtyReached(step, card);
        log.info("批量报工完成: station={}, goodQty={}, defectQty={}, stepRecordId={}",
            station, goodQty, defectTotal, step.getStepRecordId());
        return new HashMap<>();
    }

    // ==================== 公共辅助 ====================

    /**
     * 工位码 → 工序码: ① mes_station_device 心跳注册绑定 ② 工位码格式 STN|车间|产线|工序|形态(取第4段)。
     * 与客户端解析规则一致(parts[3]=processCode)。
     */
    public String resolveProcessCode(String stationCode) {
        if (StringUtils.isBlank(stationCode)) {
            return null;
        }
        String station = stationCode.trim();
        // ① 心跳注册绑定(同库直查 mes_station_device)
        MesStationDevice device = deviceMapper.selectOne(Wrappers.<MesStationDevice>lambdaQuery()
            .eq(MesStationDevice::getStationCode, station)
            .last("LIMIT 1"));
        if (device != null && StringUtils.isNotBlank(device.getProcessCode())) {
            return device.getProcessCode();
        }
        // ② 工位码格式解析
        String[] parts = station.split("\\|");
        if (parts.length >= 5 && "STN".equalsIgnoreCase(parts[0]) && StringUtils.isNotBlank(parts[3])) {
            return parts[3].trim();
        }
        return null;
    }

    /** 按 cardId + sequenceNum 定位工序记录。 */
    public ProFlowCardStep selectStepBySeq(Long cardId, Integer sequenceNum) {
        if (cardId == null || sequenceNum == null) {
            return null;
        }
        return stepMapper.selectOne(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .eq(ProFlowCardStep::getSequenceNum, sequenceNum)
            .last("LIMIT 1"));
    }

    /**
     * 操作员解析: operatorId 直用; 否则 operatorCode → sch_worker 档案(user_id/user_name)。
     * 均缺失返回空信息(容忍, 字段落 null)。
     */
    public OperatorInfo resolveOperator(Long operatorId, String operatorCode) {
        if (operatorId != null) {
            return new OperatorInfo(operatorId, trim(operatorCode), null);
        }
        String code = trim(operatorCode);
        if (StringUtils.isNotBlank(code)) {
            Map<String, Object> worker = TenantHelper.dynamic("000000", () -> schWorkerMapper.selectByEmployeeId(code));
            if (worker != null) {
                Object userId = worker.get("user_id");
                Long uid = userId == null ? null : Long.valueOf(String.valueOf(userId));
                return new OperatorInfo(uid, code, (String) worker.get("user_name"));
            }
            // 工号查不到档案: 仍留痕工号
            return new OperatorInfo(null, code, null);
        }
        return new OperatorInfo(null, null, null);
    }

    private String joinDefectCodes(List<String> defectCodes) {
        if (defectCodes == null || defectCodes.isEmpty()) {
            return null;
        }
        return String.join(",", defectCodes.stream().filter(StringUtils::isNotBlank).map(String::trim).toList());
    }

    private Map<String, Object> check(String code, boolean ok, String message) {
        Map<String, Object> c = new HashMap<>(4);
        c.put("code", code);
        c.put("ok", ok);
        c.put("message", message);
        return c;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 操作员信息(userId/code/name, 均可空)。 */
    public record OperatorInfo(Long userId, String code, String name) {
    }
}
