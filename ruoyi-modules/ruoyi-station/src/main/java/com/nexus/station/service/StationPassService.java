package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
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
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.mes.trace.MesPreparedWorkOrderPolicies;
import com.nexus.mes.trace.MesSerialReferenceService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationBatchReportDto;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationPassGuardMapper;
import com.nexus.station.mapper.StationReportMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /**
     * 校验码: 前道工序
     */
    public static final String CHECK_PREV_STEP = "PREV_STEP";

    /**
     * 校验码: 工位/工序匹配
     */
    public static final String CHECK_STATION_MATCH = "STATION_MATCH";

    /**
     * 校验码: 重复过站
     */
    public static final String CHECK_DUPLICATE = "DUPLICATE";

    /**
     * 校验码: 质量扣押
     */
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
    private final StationPassGuardMapper passGuardMapper;

    /**
     * 工单父锁先于SN/卡锁，避免不同SN持锁后互等跨卡投影。
     */
    private final ProWorkOrderStepProgressService progressService;

    private final MesSerialReferenceService serialReferences;
    private final MesPreparedWorkOrderPolicies preparedPolicies;

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
        String station = normalizeStationCode(trim(stationCode));
        if (StringUtils.isBlank(sn) || StringUtils.isBlank(station)) {
            throw new ServiceException("snCode 与 stationCode 不能为空");
        }

        var reference = serialReferences.resolve(sn);
        ProSn proSn = reference == null ? null : reference.sn();
        if (proSn == null) {
            return StationPassChecks.evaluate(sn, null, null, null, List.of(), 0, null, 0);
        }
        String localCode = reference.localSnCode();
        ProFlowCardStep step = resolveCurrentStep(proSn.getCardId(), proSn.getCurrentStepSeq());
        List<ProFlowCardStep> previous =
                step == null
                        ? List.of()
                        : stepMapper.selectList(
                                Wrappers.<ProFlowCardStep>lambdaQuery()
                                        .eq(ProFlowCardStep::getCardId, proSn.getCardId())
                                        .lt(ProFlowCardStep::getSequenceNum, step.getSequenceNum())
                                        .notIn(
                                                ProFlowCardStep::getStepStatus,
                                                "COMPLETED",
                                                "SKIPPED"));
        Long duplicates =
                reportMapper.selectCount(
                        Wrappers.<MesStationReport>lambdaQuery()
                                .eq(MesStationReport::getSnCode, localCode)
                                .eq(MesStationReport::getResult, "PASS")
                                .and(
                                        w -> {
                                            w.eq(MesStationReport::getStationCode, station);
                                            if (step != null && step.getStepRecordId() != null) {
                                                w.or().eq(
                                                                MesStationReport::getStepRecordId,
                                                                step.getStepRecordId());
                                            }
                                        }));
        boolean sameCode = localCode.equals(reference.canonicalSnCode());
        TestRecord latest =
                testRecordMapper.selectOne(
                        Wrappers.<TestRecord>lambdaQuery()
                                .eq(TestRecord::getTenantId, reference.tenantId())
                                .and(
                                        w -> {
                                            w.eq(TestRecord::getSnId, reference.localSnId());
                                            if (sameCode) {
                                                w.or(
                                                        legacy ->
                                                                legacy.isNull(TestRecord::getSnId)
                                                                        .eq(
                                                                                TestRecord
                                                                                        ::getSnCode,
                                                                                localCode));
                                            }
                                        })
                                .orderByDesc(TestRecord::getTestTime)
                                .orderByDesc(TestRecord::getTestRecordId)
                                .last("LIMIT 1"));
        boolean unverifiedLegacyTests = false;
        if (!sameCode) {
            Long count =
                    testRecordMapper.selectCount(
                            Wrappers.<TestRecord>lambdaQuery()
                                    .eq(TestRecord::getTenantId, reference.tenantId())
                                    .isNull(TestRecord::getSnId)
                                    .in(
                                            TestRecord::getSnCode,
                                            List.of(localCode, reference.canonicalSnCode())));
            require(count != null && count >= 0, "历史测试证据范围未核验");
            unverifiedLegacyTests = count > 0;
        }
        Long open =
                proSn.getCardId() == null
                        ? 0L
                        : ncrOrderMapper.selectCount(
                                Wrappers.<NcrOrder>lambdaQuery()
                                        .eq(NcrOrder::getCardId, proSn.getCardId())
                                        .in(
                                                NcrOrder::getStatus,
                                                INcrOrderService.STATUS_OPEN,
                                                INcrOrderService.STATUS_REVIEW,
                                                INcrOrderService.STATUS_DISPOSITION,
                                                INcrOrderService.STATUS_IN_REWORK));
        var checked =
                StationPassChecks.evaluate(
                        sn,
                        proSn,
                        step,
                        resolveProcessCode(station),
                        previous,
                        duplicates == null ? 0 : duplicates,
                        latest,
                        open == null ? 0 : open);
        applyLegacyTestGap(checked, unverifiedLegacyTests, latest);
        @SuppressWarnings("unchecked")
        var info = (Map<String, Object>) checked.get("snInfo");
        info.put("identityId", reference.identityId());
        info.put("localSnId", reference.localSnId());
        info.put("canonicalSnCode", reference.canonicalSnCode());
        info.put("localSnCode", reference.localSnCode());
        return checked;
    }

    /**
     * 保留歧义历史证据的可见性，质量占用的释放仍需充分且明确的来源。
     */
    private static void applyLegacyTestGap(
            Map<String, Object> validation, boolean unverified, TestRecord latest) {
        if (!unverified) {
            return;
        }
        String gap = "正式码与历史本地码不一致，未绑定 localSnId 的历史综测不能核验本件质量";
        validation.put("evidenceGaps", List.of(gap));
        if (latest != null
                && Set.of("PASS", "FAIL").contains(Objects.toString(latest.getResult(), ""))) {
            return;
        }
        @SuppressWarnings("unchecked")
        var checks = (List<Map<String, Object>>) validation.get("checks");
        for (var check : checks) {
            if (CHECK_QUALITY_HOLD.equals(check.get("code"))) {
                check.put("ok", false);
                check.put("message", gap);
            }
        }
        validation.put("pass", false);
    }

    // ==================== C3 单件过站报工 ====================

    /**
     * SN 单件过站报工(C3): 幂等键 SINGLE|sn|station|clientTs 落唯一索引,
     * 已提交且内容一致的离线重放返回成功；新请求必须在锁定重读后通过完整业务检查。
     * <p>质量全范围锁依赖 MySQL InnoDB RR 的范围锁；无匹配记录也不能改成只锁最新一行。</p>
     * <p>操作员由调用方在事务外解析传入: 本方法 @Transactional 会把连接钉在 ry-mes
     * (动态数据源在同一事务内只持有一个物理连接), 事务内查 sch_worker(ry-erp) 必然失败。</p>
     *
     * @param dto      报工请求
     * @param operator 事务外解析好的操作员(可空，按空操作员记录)
     * @return 已提交的MES效果及其原完工清单当前投递状态
     */
    @Transactional(
            rollbackFor = Exception.class,
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> reportSingle(StationPassReportDto dto, OperatorInfo operator) {
        require(dto != null, "单件过站请求不能为空");
        String rawSn = trim(dto.getSnCode());
        String station = normalizeStationCode(trim(dto.getStationCode()));
        String result = trim(dto.getResult());
        require(
                StringUtils.isNotBlank(rawSn) && rawSn.length() <= 64 && !rawSn.contains("|"),
                "SN 编码为空、过长或含幂等分隔符");
        require(StringUtils.isNotBlank(station) && station.length() <= 64, "工位编码为空或过长");
        require("PASS".equals(result) || "FAIL".equals(result), "result 仅允许 PASS/FAIL");
        require(dto.getClientTs() != null && dto.getClientTs() > 0, "clientTs 必须为正数");
        String clientKey = "SINGLE|" + rawSn + "|" + station + "|" + dto.getClientTs();
        require(clientKey.length() <= 160, "幂等键超出存储长度");
        String defects = canonicalDefects(dto.getDefectCodes());
        require(!"PASS".equals(result) || defects == null, "PASS 不能携带不良代码");
        OperatorInfo op = operator == null ? new OperatorInfo(null, null, null) : operator;
        String tenant = TenantHelper.getTenantId();
        require(TenantHelper.isEnable() && StringUtils.isNotBlank(tenant), "单件过站必须有启用的可信租户上下文");
        require(
                "mes".equals(DynamicDataSourceContextHolder.peek())
                        && TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        && Objects.equals(
                                Connection.TRANSACTION_REPEATABLE_READ,
                                TransactionSynchronizationManager
                                        .getCurrentTransactionIsolationLevel()),
                "单件过站必须通过实际 MES RR 写事务代理调用");

        String fingerprint =
                StationSinglePassFingerprint.of(
                        tenant,
                        rawSn,
                        station,
                        dto.getClientTs(),
                        result,
                        dto.getOperatorId(),
                        blankToNull(dto.getOperatorCode()),
                        defects);
        var reference = serialReferences.resolve(rawSn);
        require(reference != null, "SN has no unique local mapping to the canonical ERP identity");
        String sn = reference.localSnCode();
        preparedPolicies.prepareStationSingle(reference.sn().getCardId(), clientKey);
        Long lockedWoId = progressService.lockForSn(sn);
        List<ProSn> sns = passGuardMapper.lockSn(tenant, sn);
        require(sns != null && sns.size() == 1, "SN 不存在或编码归属不唯一");
        ProSn proSn = sns.get(0);
        require(
                Objects.equals(proSn.getSnId(), reference.localSnId())
                        && Objects.equals(proSn.getIdentityId(), reference.identityId()),
                "Locked SN differs from the resolved canonical identity");
        require(
                tenant.equals(proSn.getTenantId())
                        && sn.equals(proSn.getSnCode())
                        && "0".equals(proSn.getDelFlag()),
                "SN 租户或来源归属不匹配");
        require(Objects.equals(lockedWoId, proSn.getMesWoId()), "SN锁定父工单与真实来源不一致");
        List<StationPassGuardMapper.StoredReport> history = passGuardMapper.lockReports(tenant, sn);
        require(history != null, "报工历史来源缺失");
        for (StationPassGuardMapper.StoredReport prior : history) {
            require(
                    tenant.equals(prior.getTenantId())
                            && sn.equals(prior.getSnCode())
                            && Objects.equals(proSn.getMesWoId(), prior.getMesWoId())
                            && Objects.equals(proSn.getCardId(), prior.getCardId()),
                    "报工历史租户或 SN 归属不匹配");
            if (clientKey.equals(prior.getClientKey())) {
                require(
                        "0".equals(prior.getDelFlag())
                                && "SINGLE".equals(prior.getReportType())
                                && station.equals(prior.getStationCode())
                                && Objects.equals(dto.getClientTs(), prior.getClientTs())
                                && fingerprint.equals(prior.getRequestFingerprint())
                                && result.equals(prior.getResult())
                                && Objects.equals(
                                        defects,
                                        canonicalDefects(
                                                prior.getDefectCodes() == null
                                                        ? null
                                                        : List.of(
                                                                prior.getDefectCodes()
                                                                        .split(",", -1))))
                                && sameQuantity(
                                        prior.getGoodQty(),
                                        "PASS".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO)
                                && sameQuantity(
                                        prior.getDefectQty(),
                                        "FAIL".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO),
                        "幂等键已存在但原始请求指纹缺失、内容或承载状态冲突，禁止重放");
                return serialReferences.submissionStatus(reference);
            }
        }
        require(proSn.getCardId() != null, "SN 未绑定流转卡");
        ProFlowCard card = passGuardMapper.lockCard(tenant, proSn.getCardId());
        require(
                card != null
                        && tenant.equals(card.getTenantId())
                        && Objects.equals(proSn.getCardId(), card.getCardId())
                        && "0".equals(card.getDelFlag())
                        && proSn.getMesWoId() != null
                        && proSn.getMesWoId() > 0
                        && Objects.equals(proSn.getMesWoId(), card.getMesWoId())
                        && Set.of("RELEASED", "IN_PROGRESS")
                                .contains(Objects.toString(card.getCardStatus(), "")),
                "流转卡归属或可报工状态未核验");
        preparedPolicies.assertPreparedCard(card.getCardId(), proSn.getMesWoId());
        List<ProFlowCardStep> steps = passGuardMapper.lockSteps(tenant, card.getCardId());
        require(steps != null && !steps.isEmpty(), "流转卡工序来源缺失");
        Set<Integer> sequences = new HashSet<>();
        for (ProFlowCardStep s : steps) {
            require(
                    tenant.equals(s.getTenantId())
                            && card.getCardId().equals(s.getCardId())
                            && "0".equals(s.getDelFlag())
                            && Objects.equals(card.getMesWoId(), s.getMesWoId())
                            && s.getSequenceNum() != null
                            && s.getStepRecordId() != null
                            && sequences.add(s.getSequenceNum())
                            && Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "SKIPPED")
                                    .contains(Objects.toString(s.getStepStatus(), "")),
                    "工序来源、顺序或状态未核验");
        }
        ProFlowCardStep step =
                steps.stream()
                        .filter(s -> !Set.of("COMPLETED", "SKIPPED").contains(s.getStepStatus()))
                        .min(Comparator.comparing(ProFlowCardStep::getSequenceNum))
                        .orElse(null);
        require(step != null, "流转卡没有可报工工序");
        require(
                step.getPlannedQty() != null
                        && step.getPlannedQty().signum() > 0
                        && step.getGoodQty() != null
                        && step.getGoodQty().signum() >= 0
                        && step.getDefectQty() != null
                        && step.getDefectQty().signum() >= 0
                        && step.getScrapQty() != null
                        && step.getScrapQty().signum() >= 0,
                "当前工序计划量或累计量未核验");
        List<MesStationDevice> devices = passGuardMapper.lockDevices(tenant, station);
        require(devices != null && !devices.isEmpty(), "工位未登记，不能仅凭工位码授权报工");
        String process = devices.get(0).getProcessCode();
        require(StringUtils.isNotBlank(process), "登记工位缺少工序绑定");
        for (MesStationDevice d : devices) {
            require(
                    tenant.equals(d.getTenantId())
                            && station.equals(d.getStationCode())
                            && "0".equals(d.getDelFlag())
                            && process.equals(d.getProcessCode()),
                    "登记工位归属或工序绑定冲突");
        }
        List<TestRecord> localTests = passGuardMapper.lockTests(tenant, sn);
        require(localTests != null, "Local test evidence is unavailable");
        var tests = new ArrayList<>(localTests);
        if (!sn.equals(reference.canonicalSnCode())) {
            var canonicalTests = passGuardMapper.lockTests(tenant, reference.canonicalSnCode());
            require(canonicalTests != null, "Canonical test evidence is unavailable");
            tests.addAll(canonicalTests);
        }
        var identityTests =
                testRecordMapper.selectList(
                        Wrappers.<TestRecord>lambdaQuery()
                                .eq(TestRecord::getTenantId, tenant)
                                .eq(TestRecord::getSnId, reference.localSnId())
                                .orderByDesc(TestRecord::getTestTime)
                                .orderByDesc(TestRecord::getTestRecordId)
                                .last("FOR UPDATE"));
        require(identityTests != null, "Identity test evidence is unavailable");
        tests.addAll(identityTests);
        List<NcrOrder> ncrs = passGuardMapper.lockNcrs(tenant, card.getCardId());
        require(ncrs != null, "质量来源缺失");
        boolean sameCode = sn.equals(reference.canonicalSnCode());
        boolean unverifiedLegacyTests = false;
        var qualityTests = new ArrayList<TestRecord>();
        for (TestRecord test : tests) {
            boolean boundToThisSn = reference.localSnId().equals(test.getSnId());
            boolean historicalCode =
                    sn.equals(test.getSnCode())
                            || reference.canonicalSnCode().equals(test.getSnCode());
            require(
                    tenant.equals(test.getTenantId())
                            && (boundToThisSn || historicalCode)
                            && (boundToThisSn || test.getSnId() == null || !sameCode)
                            && Set.of("0", "1", "2")
                                    .contains(Objects.toString(test.getDelFlag(), "")),
                    "测试来源归属未核验");
            // 修复身份映射后，旧测试原码的不同拼写仍需各自的别名证据。
            if (boundToThisSn || (sameCode && test.getSnId() == null)) {
                require(
                        test.getResult() != null
                                && Set.of("", "PASS", "FAIL").contains(test.getResult()),
                        "测试结果状态未核验");
                qualityTests.add(test);
            } else if (test.getSnId() == null && "0".equals(test.getDelFlag())) {
                unverifiedLegacyTests = true;
            }
        }
        qualityTests.sort(
                Comparator.comparing(
                                TestRecord::getTestTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                TestRecord::getTestRecordId,
                                Comparator.nullsLast(Comparator.reverseOrder())));
        for (NcrOrder ncr : ncrs) {
            require(
                    tenant.equals(ncr.getTenantId())
                            && card.getCardId().equals(ncr.getCardId())
                            && Set.of("0", "1", "2")
                                    .contains(Objects.toString(ncr.getDelFlag(), ""))
                            && Set.of(
                                            INcrOrderService.STATUS_OPEN,
                                            INcrOrderService.STATUS_REVIEW,
                                            INcrOrderService.STATUS_DISPOSITION,
                                            INcrOrderService.STATUS_IN_REWORK,
                                            INcrOrderService.STATUS_CLOSED)
                                    .contains(Objects.toString(ncr.getStatus(), "")),
                    "NCR 来源归属或状态未核验");
        }
        TestRecord latest =
                qualityTests.stream()
                        .filter(t -> "0".equals(t.getDelFlag()))
                        .findFirst()
                        .orElse(null);
        long openNcr =
                ncrs.stream()
                        .filter(
                                n ->
                                        "0".equals(n.getDelFlag())
                                                && Set.of(
                                                                INcrOrderService.STATUS_OPEN,
                                                                        INcrOrderService
                                                                                .STATUS_REVIEW,
                                                                INcrOrderService.STATUS_DISPOSITION,
                                                                        INcrOrderService
                                                                                .STATUS_IN_REWORK)
                                                        .contains(
                                                                Objects.toString(
                                                                        n.getStatus(), "")))
                        .count();
        long duplicates =
                history.stream()
                        .filter(
                                r ->
                                        "PASS".equals(r.getResult())
                                                && (station.equals(r.getStationCode())
                                                        || Objects.equals(
                                                                step.getStepRecordId(),
                                                                r.getStepRecordId())))
                        .count();
        List<ProFlowCardStep> previous =
                steps.stream()
                        .filter(
                                s ->
                                        s.getSequenceNum() < step.getSequenceNum()
                                                && !Set.of("COMPLETED", "SKIPPED")
                                                        .contains(s.getStepStatus()))
                        .toList();
        Map<String, Object> validation =
                StationPassChecks.evaluate(
                        sn, proSn, step, process, previous, duplicates, latest, openNcr);
        applyLegacyTestGap(validation, unverifiedLegacyTests, latest);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) validation.get("checks");
        for (Map<String, Object> c : checks) {
            require(
                    Boolean.TRUE.equals(c.get("ok")),
                    "[" + c.get("code") + "] " + c.get("message"));
        }

        MesStationReport report = new MesStationReport();
        report.setTenantId(tenant);
        report.setReportType("SINGLE");
        report.setSnCode(sn);
        report.setStationCode(station);
        report.setCardId(card.getCardId());
        report.setStepRecordId(step.getStepRecordId());
        report.setMesWoId(proSn.getMesWoId());
        report.setWoNo(proSn.getWoNo());
        report.setOperatorId(op.userId());
        report.setOperatorCode(blankToNull(op.code()));
        report.setOperatorName(op.name());
        report.setResult(result);
        report.setDefectCodes(defects);
        report.setGoodQty("PASS".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO);
        report.setDefectQty("FAIL".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO);
        report.setClientTs(dto.getClientTs());
        report.setClientKey(clientKey);
        report.setRequestFingerprint(fingerprint);
        // 任意唯一键冲突都让整个事务回滚；不可把未知冲突吞成成功。
        require(reportMapper.insert(report) == 1, "报工记录未写入，事务回滚");
        autoStartStep(step, op);
        boolean isPass = "PASS".equals(result);
        int rows =
                stepMapper.update(
                        null,
                        Wrappers.<ProFlowCardStep>lambdaUpdate()
                                .setSql(
                                        isPass
                                                ? "good_qty = good_qty + 1"
                                                : "defect_qty = defect_qty + 1")
                                .eq(ProFlowCardStep::getTenantId, tenant)
                                .eq(ProFlowCardStep::getCardId, card.getCardId())
                                .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId())
                                .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        require(rows == 1, "工序状态已变更或数量未写入，事务回滚");
        if (isPass) {
            advanceSn(proSn, step, card, steps);
        }
        progressService.rebuildForCard(card.getCardId());
        return serialReferences.submissionStatus(reference);
    }

    /**
     * 不良代码按集合规范化；不允许分隔符或超长值改变持久化含义。
     */
    private String canonicalDefects(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        for (String code : codes) {
            require(StringUtils.isNotBlank(code) && !code.contains(","), "不良代码为空或含存储分隔符");
        }
        String joined =
                String.join(",", codes.stream().map(String::trim).distinct().sorted().toList());
        require(joined.length() <= 512, "不良代码超出存储长度");
        return joined;
    }

    /**
     * 重放数量比较不依赖 DECIMAL 的显示小数位。
     */
    private boolean sameQuantity(BigDecimal stored, BigDecimal expected) {
        return stored != null && stored.compareTo(expected) == 0;
    }

    /**
     * 可空文本规范化，保持旧已提交空值的兼容语义。
     */
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 任何未核验条件都先拒绝，不继续业务写入。
     */
    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ServiceException(message);
        }
    }

    /**
     * SN 前移使用已锁定的实际工序序号；末道完工，非末道写下一工序序号。
     */
    private void advanceSn(
            ProSn proSn, ProFlowCardStep step, ProFlowCard card, List<ProFlowCardStep> cardSteps) {
        Integer maxSeq =
                cardSteps.stream()
                        .map(ProFlowCardStep::getSequenceNum)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(null);
        boolean isLastStep = maxSeq != null && maxSeq.equals(step.getSequenceNum());

        // 单件最终状态必须先入账，再由流转卡与工单汇总冻结最终清单。
        if (isLastStep) {
            proSnService.completeSn(proSn.getSnId());
            log.info("SN 末道工序过站完成, SN 完工: snId={}, snCode={}", proSn.getSnId(), proSn.getSnCode());
        } else {
            // 写实际下一道工序的 seq(工序序号不连续时 30→40 而非 30+1), 与 resolveCurrentStep 兜底口径一致
            Integer nextSeq =
                    cardSteps.stream()
                            .map(ProFlowCardStep::getSequenceNum)
                            .filter(s -> s != null && s > step.getSequenceNum())
                            .min(Integer::compareTo)
                            .orElse(null);
            if (nextSeq != null) {
                int rows =
                        proSnMapper.update(
                                null,
                                Wrappers.<ProSn>lambdaUpdate()
                                        .set(ProSn::getCurrentStepSeq, nextSeq)
                                        .eq(ProSn::getTenantId, proSn.getTenantId())
                                        .eq(ProSn::getSnStatus, "IN_PRODUCTION")
                                        .eq(ProSn::getSnId, proSn.getSnId()));
                require(rows == 1, "SN 推进失败，事务回滚");
            }
        }
        completeStepIfQtyReached(step, card);
    }

    /**
     * 工序数量收口: 累计 good+defect+scrap >= planned_qty 时 CAS IN_PROGRESS→COMPLETED
     * (重查累计值, 避免内存旧值), 全工序完成后复用 completeCard 做卡完工聚合。
     */
    private void completeStepIfQtyReached(ProFlowCardStep step, ProFlowCard card) {
        ProFlowCardStep fresh =
                stepMapper.selectOne(
                        Wrappers.<ProFlowCardStep>lambdaQuery()
                                .eq(ProFlowCardStep::getTenantId, TenantHelper.getTenantId())
                                .eq(ProFlowCardStep::getCardId, card.getCardId())
                                .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId())
                                .last("FOR UPDATE"));
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
        int rows =
                stepMapper.update(
                        null,
                        Wrappers.<ProFlowCardStep>lambdaUpdate()
                                .set(ProFlowCardStep::getStepStatus, "COMPLETED")
                                .set(ProFlowCardStep::getActualEndTime, new Date())
                                .eq(ProFlowCardStep::getStepRecordId, fresh.getStepRecordId())
                                .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        if (rows > 0) {
            log.info(
                    "设备通道报工触发工序完工: stepRecordId={}, good={}, defect={}, scrap={}",
                    fresh.getStepRecordId(),
                    good,
                    defect,
                    scrap);
            // 卡上所有工序完成 → 复用既有 completeCard 聚合(卡完工 → 工单数量聚合/完工判定)
            Long pending =
                    stepMapper.selectCount(
                            Wrappers.<ProFlowCardStep>lambdaQuery()
                                    .eq(ProFlowCardStep::getCardId, card.getCardId())
                                    .notIn(ProFlowCardStep::getStepStatus, "COMPLETED", "SKIPPED")
                                    .last("FOR UPDATE"));
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
        } else if ("IN_PROGRESS".equals(step.getStepStatus())
                && step.getOperatorId() == null
                && operator.userId() != null) {
            stepMapper.update(
                    null,
                    Wrappers.<ProFlowCardStep>lambdaUpdate()
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
     * <p>操作员由调用方在事务外解析传入(事务连接钉在 ry-mes, 无法查 ry-erp 的 sch_worker)。</p>
     *
     * @param dto      批量报工请求
     * @param operator 事务外解析好的操作员(可空，按空操作员记录)
     * @return 已提交的MES效果及其原完工清单当前投递状态
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportBatch(StationBatchReportDto dto, OperatorInfo operator) {
        String station = normalizeStationCode(trim(dto.getStationCode()));
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
        List<ProFlowCardStep> inProgressSteps =
                stepMapper.selectList(
                        Wrappers.<ProFlowCardStep>lambdaQuery()
                                .eq(ProFlowCardStep::getProcessCode, processCode)
                                .eq(ProFlowCardStep::getStepStatus, "IN_PROGRESS"));
        if (inProgressSteps.size() != 1) {
            throw new ServiceException(
                    String.format(
                            "工位工序(%s)当前在制工序数=%d(应为1), 无法定位报工目标; 请先开工流转卡工序",
                            processCode, inProgressSteps.size()));
        }
        ProFlowCardStep step = inProgressSteps.get(0);
        preparedPolicies.prepareCard(step.getCardId());
        ProFlowCard card = progressService.lockForCard(step.getCardId());
        step =
                stepMapper.selectOne(
                        Wrappers.<ProFlowCardStep>lambdaQuery()
                                .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId())
                                .last("FOR UPDATE"));
        if (step == null
                || !"IN_PROGRESS".equals(step.getStepStatus())
                || !Objects.equals(step.getCardId(), card.getCardId())
                || !Objects.equals(step.getMesWoId(), card.getMesWoId())) {
            throw new ServiceException("锁定后的批量工序状态已变更");
        }
        if (card == null) {
            throw new ServiceException("工序所属流转卡不存在: stepRecordId=" + step.getStepRecordId());
        }

        // 3. 操作员由调用方在事务外解析传入; 幂等行落库
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
        stepMapper.update(
                null,
                Wrappers.<ProFlowCardStep>lambdaUpdate()
                        .setSql("good_qty = good_qty + {0}", goodQty)
                        .setSql("defect_qty = defect_qty + {0}", defectTotal)
                        .eq(ProFlowCardStep::getStepRecordId, step.getStepRecordId()));

        // 5. 数量达标则收口(与单件路径同一收口逻辑)
        completeStepIfQtyReached(step, card);
        progressService.rebuildForCard(card.getCardId());
        log.info(
                "批量报工完成: station={}, goodQty={}, defectQty={}, stepRecordId={}",
                station,
                goodQty,
                defectTotal,
                step.getStepRecordId());
        return new HashMap<>();
    }

    // ==================== 公共辅助 ====================

    /**
     * 工位码归一化: STN 前缀且含第 6 段(设备编码, M3 工位码第6段设备编码自动录入)时截取前 5 段。
     * 设备号是设备属性而非工位标识, 剥离后保证台账/安灯/报工记录的工位编码一致
     * (客户端保存配置时已归一化, 此处为服务端防御, 兜底异常路径直传 6 段码)。
     */
    public static String normalizeStationCode(String stationCode) {
        if (StringUtils.isBlank(stationCode)) {
            return stationCode;
        }
        String station = stationCode.trim();
        // limit=-1 保留尾部空段("STN|..|GENERAL|" 视为 6 段), 与客户端 split 行为对齐
        String[] parts = station.split("\\|", -1);
        if (parts.length >= 6 && "STN".equalsIgnoreCase(parts[0])) {
            return String.join("|", Arrays.copyOf(parts, 5));
        }
        return station;
    }

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
        MesStationDevice device =
                deviceMapper.selectOne(
                        Wrappers.<MesStationDevice>lambdaQuery()
                                .eq(MesStationDevice::getStationCode, station)
                                .last("LIMIT 1"));
        if (device != null && StringUtils.isNotBlank(device.getProcessCode())) {
            return device.getProcessCode();
        }
        // ② 工位码格式解析
        String[] parts = station.split("\\|");
        if (parts.length >= 5
                && "STN".equalsIgnoreCase(parts[0])
                && StringUtils.isNotBlank(parts[3])) {
            return parts[3].trim();
        }
        return null;
    }

    /**
     * 定位 SN 当前工序: 卡上第一条未完成工序(COMPLETED/SKIPPED 除外)按 seq 升序;
     * 卡上全部完成则兜底按 current_step_seq 直查(无未完成工序时 SN 应已完工, 兜底返回 null)。
     * 与 FlashRecordServiceImpl 的定位口径一致(pro_sn.current_step_seq 无维护逻辑, 不可靠)。
     */
    public ProFlowCardStep resolveCurrentStep(Long cardId, Integer currentStepSeq) {
        if (cardId == null) {
            return null;
        }
        ProFlowCardStep step =
                stepMapper.selectOne(
                        Wrappers.<ProFlowCardStep>lambdaQuery()
                                .eq(ProFlowCardStep::getCardId, cardId)
                                .notIn(ProFlowCardStep::getStepStatus, "COMPLETED", "SKIPPED")
                                .orderByAsc(ProFlowCardStep::getSequenceNum)
                                .last("LIMIT 1"));
        if (step != null) {
            return step;
        }
        if (currentStepSeq == null) {
            return null;
        }
        return stepMapper.selectOne(
                Wrappers.<ProFlowCardStep>lambdaQuery()
                        .eq(ProFlowCardStep::getCardId, cardId)
                        .eq(ProFlowCardStep::getSequenceNum, currentStepSeq)
                        .last("LIMIT 1"));
    }

    /**
     * 操作员解析: operatorId 直用; 否则 operatorCode → sch_worker 档案(user_id/user_name)。
     * 均缺失返回空信息(容忍, 字段落 null)。
     * <p>数据源: 本类级 @DS("mes") 会覆盖 Mapper 接口上的 @DS("erp")(同类内调用不走 AOP 代理),
     * 故用编程式 push("erp") 切库查 ry-erp 的 sch_worker(对齐 MesErpBridgeServiceImpl 模式)。</p>
     */
    public OperatorInfo resolveOperator(Long operatorId, String operatorCode) {
        if (operatorId != null) {
            return new OperatorInfo(operatorId, trim(operatorCode), null);
        }
        String code = trim(operatorCode);
        if (StringUtils.isNotBlank(code)) {
            Map<String, Object> worker =
                    TenantHelper.dynamic(
                            "000000",
                            () -> {
                                DynamicDataSourceContextHolder.push("erp");
                                try {
                                    return schWorkerMapper.selectByEmployeeId(code);
                                } finally {
                                    DynamicDataSourceContextHolder.poll();
                                }
                            });
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
        return String.join(
                ",",
                defectCodes.stream().filter(StringUtils::isNotBlank).map(String::trim).toList());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    /**
     * 操作员信息(userId/code/name, 均可空)。
     *
     * @param userId 当前认证用户标识。
     * @param code 原业务编码或经来源限定的别名。
     * @param name 所属主数据的展示名称。
     */
    public record OperatorInfo(Long userId, String code, String name) {}
}
