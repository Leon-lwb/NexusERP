package com.nexus.mes.label.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.MesLabelTemplate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LabelRenderService 单元测试 — TSPL/ZPL 双语言渲染矩阵(纯函数, 无 Spring 依赖)。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
class LabelRenderServiceTest {

    private final LabelRenderService renderService =
            new LabelRenderService(mock(SerialIdentityPort.class));

    private MesLabelTemplate template(String lang, String content) {
        MesLabelTemplate t = new MesLabelTemplate();
        t.setTemplateCode("TPL-1");
        t.setLabelType("SN");
        t.setLabelLang(lang);
        t.setWidthMm(60);
        t.setHeightMm(40);
        t.setPrinterHost("192.168.1.50");
        t.setPrinterPort(9100);
        t.setContent(content);
        return t;
    }

    private Map<String, String> fields() {
        Map<String, String> f = new HashMap<>();
        f.put("snCode", "SN-FC-0001");
        f.put("materialCode", "GW-1000");
        f.put("materialName", "网关主板");
        f.put("woNo", "MO-001");
        f.put("cardNo", "FC-001");
        f.put("productionDate", "2026-08-21");
        return f;
    }

    // ==================== TSPL ====================

    @Test
    void tspl_rendersHeaderAndAllItemTypes() {
        String content =
                """
                {"items":[
                  {"type":"qrcode","field":"snCode","x":8,"y":8,"size":5},
                  {"type":"barcode","field":"snCode","x":8,"y":60,"height":48},
                  {"type":"text","field":"materialName","x":8,"y":120,"size":24}
                ]}""";
        String payload = renderService.render(template("TSPL", content), fields());

        assertTrue(payload.startsWith("SIZE 60 mm,40 mm"), "应以 SIZE 头开始: " + payload);
        assertTrue(payload.contains("GAP 2 mm,0"));
        assertTrue(payload.contains("DIRECTION 1,0"));
        assertTrue(payload.contains("QRCODE 8,8,H,5,A,0,M2,S7,\"SN-FC-0001\""));
        assertTrue(payload.contains("BARCODE 8,60,\"128\",48,0,0,2,2,\"SN-FC-0001\""));
        // 字号 24 = TSS24.BF2 基准字号的 1 倍放大
        assertTrue(payload.contains("TEXT 8,120,\"TSS24.BF2\",0,1,1,\"网关主板\""));
        assertTrue(payload.trim().endsWith("PRINT 1,1"));
    }

    @Test
    void tspl_textSizeScalesFontMagnification() {
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":0,\"y\":0,\"size\":48},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        String payload = renderService.render(template("TSPL", content), fields());
        // 48/24 = 2 倍放大
        assertTrue(payload.contains("\"TSS24.BF2\",0,2,2"), payload);
    }

    @Test
    void tspl_escapesQuotesInValues() {
        Map<String, String> f = fields();
        f.put("materialName", "含\"引号\"物料");
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":0,\"y\":0},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        String payload = renderService.render(template("TSPL", content), f);
        assertTrue(payload.contains("\\\"引号\\\""), "双引号应转义: " + payload);
    }

    // ==================== ZPL ====================

    @Test
    void zpl_rendersHeaderAndAllItemTypes() {
        String content =
                """
                {"items":[
                  {"type":"qrcode","field":"snCode","x":8,"y":8,"size":5},
                  {"type":"barcode","field":"snCode","x":8,"y":60,"height":48},
                  {"type":"text","field":"materialName","x":8,"y":120,"size":24}
                ]}""";
        String payload = renderService.render(template("ZPL", content), fields());

        assertTrue(payload.startsWith("^XA"));
        assertTrue(payload.contains("^CI28"));
        // 标签宽度为六十毫米，分辨率为每毫米八点，对应四百八十个打印点。
        assertTrue(payload.contains("^PW480"));
        assertTrue(payload.contains("^LL320"));
        // 坐标 8mm * 8 = 64 dots
        assertTrue(payload.contains("^FO64,64^BQN,2,5^FDQA,SN-FC-0001^FS"));
        assertTrue(payload.contains("^FO64,480^BY2,3,"));
        assertTrue(payload.contains("^FD网关主板^FS"));
        assertTrue(payload.trim().endsWith("^XZ"));
    }

    // ==================== ZPL 指令注入防护(MES-LABEL-01) ====================

    @Test
    void zpl_stripsCaretInjectionInValues() {
        Map<String, String> f = fields();
        // 物料名被注入 ZPL 指令: 修复前会提前 ^XZ 截断标签并打印 SCRAP
        f.put("materialName", "X^FS^FO0,0^A0N,60,60^FDSCRAP^FS^XZ");
        // 打印项自身坐标取 5,5(→^FO40,40), 便于区分注入的 ^FO0,0
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":5,\"y\":5},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        String payload = renderService.render(template("ZPL", content), f);

        assertEquals(2, countOccurrences(payload, "^FS"), "文字和 SN 二维码各有一个结束符，不允许注入增加: " + payload);
        assertEquals(1, countOccurrences(payload, "^XZ"), "标签只应有一个 ^XZ(末尾), 未被注入提前截断: " + payload);
        assertFalse(payload.contains("^FO0,0"), "不得出现注入的定位指令: " + payload);
        assertFalse(payload.contains("^A0N,60,60"), "不得出现注入的字体指令: " + payload);
    }

    @Test
    void zpl_stripsTildeResetInjectionInBarcodeAndQrcode() {
        Map<String, String> f = fields();
        f.put("snCode", "SN~JR~HS^XA");
        String content =
                """
                {"items":[
                  {"type":"barcode","field":"snCode","x":0,"y":0},
                  {"type":"qrcode","field":"snCode","x":0,"y":0}
                ]}""";
        String payload = renderService.render(template("ZPL", content), f);

        assertFalse(payload.contains("~"), "不得残留 ~ 指令前缀(如 ~JR 复位): " + payload);
        assertEquals(1, countOccurrences(payload, "^XA"), payload);
        assertTrue(payload.contains("^FDSNJRHSXA^FS"), payload);
    }

    @Test
    void zpl_stripsNewlinesInValues() {
        Map<String, String> f = fields();
        f.put("materialName", "第一行\r\n^FS^XA第二行");
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":0,\"y\":0},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        String payload = renderService.render(template("ZPL", content), f);

        assertEquals(1, countOccurrences(payload, "^XA"), "换行不得拆出新的指令行: " + payload);
        assertTrue(payload.contains("^FD第一行FSXA第二行^FS"), payload);
    }

    @Test
    void tspl_stripsNewlinesInValues() {
        Map<String, String> f = fields();
        f.put("materialName", "行一\r\nPRINT 9,9");
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"materialName\",\"x\":0,\"y\":0},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        String payload = renderService.render(template("TSPL", content), f);

        // 换行被剥离后整串仍在同一字面量内(修复前会被拆成两条指令)
        assertTrue(payload.contains("\"行一PRINT 9,9\""), payload);
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = text.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    // ==================== 公共行为 ====================

    @Test
    void missingFieldRendersEmptyString() {
        String content =
                "{\"items\":[{\"type\":\"text\",\"field\":\"cardNo\",\"x\":0,\"y\":0},{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8}]}";
        Map<String, String> empty = new HashMap<>();
        empty.put("snCode", "SN-FC-0001");
        String payload = renderService.render(template("TSPL", content), empty);
        assertTrue(payload.contains(",\"\""), "缺失字段应渲染为空串: " + payload);
    }

    @Test
    void unknownItemTypeThrows() {
        String content = "{\"items\":[{\"type\":\"image\",\"field\":\"snCode\",\"x\":0,\"y\":0}]}";
        ServiceException ex =
                assertThrows(
                        ServiceException.class,
                        () -> renderService.render(template("TSPL", content), fields()));
        assertTrue(ex.getMessage().contains("未知打印项类型"));
    }

    @Test
    void itemWithoutFieldThrows() {
        String content = "{\"items\":[{\"type\":\"text\",\"x\":0,\"y\":0}]}";
        assertThrows(
                ServiceException.class,
                () -> renderService.render(template("TSPL", content), fields()));
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(
                ServiceException.class,
                () -> renderService.render(template("TSPL", "{not-json"), fields()));
    }

    @Test
    void emptyItemsThrows() {
        assertThrows(
                ServiceException.class,
                () -> renderService.render(template("TSPL", "{\"items\":[]}"), fields()));
    }

    @Test
    void charset_tsplGbk_zplUtf8() {
        MesLabelTemplate tspl = template("TSPL", "{\"items\":[]}");
        MesLabelTemplate zpl = template("ZPL", "{\"items\":[]}");
        // charsetOf 不依赖 content
        assertEquals("GBK", renderService.charsetOf(tspl).name());
        assertEquals("UTF-8", renderService.charsetOf(zpl).name());
    }
}
