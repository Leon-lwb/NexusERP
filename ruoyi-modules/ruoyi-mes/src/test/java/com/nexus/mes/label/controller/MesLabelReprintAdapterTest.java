package com.nexus.mes.label.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.vo.LabelPrintTaskVo;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.IMesLabelTemplateService;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.label.service.impl.MesLabelTemplateServiceImpl;
import com.nexus.mes.pro.mapper.ProSnMapper;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 原URL兼容并返回持久化任务ID，所有人工补打都必须经过原任务与原因校验。
 */
class MesLabelReprintAdapterTest {
    LabelPrintTaskVo receipt(String state) {
        var receipt = new LabelPrintTaskVo();
        receipt.setTaskId(101L);
        receipt.setStatus(state);
        return receipt;
    }

    @Test
    void explicitFieldsReachDurableReprintAndReturnUnknownTruthfully() throws Exception {
        IMesLabelTemplateService service = mock(IMesLabelTemplateService.class);
        when(service.reprintTask(1L, 5L, 90L, "污损", "request-1")).thenReturn(receipt("UNKNOWN"));
        var mvc = MockMvcBuilders.standaloneSetup(new MesLabelTemplateController(service)).build();
        mvc.perform(
                        post("/mes/label/template/reprint")
                                .param("snId", "1")
                                .param("templateId", "5")
                                .param("originalPrintLogId", "90")
                                .param("reprintReason", "污损")
                                .param("requestId", "request-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(101))
                .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.msg").value("打印状态未知，请核验原任务，禁止直接重发"));
        verify(service).reprintTask(1L, 5L, 90L, "污损", "request-1");
    }

    @Test
    void missingOriginalOrReasonCannotFallBackToLegacyPrint() {
        LabelPrintService printer = mock(LabelPrintService.class);
        var service =
                new MesLabelTemplateServiceImpl(
                        mock(MesLabelTemplateMapper.class),
                        new LabelRenderService(mock(SerialIdentityPort.class)),
                        printer,
                        mock(ProSnMapper.class));
        var controller = new MesLabelTemplateController(service);
        assertThrows(ServiceException.class, () -> controller.reprint(1L, 5L));
        assertThrows(
                ServiceException.class, () -> controller.reprint(1L, 5L, "REPRINT", null, null));
        assertThrows(ServiceException.class, () -> controller.reprint(1L, 5L, "REPRINT", 90L, " "));
        assertThrows(ServiceException.class, () -> controller.reprint(1L, 5L, "AUTO", 90L, "污损"));
        verifyNoInteractions(printer);
    }

    @Test
    void normalTestPrintReturnsDurablePendingTaskAndStableRequestId() throws Exception {
        var service = mock(IMesLabelTemplateService.class);
        when(service.testPrintTask(5L, "request-2")).thenReturn(receipt("PENDING"));
        var mvc = MockMvcBuilders.standaloneSetup(new MesLabelTemplateController(service)).build();
        mvc.perform(post("/mes/label/template/5/test-print").param("requestId", "request-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").value(101));
    }

    @Test
    void taskStatusQueryRetainsPrintPermission() throws Exception {
        var method = MesLabelTemplateController.class.getMethod("printTask", Long.class);
        assertArrayEquals(
                new String[] {"mes:label:template:print"},
                method.getAnnotation(SaCheckPermission.class).value());
    }
}
