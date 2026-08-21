package com.nexus.mes.label.listener;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * SnLabelPrintEventListener 单元测试 — SN 造码联动打印(旁路动作, 异常不外抛)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class SnLabelPrintEventListenerTest {

    @Mock
    private MesLabelTemplateMapper templateMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private LabelPrintService printService;
    /** 真实渲染服务(纯函数), 只 mock TCP 发送侧 */
    private final LabelRenderService renderService = new LabelRenderService();

    private SnLabelPrintEventListener listener;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        listener = new SnLabelPrintEventListener(templateMapper, proSnMapper, renderService, printService);
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> columns = new HashMap<>();
        columns.put("TEMPLATEID", new ColumnCache("template_id", "template_id"));
        columns.put("LABELTYPE", new ColumnCache("label_type", "label_type"));
        columns.put("ENABLED", new ColumnCache("enabled", "enabled"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesLabelTemplate.class)).thenReturn(columns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    @Test
    void noEnabledTemplate_skipsQuietly() {
        when(templateMapper.selectList(any())).thenReturn(List.of());
        SnCreatedEvent event = new SnCreatedEvent(1L, "SN-1", null, 100L);

        listener.onSnCreated(event);

        verify(proSnMapper, never()).selectById(any());
        verify(printService, never()).submit(anyString(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void enabledTemplate_rendersAndSubmits() {
        MesLabelTemplate template = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8,\"size\":5}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(printService).submit(eq("TPL-1"), eq("192.168.1.50"), eq(9100),
            payloadCaptor.capture(), eq(Charset.forName("GBK")));
        assertTrue(payloadCaptor.getValue().contains("\"SN-FC-0001\""), payloadCaptor.getValue());
        assertTrue(payloadCaptor.getValue().startsWith("SIZE 60 mm,40 mm"));
    }

    @Test
    void zplTemplate_submitsUtf8() {
        MesLabelTemplate template = template("TPL-ZPL", "ZPL",
            "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":0,\"y\":0}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        verify(printService).submit(eq("TPL-ZPL"), eq("192.168.1.50"), eq(9100),
            anyString(), eq(StandardCharsets.UTF_8));
    }

    @Test
    void snNotFound_skips() {
        MesLabelTemplate template = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(99L)).thenReturn(null);

        listener.onSnCreated(new SnCreatedEvent(99L, "SN-99", null, 100L));

        verify(printService, never()).submit(anyString(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void malformedTemplateContent_swallowedNotThrown() {
        // 脏模板(非法 JSON)不应把异常抛给事件发布方(造码不受影响)
        MesLabelTemplate template = template("TPL-BAD", "TSPL", "{not-json");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        verify(printService, never()).submit(anyString(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void nullPrinterPort_defaultsTo9100() {
        MesLabelTemplate template = template("TPL-NOPORT", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        template.setPrinterPort(null);
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        verify(printService).submit(eq("TPL-NOPORT"), eq("192.168.1.50"), eq(9100),
            anyString(), any(Charset.class));
    }

    // ==================== 辅助 ====================

    private MesLabelTemplate template(String code, String lang, String content) {
        MesLabelTemplate t = new MesLabelTemplate();
        t.setTemplateId(1L);
        t.setTemplateCode(code);
        t.setLabelType("SN");
        t.setLabelLang(lang);
        t.setWidthMm(60);
        t.setHeightMm(40);
        t.setPrinterHost("192.168.1.50");
        t.setPrinterPort(9100);
        t.setContent(content);
        t.setEnabled("Y");
        return t;
    }

    private ProSn sn(String snCode) {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode(snCode);
        sn.setMaterialCode("GW-1000");
        sn.setMaterialName("网关主板");
        sn.setWoNo("MO-001");
        sn.setCardNo("FC-001");
        sn.setProductionDate(new Date());
        return sn;
    }
}
