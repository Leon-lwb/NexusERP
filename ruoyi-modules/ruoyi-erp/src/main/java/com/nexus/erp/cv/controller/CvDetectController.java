package com.nexus.erp.cv.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.DetectDtos.MaterialDetectResult;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.erp.cv.service.CvLabelParseService;
import com.nexus.erp.cv.service.CvOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 视觉识别检测控制器(物料 OCR + 操作台人脸)
 * <p>端点与方案文档 3.3 对应:/cv/detect/product(物料 OCR)、/cv/detect/material(YOLO 兜底)、
 * /cv/ops/identify(操作台 1:N top-3)。三端统一经 ERP 代理,不直连 vision 服务。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/cv")
public class CvDetectController {

    private final VisionClient visionClient;
    private final CvLabelParseService labelParseService;
    private final CvOpsService opsService;

    /**
     * 物料成品检测 + OCR(物料识别主路径)
     * <p>返回 YOLO 检测框 + OCR 文本;enableLabelParse=true 时追加标签规则解析
     * (料号/批次/SN 提取 + 物料主数据交叉校验,见 {@link CvLabelParseService})。</p>
     */
    @PostMapping("/detect/product")
    public R<Map<String, Object>> detectProduct(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "enableOcr", defaultValue = "true") boolean enableOcr,
                                                @RequestParam(value = "enableLabelParse", defaultValue = "false") boolean enableLabelParse) {
        try {
            ProductDetectResult result = visionClient.detectProduct(file.getBytes(), enableOcr);
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("success", result.isSuccess());
            data.put("count", result.getCount());
            data.put("detections", result.getDetections());
            data.put("ocrResults", result.getOcrResults());
            data.put("processTimeMs", result.getProcessTimeMs());
            if (enableLabelParse && result.getOcrResults() != null) {
                data.put("label", labelParseService.parse(result.getOcrResults()));
            }
            return R.ok(data);
        } catch (IOException e) {
            return R.fail("读取图片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * YOLO 通用检测(兜底,COCO 80 类;当前不含工厂料件类别)
     */
    @PostMapping("/detect/material")
    public R<MaterialDetectResult> detectMaterial(@RequestParam("file") MultipartFile file) {
        try {
            return R.ok(visionClient.detectMaterial(file.getBytes()));
        } catch (IOException e) {
            return R.fail("读取图片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 车间操作台 1:N 刷脸识别(返回 top-3 候选 + 工人信息,不签发身份)
     * <p>安全约定:前端手选候选后必须调用 /cv/face/verify 做 1:1 确认,
     * 1:N 结果不得单独作为登录凭证(见方案 3.2.2)。</p>
     */
    @PostMapping("/ops/identify")
    public R<List<Map<String, Object>>> identify(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "limit", defaultValue = "3") int limit) {
        try {
            return R.ok(opsService.identify(file.getBytes(), limit));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }
}
