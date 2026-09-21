package com.nexus.mes.label.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MesLabelTemplateServiceImpl 单元测试 — MES-LABEL-03 保存即校验 + MES-LABEL-02 补打入口
 * <p>覆盖: 非法 JSON / 打印项缺 field / 未知打印项类型在保存时即被拒绝(不再落库成脏模板)、
 * 合法内容可保存、补打按 SN 渲染并留痕(print_source=REPRINT)、SN 不存在时报错。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class MesLabelTemplateServiceImplTest {

    private static final String VALID_CONTENT =
        "{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\",\"x\":8,\"y\":8,\"size\":5}]}";

    @Mock
    private MesLabelTemplateMapper baseMapper;
    @Mock
    private LabelPrintService printService;
    @Mock
    private ProSnMapper proSnMapper;

    /** 真实渲染服务(纯函数): 保存校验与补打渲染都走真实实现 */
    private final LabelRenderService renderService = new LabelRenderService();

    private MesLabelTemplateServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // MapstructUtils 的静态初始化依赖 Spring 上下文, 纯单测须先预热
        SpringStaticBootstrap.ensureActive();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MesLabelTemplate.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(MesLabelTemplate.class);
        TableInfoHelper.remove(ProSn.class);
    }

    @BeforeEach
    void setUp() {
        service = new MesLabelTemplateServiceImpl(baseMapper, renderService, printService, proSnMapper);
    }

    private MesLabelTemplateBo bo(String content) {
        MesLabelTemplateBo bo = new MesLabelTemplateBo();
        bo.setTemplateCode("TPL-1");
        bo.setTemplateName("SN 标签");
        bo.setLabelLang("TSPL");
        bo.setContent(content);
        bo.setPrinterHost("192.168.1.50");
        bo.setPrinterPort(9100);
        return bo;
    }

    // ==================== MES-LABEL-03 保存即校验 ====================

    @Test
    void insertByBo_rejectsMalformedJsonContent() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo("{not-json")));

        assertTrue(ex.getMessage().contains("合法的 JSON"), ex.getMessage());
        verify(baseMapper, never()).insert(any(MesLabelTemplate.class));
    }

    @Test
    void insertByBo_rejectsItemWithoutFieldBinding() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertByBo(bo("{\"items\":[{\"type\":\"text\",\"x\":0,\"y\":0}]}")));

        assertTrue(ex.getMessage().contains("field"), ex.getMessage());
        verify(baseMapper, never()).insert(any(MesLabelTemplate.class));
    }

    @Test
    void insertByBo_rejectsUnknownItemType() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.insertByBo(bo("{\"items\":[{\"type\":\"image\",\"field\":\"snCode\",\"x\":0,\"y\":0}]}")));

        assertTrue(ex.getMessage().contains("未知打印项类型"), ex.getMessage());
        verify(baseMapper, never()).insert(any(MesLabelTemplate.class));
    }

    @Test
    void insertByBo_rejectsEmptyItems() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo("{\"items\":[]}")));

        assertTrue(ex.getMessage().contains("未配置任何打印项"), ex.getMessage());
        verify(baseMapper, never()).insert(any(MesLabelTemplate.class));
    }

    @Test
    void insertByBo_acceptsValidContent() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(MesLabelTemplate.class))).thenReturn(1);

        assertTrue(service.insertByBo(bo(VALID_CONTENT)));

        verify(baseMapper).insert(any(MesLabelTemplate.class));
    }

    @Test
    void updateByBo_rejectsMalformedJsonContent() {
        MesLabelTemplateBo bo = bo("{not-json");
        bo.setTemplateId(5L);

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        verify(baseMapper, never()).updateById(any(MesLabelTemplate.class));
    }

    @Test
    void updateByBo_rejectsBlankContent() {
        MesLabelTemplateBo bo = bo(" ");
        bo.setTemplateId(5L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("模板内容为空"), ex.getMessage());
    }

    // ==================== MES-LABEL-02 补打 ====================

    @Test
    void reprint_rendersSnFieldsAndSendsWithReprintSource() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-FC-0001");
        when(proSnMapper.selectById(1L)).thenReturn(sn);
        MesLabelTemplate template = template(VALID_CONTENT);
        when(baseMapper.selectById(5L)).thenReturn(template);
        MesLabelPrintLog printLog = MesLabelPrintLog.of(1L, "SN-FC-0001", 5L, "TPL-1", "192.168.1.50", 9100,
            MesLabelPrintLog.SOURCE_REPRINT);
        when(printService.newLog(any(), any(), any(MesLabelTemplate.class), anyString())).thenReturn(printLog);
        when(printService.send(any(MesLabelPrintLog.class), anyString(), any(Charset.class))).thenReturn(true);

        assertTrue(service.reprint(1L, 5L));

        verify(printService).newLog(eq(1L), eq("SN-FC-0001"), eq(template), eq(MesLabelPrintLog.SOURCE_REPRINT));
        verify(printService).send(eq(printLog), anyString(), eq(Charset.forName("GBK")));
    }

    @Test
    void reprint_throwsWhenSnMissing() {
        when(proSnMapper.selectById(99L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reprint(99L, 5L));
        assertTrue(ex.getMessage().contains("SN不存在"), ex.getMessage());
        verify(printService, never()).send(any(), anyString(), any());
    }

    @Test
    void testPrint_persistsTestSourceLog() {
        MesLabelTemplate template = template(VALID_CONTENT);
        when(baseMapper.selectById(5L)).thenReturn(template);
        MesLabelPrintLog printLog = MesLabelPrintLog.of(null, null, 5L, "TPL-1", "192.168.1.50", 9100,
            MesLabelPrintLog.SOURCE_TEST);
        when(printService.newLog(any(), any(), any(MesLabelTemplate.class), anyString())).thenReturn(printLog);
        when(printService.send(any(MesLabelPrintLog.class), anyString(), any(Charset.class))).thenReturn(true);

        assertTrue(service.testPrint(5L));

        verify(printService).newLog(null, null, template, MesLabelPrintLog.SOURCE_TEST);
    }

    // ==================== 辅助 ====================

    private MesLabelTemplate template(String content) {
        MesLabelTemplate template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateCode("TPL-1");
        template.setLabelType("SN");
        template.setLabelLang("TSPL");
        template.setWidthMm(60);
        template.setHeightMm(40);
        template.setPrinterHost("192.168.1.50");
        template.setPrinterPort(9100);
        template.setContent(content);
        template.setEnabled("Y");
        return template;
    }

    @Test
    void preview_returnsRenderedPayload() {
        when(baseMapper.selectById(5L)).thenReturn(template(VALID_CONTENT));

        java.util.Map<String, Object> data = service.preview(5L);

        assertEquals("GBK", data.get("charset"));
        assertTrue(data.get("payload").toString().contains("SN-FC-TEST-0001"), data.toString());
    }
}
