package com.nexus.mes.label.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SerialIdentity;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.listener.SnLabelPrintEventListener;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.impl.MesLabelTemplateServiceImpl;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实际模板、SN事件、持久化门面及补打链路中的冻结证据回归。
 */
class LabelFrozenWorkflowTest extends LabelDurableTestSupport {
    private final MesLabelTemplateMapper templates = mock(MesLabelTemplateMapper.class);
    private final ProSnMapper sns = mock(ProSnMapper.class);
    private final SerialIdentityPort identities = mock(SerialIdentityPort.class);
    private final LabelRenderService renderer = new LabelRenderService(identities);

    @BeforeAll
    static void tables() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builder, MesLabelTemplate.class);
    }

    MesLabelTemplateServiceImpl workflow() {
        return new MesLabelTemplateServiceImpl(templates, renderer, service, sns);
    }

    MesLabelTemplate template() {
        var template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateCode("TPL");
        template.setTemplateRevision(7L);
        template.setLabelType("SN");
        template.setLabelLang("TSPL");
        template.setPrinterHost("synthetic");
        template.setPrinterPort(9100);
        template.setContent("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}");
        return template;
    }

    @BeforeEach
    void canonicalIdentity() {
        when(identities.find("000001", 1001L))
                .thenReturn(new SerialIdentity(1001L, "SN-1", 100L, null, null));
    }

    ProSn sn() {
        var sn = new ProSn();
        sn.setTenantId("000001");
        sn.setSnId(1L);
        sn.setIdentityId(1001L);
        sn.setMaterialId(100L);
        sn.setSnCode("SN-1");
        sn.setMaterialName("原物料");
        return sn;
    }

    Long automatic(MesLabelTemplate template) {
        when(templates.selectList(any())).thenReturn(List.of(template));
        when(sns.selectById(1L)).thenReturn(sn());
        DynamicDataSourceContextHolder.push("mes");
        try {
            business.executeWithoutResult(
                    tx ->
                            new SnLabelPrintEventListener(templates, sns, renderer, service)
                                    .onSnCreated(new SnCreatedEvent(1L, "SN-1", null, 100L)));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        return onlyTask().getTaskId();
    }

    @Test
    void testPrintPersistsRevisionFieldsAndActualEncodingHashes() {
        when(templates.selectById(5L)).thenReturn(template());
        var receipt = workflow().testPrintTask(5L, "sample");
        var frozen = store.record(store.find(receipt.getTaskId()));
        assertEquals("SENT", receipt.getStatus());
        assertEquals(7L, frozen.getTemplateRevision());
        assertTrue(frozen.getFieldSnapshot().contains("SN-FC-TEST-0001"));
        assertTrue(frozen.getRenderedPayload().contains("SN-FC-TEST-0001"));
        assertEquals(
                LabelPrintSnapshot.hash(
                        frozen.getRenderedPayload().getBytes(Charset.forName("GBK"))),
                frozen.getPayloadHash());
        assertEquals(
                LabelPrintSnapshot.hash(
                        frozen.getTemplateSnapshot().getBytes(StandardCharsets.UTF_8)),
                frozen.getContentHash());
        assertEquals("FROZEN", frozen.getSnapshotMode());
    }

    @Test
    void explicitReprintUsesOriginalFrozenContentAndAppendsTaskAndAudit() {
        var template = template();
        Long originalId = automatic(template);
        dispatcher.pollTenant();
        var original = store.findRecord(originalId);
        template.setContent("{not-json");
        clearInvocations(templates);
        clearInvocations(sns);
        var receipt = workflow().reprintTask(1L, 5L, originalId, "标签污损", "manual-reprint");
        var reprint = store.findRecord(receipt.getTaskId());
        assertEquals("SENT", receipt.getStatus());
        assertEquals(original.getRenderedPayload(), reprint.getRenderedPayload());
        assertEquals(original.getFieldSnapshot(), reprint.getFieldSnapshot());
        assertEquals(original.getContentHash(), reprint.getContentHash());
        assertEquals(originalId, reprint.getOriginalPrintLogId());
        assertEquals("标签污损", reprint.getReprintReason());
        assertEquals(1, reprint.getRetryCount());
        assertEquals(0, store.findRecord(originalId).getRetryCount());
        assertEquals(2, sends.get());
        assertEquals(2, count("mes_label_print_log"));
        verifyNoInteractions(templates, sns);
    }

    @Test
    void legacySnapshotFallbackIsAnnotatedAndOriginalIsNotRewritten() {
        jdbc.update(
                "INSERT INTO"
                    + " mes_label_print_log(log_id,tenant_id,sn_id,sn_code,template_id,result,print_source)"
                    + " VALUES(90,'000001',1,'SN-1',5,'SUCCESS','AUTO')");
        when(templates.selectById(5L)).thenReturn(template());
        when(sns.selectById(1L)).thenReturn(sn());
        var receipt = workflow().reprintTask(1L, 5L, 90L, "历史标签污损", "legacy-reprint");
        var frozen = store.findRecord(receipt.getTaskId());
        assertEquals("LEGACY_RECORD", frozen.getSnapshotMode());
        assertEquals(90L, frozen.getOriginalPrintLogId());
        assertTrue(frozen.getRemark().contains("当前"));
        assertNull(
                jdbc.queryForObject(
                        "SELECT rendered_payload FROM mes_label_print_log WHERE log_id=90",
                        String.class));
    }

    @Test
    void legacySuccessIsSuppressedWithoutInventingCurrentTemplateAsHistory() {
        jdbc.update(
                "INSERT INTO"
                    + " mes_label_print_log(log_id,tenant_id,sn_id,sn_code,template_id,result,print_source)"
                    + " VALUES(90,'000001',1,'SN-1',5,'SUCCESS','AUTO')");
        automatic(template());
        dispatcher.pollTenant();
        assertEquals(0, sends.get());
        assertEquals("LEGACY_SENT", onlyTask().getStatus());
        var original = store.record(onlyTask());
        assertNull(original.getTemplateSnapshot());
        assertNull(original.getRenderedPayload());
        assertEquals(90L, original.getOriginalPrintLogId());
        assertEquals(1, count("mes_label_print_log"));
    }

    @Test
    void renderFailureIsDurableAndCannotBeRebuiltAsAnotherTemplate() {
        var template = template();
        template.setContent("{not-json");
        Long id = automatic(template);
        assertEquals("FAIL", onlyTask().getStatus());
        assertEquals(0, sends.get());
        assertNotNull(store.findRecord(id).getFieldSnapshot());
        assertNull(store.findRecord(id).getRenderedPayload());
        when(templates.selectById(5L)).thenReturn(template());
        assertThrows(
                ServiceException.class,
                () -> workflow().reprintTask(1L, 5L, id, "模板修正", "bad-original"));
        assertEquals(1, count("mes_label_print_task"));
    }

    @Test
    void reprintRequiresOriginalReasonAndTerminalOriginalState() {
        assertThrows(ServiceException.class, () -> workflow().reprint(1L, 5L));
        assertThrows(
                ServiceException.class, () -> workflow().reprintTask(1L, 5L, null, "污损", null));
        assertThrows(ServiceException.class, () -> workflow().reprintTask(1L, 5L, 90L, " ", null));
        Long pending = automatic(template());
        assertThrows(
                ServiceException.class, () -> workflow().reprintTask(1L, 5L, pending, "污损", null));
        assertEquals(0, sends.get());
    }

    @Test
    void samplePrintCannotUseOnlyMaterialMachineCode() {
        var template = template();
        template.setContent("{\"items\":[{\"type\":\"qrcode\",\"field\":\"materialCode\"}]}");
        when(templates.selectById(5L)).thenReturn(template);
        assertThrows(ServiceException.class, () -> workflow().testPrintTask(5L, "invalid"));
        assertEquals(0, count("mes_label_print_task"));
    }
}
