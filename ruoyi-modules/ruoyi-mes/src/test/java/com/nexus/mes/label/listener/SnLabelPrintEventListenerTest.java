package com.nexus.mes.label.listener;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelPrintLogMapper;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SnLabelPrintEventListener 单元测试 — SN 造码联动打印(旁路动作, 异常不外抛)
 * <p>覆盖: 无启用模板跳过、渲染提交、ZPL UTF-8、SN 不存在跳过、端口缺省、
 * MES-LABEL-02 幂等(同 SN 同模板已有成功留痕则跳过)、MES-LABEL-03 渲染失败落 FAIL 留痕。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class SnLabelPrintEventListenerTest {

    @Mock
    private MesLabelTemplateMapper templateMapper;
    @Mock
    private MesLabelPrintLogMapper printLogMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private LabelPrintService printService;
    /** 真实渲染服务(纯函数), 只 mock TCP 发送侧 */
    private final LabelRenderService renderService = new LabelRenderService();

    private SnLabelPrintEventListener listener;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MesLabelTemplate.class);
        TableInfoHelper.initTableInfo(assistant, MesLabelPrintLog.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(MesLabelTemplate.class);
        TableInfoHelper.remove(MesLabelPrintLog.class);
        TableInfoHelper.remove(ProSn.class);
    }

    @BeforeEach
    void setUp() {
        listener = new SnLabelPrintEventListener(templateMapper, printLogMapper, proSnMapper, renderService, printService);
        lenient().when(printService.newLog(any(), any(), any(MesLabelTemplate.class), anyString()))
            .thenAnswer(inv -> MesLabelPrintLog.of(inv.getArgument(0), inv.getArgument(1), 1L, "TPL-1",
                "192.168.1.50", ((MesLabelTemplate) inv.getArgument(2)).getPrinterPort(), inv.getArgument(3)));
    }

    @Test
    void noEnabledTemplate_skipsQuietly() {
        when(templateMapper.selectList(any())).thenReturn(List.of());

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-1", null, 100L));

        verify(proSnMapper, never()).selectById(any());
        verify(printService, never()).submit(any(), anyString(), any());
    }

    @Test
    void enabledTemplate_rendersAndSubmits() {
        MesLabelTemplate template = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8,\"size\":5}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(printService).submit(any(MesLabelPrintLog.class), payloadCaptor.capture(), eq(Charset.forName("GBK")));
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

        verify(printService).submit(any(MesLabelPrintLog.class), anyString(), eq(StandardCharsets.UTF_8));
    }

    @Test
    void snNotFound_skips() {
        MesLabelTemplate template = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(99L)).thenReturn(null);

        listener.onSnCreated(new SnCreatedEvent(99L, "SN-99", null, 100L));

        verify(printService, never()).submit(any(), anyString(), any());
    }

    @Test
    void nullPrinterPort_stillSubmitsAndLeavesPortToServiceDefault() {
        MesLabelTemplate template = template("TPL-NOPORT", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        template.setPrinterPort(null);
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        ArgumentCaptor<MesLabelPrintLog> logCaptor = ArgumentCaptor.forClass(MesLabelPrintLog.class);
        verify(printService).submit(logCaptor.capture(), anyString(), any(Charset.class));
        // 端口缺省由 LabelPrintService 统一补 9100(见 LabelPrintServiceTest)
        assertEquals(null, logCaptor.getValue().getPrinterPort());
    }

    // ==================== MES-LABEL-02 幂等 ====================

    @Test
    void alreadyPrintedSnTemplate_skipsSubmit() {
        MesLabelTemplate template = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));
        // 同一 (sn_id, template_id) 已有成功打印留痕 -> 事件重放不得重复出纸
        when(printLogMapper.selectCount(any())).thenReturn(1L);

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        verify(printService, never()).submit(any(), anyString(), any());
    }

    @Test
    void multiTemplate_eachTemplateGetsItsOwnPrintLog() {
        MesLabelTemplate first = template("TPL-1", "TSPL",
            "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}");
        MesLabelTemplate second = template("TPL-2", "ZPL",
            "{\"items\":[{\"type\":\"text\",\"field\":\"snCode\",\"x\":0,\"y\":0}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(first, second));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        // 多模板场景每个模板各一条留痕(可追溯"一个SN打了几张")
        verify(printService, org.mockito.Mockito.times(2)).submit(any(MesLabelPrintLog.class), anyString(), any());
    }

    // ==================== MES-LABEL-03 渲染失败留痕 ====================

    @Test
    void malformedTemplateContent_isLoggedAsFailNotSwallowed() {
        // 脏模板(非法 JSON)不得把异常抛给事件发布方(造码不受影响), 但必须落 FAIL 留痕
        MesLabelTemplate template = template("TPL-BAD", "TSPL", "{not-json");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        verify(printService, never()).submit(any(), anyString(), any());
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(printService).recordFailure(any(MesLabelPrintLog.class), reasonCaptor.capture());
        assertTrue(reasonCaptor.getValue().contains("合法的 JSON"), reasonCaptor.getValue());
    }

    @Test
    void itemWithoutField_isLoggedAsFail() {
        MesLabelTemplate template = template("TPL-NOFIELD", "TSPL", "{\"items\":[{\"type\":\"text\",\"x\":0,\"y\":0}]}");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(proSnMapper.selectById(1L)).thenReturn(sn("SN-FC-0001"));

        listener.onSnCreated(new SnCreatedEvent(1L, "SN-FC-0001", null, 100L));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(printService).recordFailure(any(MesLabelPrintLog.class), reasonCaptor.capture());
        assertTrue(reasonCaptor.getValue().contains("field"), reasonCaptor.getValue());
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
