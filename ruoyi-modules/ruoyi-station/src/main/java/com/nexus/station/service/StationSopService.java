package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.domain.SopProcessBinding;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopProcessBindingMapper;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import com.nexus.mes.sop.service.ISopDocumentService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工位终端 SOP 服务 (M3 契约 C16-C18)
 * <p>按工位工序码拉取绑定的已发布 SOP 文档列表(供工位端全屏轮播)。
 * 关联链: 工位工序码(心跳绑定值优先, 工位码第 4 段兜底) →
 * sop_process_binding.process_code(冗余列, 免 ERP 跨库) → sop_document(仅 PUBLISHED),
 * is_default 优先 + sort_order 升序 —— 与管理端 getSopForProcess 口径一致但返回多文档列表(轮播)。</p>
 * <p>文件下载走 Controller 流式代理(仿固件下载), OSS 记录在 ry-vue(master) 库:
 * 本类级 @DS("mes") 下用编程式 push("master") 读取(FirmwareServiceImpl 先例)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationSopService {

    private final StationDeviceMapper deviceMapper;
    private final SopProcessBindingMapper bindingMapper;
    private final SopDocumentMapper documentMapper;
    private final SopViewLogMapper viewLogMapper;
    private final ISysOssService sysOssService;

    /**
     * 工位当前 SOP 文档列表(C16): 按绑定顺序返回已发布文档, 并逐文档登记查阅留痕。
     *
     * @param stationCode  工位编码(支持 6 段码, 自动归一化)
     * @param operatorName 在岗操作员姓名(查阅留痕, 可空)
     * @param ip           工位机 IP(查阅留痕, 可空)
     * @return {processCode, processName, docs: [{sopId, sopCode, sopName, sopType, versionNo, fileType}]}
     */
    public Map<String, Object> current(String stationCode, String operatorName, String ip) {
        String processCode = resolveProcessCode(stationCode);
        List<SopDocument> docs = publishedDocs(processCode);
        Map<String, Object> data = docRows(processCode, docs, true);
        for (SopDocument doc : docs) {
            recordViewLog(doc, operatorName, ip);
        }
        return data;
    }

    /**
     * 版本轻量查询(C18, 轮询比对用): 不登记查阅日志, 仅返回 sopId+版本号集合。
     *
     * @param stationCode 工位编码
     * @return {processCode, docs: [{sopId, versionNo}]}
     */
    public Map<String, Object> versionCheck(String stationCode) {
        String processCode = resolveProcessCode(stationCode);
        return docRows(processCode, publishedDocs(processCode), false);
    }

    /**
     * 下载前解析(C17 前半段, 业务校验可单测): PUBLISHED 门禁 + fileOssId 校验 +
     * 跨库读 sys_oss(私有桶对象记录)。
     *
     * @param sopId SOP 文档ID
     * @return {doc: SopDocument, ossVo: SysOssVo}
     */
    public Map<String, Object> resolveDownload(Long sopId) {
        if (sopId == null) {
            throw new ServiceException("sopId 不能为空");
        }
        SopDocument doc = documentMapper.selectById(sopId);
        if (doc == null) {
            throw new ServiceException("SOP 文档不存在: " + sopId);
        }
        if (!ISopDocumentService.STATUS_PUBLISHED.equals(doc.getStatus())) {
            throw new ServiceException("仅已发布(PUBLISHED)的 SOP 可下载: " + doc.getSopCode() + " 当前=" + doc.getStatus());
        }
        if (StringUtils.isBlank(doc.getFileOssId())) {
            throw new ServiceException("SOP 文档未上传文件: " + doc.getSopCode());
        }
        Long ossId;
        try {
            ossId = Long.parseLong(doc.getFileOssId().trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("SOP 文件记录 ID 非法: " + doc.getFileOssId());
        }
        // sys_oss 位于 ry-vue(master) 库: 类级 @DS("mes") 已切线程数据源, 手动 push master 读取(固件下载先例)
        SysOssVo ossVo;
        DynamicDataSourceContextHolder.push("master");
        try {
            ossVo = sysOssService.getById(ossId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        if (ossVo == null || StringUtils.isBlank(ossVo.getFileName())) {
            throw new ServiceException("SOP 文件记录不存在: ossId=" + ossId);
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("doc", doc);
        data.put("ossVo", ossVo);
        return data;
    }

    // ==================== 辅助 ====================

    /** 工位工序码 → 绑定的已发布文档列表(is_default 优先 + sort_order 升序) */
    private List<SopDocument> publishedDocs(String processCode) {
        if (processCode == null) {
            return List.of();
        }
        List<SopProcessBinding> bindings = bindingMapper.selectList(Wrappers.<SopProcessBinding>lambdaQuery()
            .eq(SopProcessBinding::getProcessCode, processCode)
            .orderByDesc(SopProcessBinding::getIsDefault)
            .orderByAsc(SopProcessBinding::getSortOrder));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> sopIds = bindings.stream().map(SopProcessBinding::getSopId).distinct().toList();
        Map<Long, SopDocument> docMap = documentMapper.selectByIds(sopIds).stream()
            .collect(Collectors.toMap(SopDocument::getSopId, Function.identity(), (a, b) -> a));
        // 按绑定顺序输出且仅保留已发布文档(草稿/归档不下发)
        List<SopDocument> docs = new ArrayList<>();
        for (SopProcessBinding binding : bindings) {
            SopDocument doc = docMap.get(binding.getSopId());
            if (doc != null && ISopDocumentService.STATUS_PUBLISHED.equals(doc.getStatus())) {
                docs.add(doc);
            }
        }
        return docs;
    }

    /** 组装返回结构(withDetail=false 时仅 sopId+versionNo(轮询轻量版) */
    private Map<String, Object> docRows(String processCode, List<SopDocument> docs, boolean withDetail) {
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("processCode", processCode);
        List<Map<String, Object>> rows = new ArrayList<>(docs.size());
        for (SopDocument doc : docs) {
            Map<String, Object> row = new LinkedHashMap<>(8);
            row.put("sopId", doc.getSopId());
            row.put("versionNo", doc.getCurrentVersion());
            if (withDetail) {
                row.put("sopCode", doc.getSopCode());
                row.put("sopName", doc.getSopName());
                row.put("sopType", doc.getSopType());
                row.put("fileType", doc.getFileType());
            }
            rows.add(row);
        }
        data.put("docs", rows);
        return data;
    }

    /** 工位工序码: 心跳绑定值(mes_station_device.process_code)优先, 工位码第 4 段解析兜底(与 LinkageService 同口径) */
    private String resolveProcessCode(String stationCode) {
        String station = StationPassService.normalizeStationCode(stationCode);
        if (StringUtils.isBlank(station)) {
            return null;
        }
        MesStationDevice device = deviceMapper.selectOne(Wrappers.<MesStationDevice>lambdaQuery()
            .eq(MesStationDevice::getStationCode, station)
            .last("LIMIT 1"));
        if (device != null && StringUtils.isNotBlank(device.getProcessCode())) {
            return device.getProcessCode().trim();
        }
        String[] parts = station.split("\\|", -1);
        if (parts.length >= 5 && "STN".equalsIgnoreCase(parts[0]) && StringUtils.isNotBlank(parts[3])) {
            return parts[3].trim();
        }
        return null;
    }

    /** 查阅留痕: 设备通道免登录, viewerName 取在岗操作员(无则留空), IP 为工位机地址 */
    private void recordViewLog(SopDocument doc, String operatorName, String ip) {
        try {
            SopViewLog logRow = new SopViewLog();
            logRow.setSopId(doc.getSopId());
            logRow.setVersionNo(doc.getCurrentVersion());
            logRow.setViewerName(StringUtils.blankToDefault(operatorName, null));
            logRow.setViewTime(new Date());
            logRow.setIpAddress(ip);
            viewLogMapper.insert(logRow);
        } catch (Exception e) {
            // 查阅留痕是旁路动作, 失败不影响 SOP 下发
            log.warn("SOP 查阅留痕失败(忽略): sopId={}, error={}", doc.getSopId(), e.getMessage());
        }
    }
}
