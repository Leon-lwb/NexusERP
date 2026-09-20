package com.nexus.mes.label.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.pro.domain.ProSn;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签渲染服务 — 字段映射 JSON → TSPL/ZPL 指令串(纯字符串拼接, 无重依赖)
 * <p>content JSON 结构: {"items":[{"type":"text|barcode|qrcode","field":"snCode","x":10,"y":10,"size":24,"height":48}]}。
 * 坐标单位 mm(TSPL 原生支持; ZPL 按 8 dots/mm 换算 203dpi)。
 * 编码: TSPL 中文点阵字体(TSS24.BF2)按 GBK 发送, ZPL 走 ^CI28 UTF-8。</p>
 *
 * @author Nexus
 */
@Service
public class LabelRenderService {

    /** TSPL 中文点阵字体(TSC 标配), 基准字号 24 dot */
    private static final String TSPL_CN_FONT = "TSS24.BF2";
    private static final int TSPL_CN_FONT_BASE = 24;
    /** ZPL 203dpi 换算: 1mm = 8 dots */
    private static final int ZPL_DOTS_PER_MM = 8;

    /** 标签类型: SN 标签 */
    public static final String TYPE_SN = "SN";
    /** 打印机语言 */
    public static final String LANG_TSPL = "TSPL";
    public static final String LANG_ZPL = "ZPL";

    /** 内容 JSON 解析用独立 mapper(内部 DTO, 不涉 API 序列化; 亦使本服务为纯函数可单测) */
    private static final ObjectMapper CONTENT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 渲染标签指令串
     *
     * @param template 模板(含 content JSON 与宽高)
     * @param fields   字段值域(键为字段名, 值可空)
     * @return 打印指令串(以 \r\n 分行)
     */
    public String render(MesLabelTemplate template, Map<String, String> fields) {
        if (template == null || StringUtils.isBlank(template.getContent())) {
            throw new ServiceException("模板内容为空: " + (template == null ? null : template.getTemplateCode()));
        }
        LabelContent content = parseContent(template.getContent());
        if (content.getItems() == null || content.getItems().isEmpty()) {
            throw new ServiceException("模板未配置任何打印项: " + template.getTemplateCode());
        }
        if (LANG_ZPL.equalsIgnoreCase(template.getLabelLang())) {
            return renderZpl(template, content, fields);
        }
        return renderTspl(template, content, fields);
    }

    /** 模板语言对应的发送字符集: TSPL 中文点阵字体需 GBK, ZPL(^CI28) 用 UTF-8 */
    public Charset charsetOf(MesLabelTemplate template) {
        return LANG_ZPL.equalsIgnoreCase(template.getLabelLang())
            ? StandardCharsets.UTF_8 : Charset.forName("GBK");
    }

    /**
     * SN 六字段值域(与模板字段绑定一一对应): 造码联动打印与人工补打共用, 避免两处字段映射漂移
     *
     * @param sn SN(可空, 空则全部渲染为空串)
     */
    public Map<String, String> snFields(ProSn sn) {
        Map<String, String> fields = new LinkedHashMap<>(8);
        if (sn == null) {
            return fields;
        }
        fields.put("snCode", sn.getSnCode());
        fields.put("materialCode", sn.getMaterialCode());
        fields.put("materialName", sn.getMaterialName());
        fields.put("woNo", sn.getWoNo());
        fields.put("cardNo", sn.getCardNo());
        fields.put("productionDate", sn.getProductionDate() == null ? "" : String.format("%tF", sn.getProductionDate()));
        return fields;
    }

    // ==================== TSPL ====================

    private String renderTspl(MesLabelTemplate t, LabelContent content, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("SIZE ").append(nvl(t.getWidthMm(), 60)).append(" mm,").append(nvl(t.getHeightMm(), 40)).append(" mm\r\n");
        sb.append("GAP 2 mm,0\r\n");
        sb.append("DIRECTION 1,0\r\n");
        sb.append("CLS\r\n");
        for (LabelItem item : content.getItems()) {
            String value = valueOf(item, fields);
            int x = nvl(item.getX(), 0);
            int y = nvl(item.getY(), 0);
            switch (item.typeSafe()) {
                case "text" -> {
                    // 放大倍数按基准字号换算(最小 1), 缺省 24 即 1 倍
                    int size = nvl(item.getSize(), TSPL_CN_FONT_BASE);
                    int mag = Math.max(1, Math.round((float) size / TSPL_CN_FONT_BASE));
                    sb.append("TEXT ").append(x).append(',').append(y)
                        .append(",\"").append(TSPL_CN_FONT).append("\",0,").append(mag).append(',').append(mag)
                        .append(",\"").append(escapeTspl(value)).append("\"\r\n");
                }
                case "barcode" -> sb.append("BARCODE ").append(x).append(',').append(y)
                    .append(",\"128\",").append(nvl(item.getHeight(), 48))
                    .append(",0,0,2,2,\"").append(escapeTspl(value)).append("\"\r\n");
                case "qrcode" -> sb.append("QRCODE ").append(x).append(',').append(y)
                    .append(",H,").append(nvl(item.getSize(), 4)).append(",A,0,M2,S7,\"")
                    .append(escapeTspl(value)).append("\"\r\n");
                default -> throw new ServiceException("未知打印项类型: " + item.getType() + "(仅支持 text/barcode/qrcode)");
            }
        }
        sb.append("PRINT 1,1\r\n");
        return sb.toString();
    }

    // ==================== ZPL ====================

    private String renderZpl(MesLabelTemplate t, LabelContent content, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("^XA\r\n");
        sb.append("^CI28\r\n");
        sb.append("^PW").append(nvl(t.getWidthMm(), 60) * ZPL_DOTS_PER_MM).append("\r\n");
        sb.append("^LL").append(nvl(t.getHeightMm(), 40) * ZPL_DOTS_PER_MM).append("\r\n");
        for (LabelItem item : content.getItems()) {
            String value = valueOf(item, fields);
            // MES-LABEL-01: ZPL 字段值必须转义 —— ^/~ 是标签指令前缀, 物料名/工单号等自由文本可直接注入
            // (如 "^FS^FO0,0^A0N,60,60^FDSCRAP^FS^XZ" 可截断标签、改版式、打印任意内容)
            String safe = escapeZpl(value);
            int x = nvl(item.getX(), 0) * ZPL_DOTS_PER_MM;
            int y = nvl(item.getY(), 0) * ZPL_DOTS_PER_MM;
            switch (item.typeSafe()) {
                case "text" -> {
                    int size = nvl(item.getSize(), 24) * ZPL_DOTS_PER_MM / 4;
                    sb.append("^FO").append(x).append(',').append(y)
                        .append("^A0N,").append(size).append(',').append(Math.max(1, size * 4 / 5))
                        .append("^FD").append(safe).append("^FS\r\n");
                }
                case "barcode" -> {
                    int height = nvl(item.getHeight(), 48) * ZPL_DOTS_PER_MM / 4;
                    sb.append("^FO").append(x).append(',').append(y)
                        .append("^BY2,3,").append(height)
                        .append("^BCN,").append(height).append(",Y,N,N^FD").append(safe).append("^FS\r\n");
                }
                case "qrcode" -> sb.append("^FO").append(x).append(',').append(y)
                    .append("^BQN,2,").append(nvl(item.getSize(), 4))
                    .append("^FDQA,").append(safe).append("^FS\r\n");
                default -> throw new ServiceException("未知打印项类型: " + item.getType() + "(仅支持 text/barcode/qrcode)");
            }
        }
        sb.append("^XZ\r\n");
        return sb.toString();
    }

    // ==================== 辅助 ====================

    /** 解析并校验 content JSON */
    public LabelContent parseContent(String content) {
        try {
            LabelContent parsed = CONTENT_MAPPER.readValue(content, LabelContent.class);
            return parsed != null ? parsed : new LabelContent();
        } catch (Exception e) {
            throw new ServiceException("模板内容不是合法的 JSON: " + e.getMessage());
        }
    }

    private String valueOf(LabelItem item, Map<String, String> fields) {
        if (StringUtils.isBlank(item.getField())) {
            throw new ServiceException("打印项缺少字段绑定(field)");
        }
        String value = fields.get(item.getField());
        return value == null ? "" : value;
    }

    /** TSPL 字符串字面量转义(双引号与反斜杠; 并剥离 CR/LF 等控制字符, 否则换行会把一条指令拆成两条) */
    private String escapeTspl(String value) {
        return sanitize(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * ZPL 字段值转义(MES-LABEL-01): 剥离指令前缀 {@code ^} 与 {@code ~} 及 CR/LF 等控制字符,
     * 消除"物料名/工单号含 ^FS 等指令"导致的标签指令注入(截断、改版式、~JR 复位)。
     */
    private String escapeZpl(String value) {
        return sanitize(value).replace("^", "").replace("~", "");
    }

    /** 通用清洗: 去除 CR/LF/TAB 及其余 C0 控制字符与 DEL(标签指令按行解析, 换行即注入新的指令行) */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    /** 字段映射布局 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LabelContent {
        /** 打印项列表 */
        private List<LabelItem> items;
    }

    /** 单个打印项 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LabelItem {
        /** 类型: text / barcode / qrcode */
        private String type;
        /** 绑定字段: snCode/materialCode/materialName/woNo/cardNo/productionDate */
        private String field;
        /** X 坐标(mm) */
        private Integer x;
        /** Y 坐标(mm) */
        private Integer y;
        /** text 字号 / qrcode 单元格 */
        private Integer size;
        /** barcode 高度 */
        private Integer height;

        public String typeSafe() {
            return type == null ? "" : type.trim().toLowerCase();
        }
    }
}
