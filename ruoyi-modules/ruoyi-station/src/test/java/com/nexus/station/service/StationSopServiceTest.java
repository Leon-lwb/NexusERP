package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.domain.SopProcessBinding;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopProcessBindingMapper;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationSopService 单元测试 — 工位 SOP 下发/版本比对/下载解析(C16-C18)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationSopServiceTest {

    @Mock
    private StationDeviceMapper deviceMapper;
    @Mock
    private SopProcessBindingMapper bindingMapper;
    @Mock
    private SopDocumentMapper documentMapper;
    @Mock
    private SopViewLogMapper viewLogMapper;
    @Mock
    private ISysOssService sysOssService;
    @InjectMocks
    private StationSopService service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> deviceColumns = new HashMap<>();
        deviceColumns.put("DEVICEID", new ColumnCache("device_id", "device_id"));
        deviceColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        deviceColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesStationDevice.class)).thenReturn(deviceColumns);
        Map<String, ColumnCache> bindingColumns = new HashMap<>();
        bindingColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        bindingColumns.put("ISDEFAULT", new ColumnCache("is_default", "is_default"));
        bindingColumns.put("SORTORDER", new ColumnCache("sort_order", "sort_order"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(SopProcessBinding.class)).thenReturn(bindingColumns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    // ==================== C16 工位当前 SOP 文档列表 ====================

    @Test
    void current_multiDocs_bindingOrderAndPublishedFilter() {
        // 设备心跳绑定工序 ASM
        when(deviceMapper.selectOne(any())).thenReturn(device("STN|JZ|L1|ASM|GENERAL", "ASM"));
        // 绑定顺序: 默认文档 sop2 在前, sop1 在后(SQL orderBy 保证, mock 按此顺序返回)
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(2L, "1"), binding(1L, "0")));
        when(documentMapper.selectByIds(anyList())).thenReturn(List.of(
            doc(1L, "PUBLISHED", "1.0"),
            doc(2L, "PUBLISHED", "2.3")));

        Map<String, Object> data = service.current("STN|JZ|L1|ASM|GENERAL", "张明", "192.168.1.7");

        assertEquals("ASM", data.get("processCode"));
        List<?> docs = (List<?>) data.get("docs");
        assertEquals(2, docs.size());
        // 绑定顺序(默认优先)透传到文档列表
        assertEquals(2L, ((Map<?, ?>) docs.get(0)).get("sopId"));
        assertEquals(1L, ((Map<?, ?>) docs.get(1)).get("sopId"));
        assertEquals("2.3", ((Map<?, ?>) docs.get(0)).get("versionNo"));
        assertEquals("PDF", ((Map<?, ?>) docs.get(0)).get("fileType"));
        // 逐文档登记查阅留痕
        ArgumentCaptor<SopViewLog> logCaptor = ArgumentCaptor.forClass(SopViewLog.class);
        verify(viewLogMapper, org.mockito.Mockito.times(2)).insert(logCaptor.capture());
        assertEquals("张明", logCaptor.getAllValues().get(0).getViewerName());
        assertEquals("192.168.1.7", logCaptor.getAllValues().get(0).getIpAddress());
    }

    @Test
    void current_draftAndArchivedDocsFiltered() {
        when(deviceMapper.selectOne(any())).thenReturn(device("STN|JZ|L1|ASM|GENERAL", "ASM"));
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(1L, "1"), binding(2L, "0")));
        // 一个 DRAFT 一个 PUBLISHED → 仅已发布下发
        when(documentMapper.selectByIds(anyList())).thenReturn(List.of(
            doc(1L, "DRAFT", "0.9"),
            doc(2L, "PUBLISHED", "1.0")));

        Map<String, Object> data = service.current("STN|JZ|L1|ASM|GENERAL", null, null);

        List<?> docs = (List<?>) data.get("docs");
        assertEquals(1, docs.size());
        assertEquals(2L, ((Map<?, ?>) docs.get(0)).get("sopId"));
    }

    @Test
    void current_noDevice_fallsBackToStationCodeParse() {
        // 设备未注册(心跳未上报) → 工位码第 4 段解析兜底
        when(deviceMapper.selectOne(any())).thenReturn(null);
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(1L, "1")));
        when(documentMapper.selectByIds(anyList())).thenReturn(List.of(doc(1L, "PUBLISHED", "1.0")));

        Map<String, Object> data = service.current("STN|注塑车间|产线1|PKG|GENERAL", null, null);

        assertEquals("PKG", data.get("processCode"));
        assertEquals(1, ((List<?>) data.get("docs")).size());
    }

    @Test
    void current_noBinding_returnsEmpty() {
        when(deviceMapper.selectOne(any())).thenReturn(device("STN|JZ|L1|ASM|GENERAL", "ASM"));
        when(bindingMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.current("STN|JZ|L1|ASM|GENERAL", null, null);

        assertTrue(((List<?>) data.get("docs")).isEmpty());
        verify(viewLogMapper, never()).insert(any(SopViewLog.class));
    }

    @Test
    void current_sixSegmentCode_normalized() {
        // 6 段工位码(带设备号)归一化后仍按 station_code 命中台账
        when(deviceMapper.selectOne(any())).thenReturn(device("STN|JZ|L1|ASM|GENERAL", "ASM"));
        when(bindingMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.current("STN|JZ|L1|ASM|GENERAL|IPC-0007", null, null);

        assertEquals("ASM", data.get("processCode"));
    }

    // ==================== C18 版本轻量查询 ====================

    @Test
    void versionCheck_lightweight_noLogNoDetail() {
        when(deviceMapper.selectOne(any())).thenReturn(device("STN|JZ|L1|ASM|GENERAL", "ASM"));
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(1L, "1")));
        when(documentMapper.selectByIds(anyList())).thenReturn(List.of(doc(1L, "PUBLISHED", "1.0")));

        Map<String, Object> data = service.versionCheck("STN|JZ|L1|ASM|GENERAL");

        List<?> docs = (List<?>) data.get("docs");
        assertEquals(1, docs.size());
        Map<?, ?> row = (Map<?, ?>) docs.get(0);
        assertEquals(1L, row.get("sopId"));
        assertEquals("1.0", row.get("versionNo"));
        assertFalse(row.containsKey("sopName"), "轻量版不应带文档明细字段");
        verify(viewLogMapper, never()).insert(any(SopViewLog.class));
    }

    // ==================== C17 下载解析 ====================

    @Test
    void resolveDownload_success() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, "PUBLISHED", "1.0"));
        SysOssVo ossVo = new SysOssVo();
        ossVo.setOssId(500L);
        ossVo.setFileName("oss/2026/08/sop-1.pdf");
        when(sysOssService.getById(500L)).thenReturn(ossVo);

        Map<String, Object> resolved = service.resolveDownload(1L);

        assertEquals(1L, ((SopDocument) resolved.get("doc")).getSopId());
        assertEquals("oss/2026/08/sop-1.pdf", ((SysOssVo) resolved.get("ossVo")).getFileName());
    }

    @Test
    void resolveDownload_notFound_throws() {
        when(documentMapper.selectById(99L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.resolveDownload(99L));
    }

    @Test
    void resolveDownload_notPublished_throws() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, "DRAFT", "0.9"));
        assertThrows(ServiceException.class, () -> service.resolveDownload(1L));
    }

    @Test
    void resolveDownload_noFileOssId_throws() {
        SopDocument doc = doc(1L, "PUBLISHED", "1.0");
        doc.setFileOssId(null);
        when(documentMapper.selectById(1L)).thenReturn(doc);
        assertThrows(ServiceException.class, () -> service.resolveDownload(1L));
    }

    @Test
    void resolveDownload_ossRecordMissing_throws() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, "PUBLISHED", "1.0"));
        when(sysOssService.getById(500L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.resolveDownload(1L));
    }

    // ==================== 辅助 ====================

    private MesStationDevice device(String stationCode, String processCode) {
        MesStationDevice d = new MesStationDevice();
        d.setDeviceId(7L);
        d.setDeviceCode("IPC-0007");
        d.setStationCode(stationCode);
        d.setProcessCode(processCode);
        return d;
    }

    private SopProcessBinding binding(Long sopId, String isDefault) {
        SopProcessBinding b = new SopProcessBinding();
        b.setBindingId(sopId);
        b.setProcessCode("ASM");
        b.setSopId(sopId);
        b.setIsDefault(isDefault);
        b.setSortOrder(0);
        return b;
    }

    private SopDocument doc(Long sopId, String status, String version) {
        SopDocument d = new SopDocument();
        d.setSopId(sopId);
        d.setSopCode("SOP-" + sopId);
        d.setSopName("装配作业指导-" + sopId);
        d.setSopType("PRODUCTION");
        d.setStatus(status);
        d.setCurrentVersion(version);
        d.setFileOssId("500");
        d.setFileType("PDF");
        return d;
    }
}
