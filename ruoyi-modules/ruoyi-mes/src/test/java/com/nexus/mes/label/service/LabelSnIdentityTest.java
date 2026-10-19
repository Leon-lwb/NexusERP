package com.nexus.mes.label.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.MesLabelTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SN 标签必须能以机器码读回本件正式 SN，物料码不能替代单件身份。
 */
class LabelSnIdentityTest {

    private final LabelRenderService renderService = new LabelRenderService(mock(SerialIdentityPort.class));

    @Test
    void render_rejectsMaterialBarcodeEvenWhenSnAppearsAsText() {
        MesLabelTemplate template = template("{\"items\":[{\"type\":\"text\",\"field\":\"snCode\"},"
            + "{\"type\":\"barcode\",\"field\":\"materialCode\"}]}");
        ServiceException error = assertThrows(ServiceException.class,
            () -> renderService.render(template, Map.of("snCode", "SN-1", "materialCode", "MAT-1")));
        assertTrue(error.getMessage().contains("snCode"));
    }

    @Test
    void render_rejectsEmptySnInMachineReadableItem() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> renderService.render(template("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}"),
                Map.of("snCode", " ")));
        assertTrue(error.getMessage().contains("SN"));
    }

    @Test
    void render_acceptsBarcodeAndQrcodeBoundToSn() {
        for (String kind : new String[]{"barcode", "qrcode"}) {
            String payload = renderService.render(template("{\"items\":[{\"type\":\"" + kind
                + "\",\"field\":\"snCode\"}]}"), Map.of("snCode", "SN-1"));
            assertTrue(payload.contains("SN-1"));
        }
    }

    private MesLabelTemplate template(String content) {
        MesLabelTemplate template = new MesLabelTemplate();
        template.setTemplateCode("TPL-SN");
        template.setLabelType("SN");
        template.setLabelLang("TSPL");
        template.setContent(content);
        return template;
    }
}
