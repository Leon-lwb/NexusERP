package com.nexus.mes.label.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelPrintLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LabelPrintService 单元测试 — MES-LABEL-02 打印留痕回归
 * <p>覆盖: 发送成功/失败都落 mes_label_print_log(失败含原因)、未配置打印机地址落 FAIL、
 * 渲染失败留痕、重打计数、留痕对象快照字段。用本地 ServerSocket 充当"在线打印机"。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class LabelPrintServiceTest {

    @Mock
    private MesLabelPrintLogMapper printLogMapper;

    @Captor
    private ArgumentCaptor<MesLabelPrintLog> logCaptor;

    private LabelPrintService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MesLabelPrintLog.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(MesLabelPrintLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new LabelPrintService(printLogMapper);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private MesLabelPrintLog log(String host, Integer port, String source) {
        return MesLabelPrintLog.of(1L, "SN-FC-0001", 5L, "TPL-1", host, port, source);
    }

    // ==================== 成功/失败留痕 ====================

    @Test
    void send_successPersistsSuccessLog() throws Exception {
        when(printLogMapper.selectCount(any())).thenReturn(0L);
        try (ServerSocket printer = new ServerSocket(0)) {
            MesLabelPrintLog printLog = log("127.0.0.1", printer.getLocalPort(), MesLabelPrintLog.SOURCE_AUTO);

            assertTrue(service.send(printLog, "PAYLOAD", StandardCharsets.UTF_8));
        }

        verify(printLogMapper).insert(logCaptor.capture());
        MesLabelPrintLog saved = logCaptor.getValue();
        assertEquals(MesLabelPrintLog.RESULT_SUCCESS, saved.getResult(), "成功打印必须落 SUCCESS 留痕");
        assertNull(saved.getFailReason());
        assertEquals(0, saved.getRetryCount(), "首次打印重打计数为 0");
        assertNotNull(saved.getPrintTime());
        assertEquals("SN-FC-0001", saved.getSnCode());
        assertEquals(5L, saved.getTemplateId());
    }

    @Test
    void send_failurePersistsFailLogWithReasonAndRetryCount() throws Exception {
        when(printLogMapper.selectCount(any())).thenReturn(2L);
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        MesLabelPrintLog printLog = log("127.0.0.1", closedPort, MesLabelPrintLog.SOURCE_AUTO);

        assertFalse(service.send(printLog, "PAYLOAD", StandardCharsets.UTF_8));

        verify(printLogMapper).insert(logCaptor.capture());
        MesLabelPrintLog saved = logCaptor.getValue();
        assertEquals(MesLabelPrintLog.RESULT_FAIL, saved.getResult(), "打印失败也必须落库(修复前不落库)");
        assertNotNull(saved.getFailReason(), "失败原因必须落库");
        assertEquals(2, saved.getRetryCount(), "重打计数取历史打印次数");
    }

    @Test
    void send_blankHostPersistsFailLog() {
        when(printLogMapper.selectCount(any())).thenReturn(0L);
        MesLabelPrintLog printLog = log(" ", 9100, MesLabelPrintLog.SOURCE_AUTO);

        assertFalse(service.send(printLog, "PAYLOAD", StandardCharsets.UTF_8));

        verify(printLogMapper).insert(logCaptor.capture());
        assertEquals(MesLabelPrintLog.RESULT_FAIL, logCaptor.getValue().getResult());
        assertEquals("未配置打印机地址", logCaptor.getValue().getFailReason());
    }

    @Test
    void send_nullPortFallsBackToDefaultAndStillPersists() {
        when(printLogMapper.selectCount(any())).thenReturn(0L);
        MesLabelPrintLog printLog = log("127.0.0.1", null, MesLabelPrintLog.SOURCE_AUTO);

        service.send(printLog, "PAYLOAD", StandardCharsets.UTF_8);

        verify(printLogMapper).insert(logCaptor.capture());
        assertNotNull(logCaptor.getValue().getResult(), "端口缺省(9100)时仍须落留痕");
    }

    // ==================== 渲染失败留痕(MES-LABEL-03 共用) ====================

    @Test
    void recordFailure_persistsFailLogWithReason() {
        when(printLogMapper.selectCount(any())).thenReturn(0L);
        MesLabelPrintLog printLog = log("10.0.0.9", 9100, MesLabelPrintLog.SOURCE_AUTO);

        service.recordFailure(printLog, "模板内容不是合法的 JSON");

        verify(printLogMapper).insert(logCaptor.capture());
        MesLabelPrintLog saved = logCaptor.getValue();
        assertEquals(MesLabelPrintLog.RESULT_FAIL, saved.getResult());
        assertEquals("模板内容不是合法的 JSON", saved.getFailReason());
        assertNotNull(saved.getPrintTime());
    }

    @Test
    void recordFailure_truncatesOverlongReason() {
        when(printLogMapper.selectCount(any())).thenReturn(0L);
        MesLabelPrintLog printLog = log("10.0.0.9", 9100, MesLabelPrintLog.SOURCE_REPRINT);

        service.recordFailure(printLog, "x".repeat(900));

        verify(printLogMapper).insert(logCaptor.capture());
        assertEquals(500, logCaptor.getValue().getFailReason().length(), "失败原因须截断到列长");
    }

    // ==================== 留痕对象构造 ====================

    @Test
    void newLog_snapshotsTemplateAndSource() {
        MesLabelTemplate template = new MesLabelTemplate();
        template.setTemplateId(9L);
        template.setTemplateCode("TPL-9");
        template.setPrinterHost("10.0.0.9");
        template.setPrinterPort(9101);

        MesLabelPrintLog printLog = service.newLog(1L, "SN-1", template, MesLabelPrintLog.SOURCE_REPRINT);

        assertEquals(1L, printLog.getSnId());
        assertEquals("SN-1", printLog.getSnCode());
        assertEquals(9L, printLog.getTemplateId());
        assertEquals("TPL-9", printLog.getTemplateCode(), "模板编码须快照(模板改名后仍可追溯)");
        assertEquals("10.0.0.9", printLog.getPrinterHost());
        assertEquals(9101, printLog.getPrinterPort());
        assertEquals(MesLabelPrintLog.SOURCE_REPRINT, printLog.getPrintSource());
        assertEquals(0, printLog.getRetryCount());
    }
}
