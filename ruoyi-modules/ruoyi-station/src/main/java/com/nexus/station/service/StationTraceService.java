package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.station.domain.MesSnBindLog;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.mapper.StationSnBindLogMapper;
import com.nexus.station.mapper.StationReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工位终端 SN 追溯查询服务 (M3 契约 C13)
 * <p>按 SN 聚合全履历时间线(现场快速答问: "这块板烧的什么版本?"):
 * SN 概要(pro_sn) + 工序链(pro_flow_card_step) + 四源记录按时间倒序合并 ——
 * 刷写(mes_flash_record)/综测(mes_test_record, 按 test_no 分组一次测试多 item 行归一)/
 * 过站报工(mes_station_report)/关键件绑定(mes_sn_bind_log)。</p>
 * <p>数据源切换: 类级 @DS("mes"); mes_flash_record/mes_test_record 现有索引按 sn_id,
 * 此处按 sn_code 查为全表扫描(当前数据量小可接受, 数据量上来后建议补 sn_code 索引)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationTraceService {

    /** 时间线记录类型 */
    public static final String TYPE_FLASH = "FLASH";
    public static final String TYPE_TEST = "TEST";
    public static final String TYPE_PASS_REPORT = "PASS_REPORT";
    public static final String TYPE_SN_BIND = "SN_BIND";

    /** 单源查询上限(防异常数据撑爆响应) */
    private static final int SOURCE_LIMIT = 200;

    private final ProSnMapper proSnMapper;
    private final ProFlowCardStepMapper stepMapper;
    private final FlashRecordMapper flashRecordMapper;
    private final TestRecordMapper testRecordMapper;
    private final StationReportMapper reportMapper;
    private final StationSnBindLogMapper bindLogMapper;

    /**
     * 按 SN 聚合全履历
     *
     * @param snCode SN编码
     * @return {sn, steps, records: [{type, time, result?, summary, items?}]} 记录按时间倒序
     */
    public Map<String, Object> trace(String snCode) {
        String sn = snCode == null ? null : snCode.trim();
        if (StringUtils.isBlank(sn)) {
            throw new ServiceException("snCode 不能为空");
        }
        ProSn proSn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, sn).last("LIMIT 1"));
        if (proSn == null) {
            throw new ServiceException("SN 不存在: " + sn);
        }

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("sn", snSummary(proSn));
        data.put("steps", steps(proSn.getCardId()));
        data.put("records", records(sn, proSn));
        return data;
    }

    private Map<String, Object> snSummary(ProSn sn) {
        Map<String, Object> m = new LinkedHashMap<>(10);
        m.put("snCode", sn.getSnCode());
        m.put("woNo", sn.getWoNo());
        m.put("cardNo", sn.getCardNo());
        m.put("materialCode", sn.getMaterialCode());
        m.put("materialName", sn.getMaterialName());
        m.put("snStatus", sn.getSnStatus());
        m.put("parentSnCode", sn.getParentSnCode());
        return m;
    }

    /** 工序链: 卡上全部工序按序号升序(进度一览) */
    private List<Map<String, Object>> steps(Long cardId) {
        if (cardId == null) {
            return List.of();
        }
        List<ProFlowCardStep> steps = stepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getCardId, cardId)
            .orderByAsc(ProFlowCardStep::getSequenceNum));
        List<Map<String, Object>> rows = new ArrayList<>(steps.size());
        for (ProFlowCardStep s : steps) {
            Map<String, Object> m = new LinkedHashMap<>(8);
            m.put("sequenceNum", s.getSequenceNum());
            m.put("processCode", s.getProcessCode());
            m.put("processName", s.getProcessName());
            m.put("stepStatus", s.getStepStatus());
            m.put("goodQty", s.getGoodQty());
            m.put("defectQty", s.getDefectQty());
            m.put("actualEndTime", s.getActualEndTime());
            rows.add(m);
        }
        return rows;
    }

    /** 四源记录合并时间线, 按时间倒序(最新在前) */
    private List<Map<String, Object>> records(String sn, ProSn proSn) {
        List<Map<String, Object>> records = new ArrayList<>();
        records.addAll(flashRecords(sn));
        records.addAll(testRecords(sn));
        records.addAll(passReports(sn));
        records.addAll(bindLogs(sn));
        records.sort(Comparator.comparing(r -> (Date) r.get("time"), Comparator.nullsLast(Comparator.reverseOrder())));
        return records;
    }

    private List<Map<String, Object>> flashRecords(String sn) {
        List<FlashRecord> rows = flashRecordMapper.selectList(Wrappers.<FlashRecord>lambdaQuery()
            .eq(FlashRecord::getSnCode, sn)
            .orderByDesc(FlashRecord::getFlashTime)
            .last("LIMIT " + SOURCE_LIMIT));
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (FlashRecord r : rows) {
            Map<String, Object> m = base(TYPE_FLASH, nvlDate(r.getFlashTime(), r.getCreateTime()));
            m.put("time", r.getFlashTime());
            m.put("result", r.getResult());
            m.put("summary", String.format("刷写[%s] 版本%s %s",
                nvl(r.getFlashTypeCode()), nvl(r.getContentVersion()), nvl(r.getResult())));
            Map<String, Object> detail = new LinkedHashMap<>(8);
            detail.put("flashTypeCode", r.getFlashTypeCode());
            detail.put("contentVersion", r.getContentVersion());
            detail.put("equipmentCode", r.getEquipmentCode());
            detail.put("operatorName", r.getOperatorName());
            detail.put("failReason", r.getFailReason());
            detail.put("retryCount", r.getRetryCount());
            m.put("detail", detail);
            records.add(m);
        }
        return records;
    }

    /** 综测按 test_no 分组: 一次测试多 item 行归一, 组内任一 FAIL 则整组 FAIL */
    private List<Map<String, Object>> testRecords(String sn) {
        List<TestRecord> rows = testRecordMapper.selectList(Wrappers.<TestRecord>lambdaQuery()
            .eq(TestRecord::getSnCode, sn)
            .orderByDesc(TestRecord::getTestTime)
            .last("LIMIT " + SOURCE_LIMIT));
        Map<String, List<TestRecord>> groups = new LinkedHashMap<>();
        for (TestRecord r : rows) {
            groups.computeIfAbsent(r.getTestNo() == null ? "" : r.getTestNo(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> records = new ArrayList<>(groups.size());
        for (List<TestRecord> group : groups.values()) {
            TestRecord head = group.get(0);
            boolean anyFail = group.stream().anyMatch(r -> "FAIL".equals(r.getResult()));
            Map<String, Object> m = base(TYPE_TEST, head.getTestTime());
            m.put("result", anyFail ? "FAIL" : "PASS");
            m.put("summary", String.format("综测[%s] 版本%s %s %d项",
                head.getTestNo(), nvl(head.getContentVersion()), anyFail ? "FAIL" : "PASS", group.size()));
            List<Map<String, Object>> items = new ArrayList<>(group.size());
            for (TestRecord r : group) {
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("itemCode", r.getItemCode());
                item.put("itemName", r.getItemName());
                item.put("value", r.getNumericValue() != null ? String.valueOf(r.getNumericValue()) : r.getTextValue());
                item.put("upperLimit", r.getUpperLimit());
                item.put("lowerLimit", r.getLowerLimit());
                item.put("pass", !"FAIL".equals(r.getResult()));
                items.add(item);
            }
            Map<String, Object> detail = new LinkedHashMap<>(4);
            detail.put("equipmentCode", head.getEquipmentCode());
            detail.put("testerName", head.getTesterName());
            detail.put("items", items);
            m.put("detail", detail);
            records.add(m);
        }
        return records;
    }

    private List<Map<String, Object>> passReports(String sn) {
        List<MesStationReport> rows = reportMapper.selectList(Wrappers.<MesStationReport>lambdaQuery()
            .eq(MesStationReport::getSnCode, sn)
            .orderByDesc(MesStationReport::getCreateTime)
            .last("LIMIT " + SOURCE_LIMIT));
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (MesStationReport r : rows) {
            Map<String, Object> m = base(TYPE_PASS_REPORT, r.getCreateTime());
            m.put("result", r.getResult());
            m.put("summary", String.format("过站报工[%s] %s%s",
                nvl(r.getStationCode()), nvl(r.getResult()),
                StringUtils.isNotBlank(r.getDefectCodes()) ? " 不良:" + r.getDefectCodes() : ""));
            Map<String, Object> detail = new LinkedHashMap<>(6);
            detail.put("stationCode", r.getStationCode());
            detail.put("operatorName", r.getOperatorName());
            detail.put("operatorCode", r.getOperatorCode());
            detail.put("defectCodes", r.getDefectCodes());
            detail.put("woNo", r.getWoNo());
            m.put("detail", detail);
            records.add(m);
        }
        return records;
    }

    private List<Map<String, Object>> bindLogs(String sn) {
        // SN 可能是父件(被绑定到成品)也可能是组件(绑定到主板), 两侧都查
        List<MesSnBindLog> rows = bindLogMapper.selectList(Wrappers.<MesSnBindLog>lambdaQuery()
            .and(w -> w.eq(MesSnBindLog::getParentSnCode, sn).or().eq(MesSnBindLog::getComponentSnCode, sn))
            .orderByDesc(MesSnBindLog::getCreateTime)
            .last("LIMIT " + SOURCE_LIMIT));
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (MesSnBindLog r : rows) {
            Map<String, Object> m = base(TYPE_SN_BIND, r.getCreateTime());
            String relation = sn.equals(r.getParentSnCode())
                ? "组件 " + nvl(r.getComponentSnCode()) + " 绑定到本SN"
                : "本SN 绑定到 " + nvl(r.getParentSnCode());
            if ("UNBIND".equals(r.getAction())) {
                relation = sn.equals(r.getParentSnCode())
                    ? "组件 " + nvl(r.getComponentSnCode()) + " 从本SN 解绑"
                    : "本SN 从 " + nvl(r.getParentSnCode()) + " 解绑";
            } else if ("REBIND".equals(r.getAction())) {
                relation += " (换板 → " + nvl(r.getNewComponentSnCode()) + ")";
            }
            m.put("summary", String.format("关键件%s: %s", nvl(r.getAction()), relation));
            Map<String, Object> detail = new LinkedHashMap<>(6);
            detail.put("action", r.getAction());
            detail.put("parentSnCode", r.getParentSnCode());
            detail.put("componentSnCode", r.getComponentSnCode());
            detail.put("newComponentSnCode", r.getNewComponentSnCode());
            detail.put("reason", r.getReason());
            detail.put("operatorCode", r.getOperatorCode());
            m.put("detail", detail);
            records.add(m);
        }
        return records;
    }

    private Map<String, Object> base(String type, Date time) {
        Map<String, Object> m = new LinkedHashMap<>(6);
        m.put("type", type);
        m.put("time", time);
        return m;
    }

    private Date nvlDate(Date primary, Date fallback) {
        return primary != null ? primary : fallback;
    }

    private String nvl(String v) {
        return v == null ? "-" : v;
    }
}
