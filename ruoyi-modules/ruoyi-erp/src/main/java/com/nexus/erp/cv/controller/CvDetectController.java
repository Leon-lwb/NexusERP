package com.nexus.erp.cv.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.DefectScenario;
import com.nexus.common.cv.domain.DetectDtos.DefectDetectResult;
import com.nexus.common.cv.domain.DetectDtos.MaterialDetectResult;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.erp.cv.service.CvLabelParseService;
import com.nexus.erp.cv.service.CvOpsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉识别检测控制器(物料 OCR、工业缺陷检测、操作台人脸)
 * <p>端点与方案文档 3.3 对应:/cv/detect/product(物料 OCR)、/cv/detect/material(YOLO 兜底)、
 * /cv/detect/defect(工业缺陷)、/cv/ops/identify(操作台 1:N top-3)。统一经 ERP 代理,不直连 vision 服务。</p>
 * <p><b>鉴权</b>:检测与识别端点都会消耗视觉服务额度并暴露底库候选,必须带权限码
 * ({@code cv:detect:query} / {@code cv:ops:identify}),不能仅靠登录态。</p>
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
    @SaCheckPermission("cv:detect:query")
    @PostMapping("/detect/product")
    public R<Map<String, Object>> detectProduct(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "enableOcr", defaultValue = "true") boolean enableOcr,
                                                @RequestParam(value = "enableLabelParse", defaultValue = "false") boolean enableLabelParse) {
        try {
            ProductDetectResult result = visionClient.detectProduct(file.getBytes(), enableOcr);
            Map<String, Object> data = new HashMap<>();
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
     * 单张工业缺陷检测：仅接受 defect_metal / defect_pcb，固定使用成品推理且关闭 OCR。
     * <p>file 与 scenario 均必填；非法场景在读取图片或访问 Vision 前拒绝，禁止降级至通用模型。
     * detections.bbox 原样返回原图像素坐标 [x1, y1, x2, y2]，耗时为本次完整推理总耗时。</p>
     */
    @SaCheckPermission("cv:detect:query")
    @PostMapping(value = "/detect/defect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DefectDetectResult> detectDefect(@RequestParam("file") MultipartFile file,
                                             @RequestParam("scenario") String scenario) {
        if (!DefectScenario.isSupported(scenario)) {
            return R.fail("工业缺陷检测场景无效，请选择金属表面缺陷或 PCB缺陷");
        }
        if (file.isEmpty()) {
            return R.fail("检测图片不能为空");
        }
        try {
            ProductDetectResult inference = visionClient.detectProduct(file.getBytes(), false, scenario);
            if (!inference.isSuccess()) {
                return R.fail("工业缺陷检测未成功，请检查所选场景模型是否可用");
            }
            if (!scenario.equals(inference.getEffectiveScenario())) {
                return R.fail("视觉服务未确认所选工业缺陷检测场景，请检查模型配置与服务版本");
            }
            DefectDetectResult result = new DefectDetectResult();
            result.setScenario(inference.getEffectiveScenario());
            result.setSuccess(inference.isSuccess());
            result.setCount(inference.getCount());
            result.setDetections(inference.getDetections());
            result.setProcessTimeMs(inference.getProcessTimeMs());
            return R.ok(result);
        } catch (IOException e) {
            return R.fail("读取图片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * YOLO 通用检测(兜底,COCO 80 类;当前不含工厂料件类别)
     */
    @SaCheckPermission("cv:detect:query")
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
    @SaCheckPermission("cv:ops:identify")
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
