package com.nexus.mes.label.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 经过真实事件、渲染及任务存储验证正式身份校准，旧冻结记录始终保留原文字节。
 */
class LabelCanonicalIdentityWorkflowTest extends LabelDurableTestSupport {
    private static final long IDENTITY_ID = 9007199254740993L;
    private static final String LOCAL_CODE = "MES-LOCAL-OLD";
    private static final String FORMAL_CODE = "ERP-FORMAL-0001";
    private final SerialIdentityPort identities = mock(SerialIdentityPort.class);
    private final MesLabelTemplateMapper templates = mock(MesLabelTemplateMapper.class);
    private final ProSnMapper sns = mock(ProSnMapper.class);
    private AnnotationConfigApplicationContext context;
    private MesLabelTemplateServiceImpl workflow;
    private ProSn sn;
    private MesLabelTemplate template;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesLabelTemplate.class);
    }

    @BeforeEach
    void canonicalFixture() {
        sn = new ProSn();
        sn.setTenantId("000001");
        sn.setSnId(1L);
        sn.setIdentityId(IDENTITY_ID);
        sn.setMaterialId(100L);
        sn.setSnCode(LOCAL_CODE);
        sn.setMaterialCode("MAT-100");
        template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateCode("TPL");
        template.setTemplateRevision(7L);
        template.setLabelType("SN");
        template.setLabelLang("TSPL");
        template.setPrinterHost("synthetic");
        template.setPrinterPort(9100);
        template.setContent("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}");
        when(templates.selectList(any())).thenReturn(List.of(template));
        when(templates.selectById(5L)).thenReturn(template);
        when(sns.selectById(1L)).thenReturn(sn);
        when(identities.find("000001", IDENTITY_ID))
                .thenReturn(new SerialIdentity(IDENTITY_ID, FORMAL_CODE, 100L, null, null));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SerialIdentityPort.class, () -> identities);
        context.registerBean(MesLabelTemplateMapper.class, () -> templates);
        context.registerBean(ProSnMapper.class, () -> sns);
        context.registerBean(LabelPrintService.class, () -> service);
        context.registerBean(LabelRenderService.class);
        context.registerBean(SnLabelPrintEventListener.class);
        context.registerBean(MesLabelTemplateServiceImpl.class);
        context.refresh();
        workflow = context.getBean(MesLabelTemplateServiceImpl.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void newAutomaticTaskFreezesErpFormalCodeAndStableIdentityWithoutChangingMesAlias()
            throws Exception {
        automatic();
        var frozen = store.record(onlyTask());
        assertEquals("PENDING", onlyTask().getStatus());
        assertEquals(FORMAL_CODE, frozen.getSnCode());
        assertTrue(frozen.getRenderedPayload().contains(FORMAL_CODE));
        assertFalse(frozen.getRenderedPayload().contains(LOCAL_CODE));
        var fields = new ObjectMapper().readTree(frozen.getFieldSnapshot());
        assertEquals(FORMAL_CODE, fields.get("snCode").asText());
        assertEquals(Long.toString(IDENTITY_ID), fields.get("identityId").textValue());
        assertEquals("100", fields.get("materialId").textValue());
        assertEquals(LOCAL_CODE, sn.getSnCode());
        assertEquals(0, sends.get());
        dispatcher.pollTenant();
        assertEquals(1, sends.get());
        assertEquals("SENT", onlyTask().getStatus());
        verify(identities).find("000001", IDENTITY_ID);
    }

    @Test
    void currentTemplateFallbackUsesVerifiedFormalCodeAndAuditsLegacyOrigin() {
        legacyRecord();
        var receipt = workflow.reprintTask(1L, 5L, 90L, "核实身份后规范化重贴", "canonical-legacy");
        var frozen = store.findRecord(receipt.getTaskId());
        assertEquals("SENT", receipt.getStatus());
        assertEquals(FORMAL_CODE, frozen.getSnCode());
        assertTrue(frozen.getRenderedPayload().contains(FORMAL_CODE));
        assertEquals("LEGACY_RECORD", frozen.getSnapshotMode());
        assertEquals(90L, frozen.getOriginalPrintLogId());
        assertEquals("核实身份后规范化重贴", frozen.getReprintReason());
        assertTrue(frozen.getRemark().contains("不能证明"));
        assertEquals(
                LOCAL_CODE,
                jdbc.queryForObject(
                        "SELECT sn_code FROM mes_label_print_log WHERE log_id=90", String.class));
        assertNull(
                jdbc.queryForObject(
                        "SELECT rendered_payload FROM mes_label_print_log WHERE log_id=90",
                        String.class));
        var repeated = workflow.reprintTask(1L, 5L, 90L, "核实身份后规范化重贴", "canonical-legacy");
        assertEquals(receipt.getTaskId(), repeated.getTaskId());
        assertEquals(1, sends.get());
        assertEquals(1, count("mes_label_print_task"));
    }

    @Test
    void localCodeWithoutMappedIdentityCannotCreateFormalTask() {
        sn.setIdentityId(null);
        assertBothCreationPathsRejected();
        verifyNoInteractions(identities);
    }

    @Test
    void absentErpIdentityCannotCreateFormalTask() {
        when(identities.find("000001", IDENTITY_ID)).thenReturn(null);
        assertBothCreationPathsRejected();
    }

    @Test
    void materialConflictCannotCreateFormalTask() {
        when(identities.find("000001", IDENTITY_ID))
                .thenReturn(new SerialIdentity(IDENTITY_ID, FORMAL_CODE, 200L, null, null));
        assertBothCreationPathsRejected();
    }

    @Test
    void missingLocalMaterialCannotCreateFormalTask() {
        sn.setMaterialId(null);
        assertBothCreationPathsRejected();
        verifyNoInteractions(identities);
    }

    @Test
    void unexpectedStableIdentityCannotCreateFormalTask() {
        when(identities.find("000001", IDENTITY_ID))
                .thenReturn(new SerialIdentity(IDENTITY_ID + 1, FORMAL_CODE, 100L, null, null));
        assertBothCreationPathsRejected();
    }

    @Test
    void blankFormalCodeCannotUseLocalCodeAsProof() {
        when(identities.find("000001", IDENTITY_ID))
                .thenReturn(new SerialIdentity(IDENTITY_ID, " ", 100L, null, null));
        assertBothCreationPathsRejected();
    }

    @Test
    void foreignTenantSnCannotBeCalibratedAgainstCurrentTenant() {
        sn.setTenantId("000002");
        assertBothCreationPathsRejected();
        verifyNoInteractions(identities);
    }

    @Test
    void missingTrustedTenantCannotFallBackToEntityTenant() {
        TenantHelper.clearDynamic();
        assertThrows(ServiceException.class, this::automatic);
        assertEquals(0, count("mes_label_print_task"));
        verifyNoInteractions(identities);
    }

    @Test
    void frozenHistoricalTaskReprintsOriginalBytesWithoutCanonicalLookup() {
        var renderer = context.getBean(LabelRenderService.class);
        var original = service.newLog(1L, LOCAL_CODE, template, MesLabelPrintLog.SOURCE_AUTO);
        var oldFields = Map.of("snCode", LOCAL_CODE);
        service.freezeFields(original, oldFields);
        DynamicDataSourceContextHolder.push("mes");
        try {
            business.executeWithoutResult(
                    tx ->
                            service.submit(
                                    original,
                                    renderer.render(template, oldFields),
                                    renderer.charsetOf(template)));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        var originalId = onlyTask().getTaskId();
        dispatcher.pollTenant();
        String originalRecord = store.find(originalId).getFrozenRecord();
        var frozen = store.findRecord(originalId);
        template.setContent("{invalid-current-template");
        clearInvocations(templates, sns, identities);
        var receipt = workflow.reprintTask(1L, 5L, originalId, "保留原标签内容补打", "frozen-old");
        var reprint = store.findRecord(receipt.getTaskId());
        assertEquals(frozen.getRenderedPayload(), reprint.getRenderedPayload());
        assertEquals(frozen.getFieldSnapshot(), reprint.getFieldSnapshot());
        assertEquals(frozen.getPayloadHash(), reprint.getPayloadHash());
        assertEquals(LOCAL_CODE, reprint.getSnCode());
        assertEquals(originalId, reprint.getOriginalPrintLogId());
        assertEquals("保留原标签内容补打", reprint.getReprintReason());
        assertEquals(originalRecord, store.find(originalId).getFrozenRecord());
        verifyNoInteractions(templates, sns, identities);
    }

    @Test
    void previewAndTestSamplesRemainExplicitSamplesWithoutRegisteredIdentityLookup() {
        assertTrue(workflow.preview(5L).get("payload").toString().contains("SN-FC-TEST-0001"));
        var receipt = workflow.testPrintTask(5L, "sample-only");
        var frozen = store.findRecord(receipt.getTaskId());
        assertEquals(MesLabelPrintLog.SOURCE_TEST, frozen.getPrintSource());
        assertNull(frozen.getSnId());
        assertTrue(frozen.getRenderedPayload().contains("SN-FC-TEST-0001"));
        verifyNoInteractions(identities);
    }

    private void automatic() {
        DynamicDataSourceContextHolder.push("mes");
        try {
            business.executeWithoutResult(
                    tx -> context.publishEvent(new SnCreatedEvent(1L, LOCAL_CODE, null, 100L)));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private void legacyRecord() {
        jdbc.update(
                "INSERT INTO"
                    + " mes_label_print_log(log_id,tenant_id,sn_id,sn_code,template_id,result,print_source)"
                    + " VALUES(90,'000001',1,?,5,'SUCCESS','AUTO')",
                LOCAL_CODE);
    }

    private void assertBothCreationPathsRejected() {
        assertThrows(ServiceException.class, this::automatic);
        legacyRecord();
        assertThrows(
                ServiceException.class,
                () -> workflow.reprintTask(1L, 5L, 90L, "核实后重贴", "blocked-new-content"));
        assertEquals(0, count("mes_label_print_task"));
        assertEquals(1, count("mes_label_print_log"));
        assertEquals(0, sends.get());
    }
}
