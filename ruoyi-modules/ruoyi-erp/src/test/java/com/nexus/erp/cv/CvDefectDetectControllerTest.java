package com.nexus.erp.cv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.DetectDtos.DetectionItem;
import com.nexus.common.cv.domain.DetectDtos.OcrItem;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.erp.cv.controller.CvDetectController;
import com.nexus.erp.cv.service.CvLabelParseService;
import com.nexus.erp.cv.service.CvOpsService;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real Spring multipart binding and controller behavior, isolated from Vision and databases. */
class CvDefectDetectControllerTest {
    private static final byte[] IMAGE = "image-bytes".getBytes(StandardCharsets.UTF_8);
    private MockMvc mvc;
    private final List<Object[]> calls = new ArrayList<>();
    private ProductDetectResult inference;
    private VisionException upstreamFailure;
    private CvLabelParseService labels;

    @BeforeEach
    void setUp() {
        calls.clear();
        upstreamFailure = null;
        DetectionItem item = new DetectionItem();
        item.setClassName("scratches");
        item.setConfidence(0.92);
        item.setBbox(List.of(120.0, 40.0, 360.0, 180.0));
        OcrItem ocr = new OcrItem();
        ocr.setText("MAT-001");
        inference = new ProductDetectResult();
        inference.setSuccess(true);
        inference.setEffectiveScenario("defect_metal");
        inference.setCount(1);
        inference.setDetections(List.of(item));
        inference.setProcessTimeMs(23.75);
        inference.setOcrResults(List.of(ocr));
        VisionClient client = mock(VisionClient.class, invocation -> {
            if (invocation.getMethod().getName().equals("detectProduct")) {
                calls.add(invocation.getArguments());
                if (upstreamFailure != null) throw upstreamFailure;
                return inference;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        labels = mock(CvLabelParseService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CvDetectController(client, labels, mock(CvOpsService.class))).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"defect_metal", "defect_pcb"})
    void onlySelectedIndustrialModelIsCalledWithOcrDisabled(String scenario) throws Exception {
        inference.setEffectiveScenario(scenario);
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", scenario))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.scenario").value(scenario))
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.count").value(1))
            .andExpect(jsonPath("$.data.processTimeMs").value(23.75))
            .andExpect(jsonPath("$.data.detections[0].className").value("scratches"))
            .andExpect(jsonPath("$.data.detections[0].bbox[2]").value(360.0))
            .andExpect(jsonPath("$.data.ocrResults").doesNotExist());
        assertEquals(1, calls.size());
        assertArrayEquals(IMAGE, (byte[]) calls.get(0)[0]);
        assertEquals(false, calls.get(0)[1]);
        assertEquals(scenario, calls.get(0)[2]);
        verifyNoInteractions(labels);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "general", "defect_other", "DEFECT_PCB", "defect_pcb "})
    void invalidScenarioCannotFallBackToGeneral(String scenario) throws Exception {
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", scenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("场景")));
        assertTrue(calls.isEmpty());
    }

    @Test
    void scenarioIsRequired() throws Exception {
        mvc.perform(multipart("/cv/detect/defect").file(image())).andExpect(status().isBadRequest());
        assertTrue(calls.isEmpty());
    }

    @Test
    void multipartFieldMustRemainFile() throws Exception {
        mvc.perform(multipart("/cv/detect/defect").file(new MockMultipartFile("image", IMAGE)).param("scenario", "defect_pcb"))
            .andExpect(status().isBadRequest());
        assertTrue(calls.isEmpty());
    }

    @Test
    void emptyFileNeverCallsVision() throws Exception {
        mvc.perform(multipart("/cv/detect/defect").file(new MockMultipartFile("file", new byte[0])).param("scenario", "defect_pcb"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        assertTrue(calls.isEmpty());
    }

    @Test
    void keepsExistingDetectionPermission() throws Exception {
        var endpoint = assertDoesNotThrow(() -> CvDetectController.class.getMethod("detectDefect", MultipartFile.class, String.class),
            "Dedicated defect endpoint must exist");
        assertArrayEquals(new String[]{"cv:detect:query"}, endpoint.getAnnotation(SaCheckPermission.class).value());
    }

    @Test
    void modelErrorsAreFailuresWithReason() throws Exception {
        upstreamFailure = new VisionException("所选缺陷模型不可用", 503, "HTTP_503");
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", "defect_metal"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("所选缺陷模型不可用"));
    }

    @Test
    void unsuccessfulModelResponseCannotLookLikeZeroDefects() throws Exception {
        inference.setSuccess(false);
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", "defect_metal"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"general", "defect_metal"})
    void missingOrMismatchedAcknowledgementCannotBecomeIndustrialSuccess(String effective) throws Exception {
        inference.setEffectiveScenario(effective);
        inference.setCount(0);
        inference.setDetections(List.of());
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", "defect_pcb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("场景")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void successfulEmptyInferenceRemainsValid() throws Exception {
        inference.setEffectiveScenario("defect_pcb");
        inference.setCount(0);
        inference.setDetections(List.of());
        mvc.perform(multipart("/cv/detect/defect").file(image()).param("scenario", "defect_pcb"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void existingOcrDefaultsAndOptionalLabelParsingStayCompatible() throws Exception {
        mvc.perform(multipart("/cv/detect/product").file(image()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ocrResults[0].text").value("MAT-001"));
        assertEquals(2, calls.get(0).length, "Existing endpoint keeps the legacy client call");
        assertEquals(true, calls.get(0)[1]);
        when(labels.parse(anyList())).thenReturn(Map.of("matched", true));
        mvc.perform(multipart("/cv/detect/product").file(image()).param("enableOcr", "false").param("enableLabelParse", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.label.matched").value(true));
        assertEquals(false, calls.get(1)[1]);
        verify(labels).parse(inference.getOcrResults());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "surface.png", "image/png", IMAGE);
    }
}
