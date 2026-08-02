package com.nexus.erp.cv.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.FaceDtos.RecognizeResult;
import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import com.nexus.common.cv.domain.FaceDtos.SubjectItem;
import com.nexus.common.cv.domain.FaceDtos.SubjectList;
import com.nexus.common.cv.domain.FaceDtos.VerifyResult;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.erp.cv.service.CvFaceBatchRegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视觉识别底库管理控制器
 * <p>三端统一经 ERP 后端代理访问 nexus-vision-service,不直连视觉服务。
 * 端点与方案文档 3.3 对应:注册/1:1/1:N/底库列表/详情/删除/重新注册。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/cv/face")
public class CvFaceController {

    private final VisionClient visionClient;
    private final CvFaceBatchRegisterService batchRegisterService;

    /**
     * 批量注册(测试期):遍历已绑定头像的 SysUser,按 EMP-工号注册底库
     */
    @SaCheckPermission("cv:face:register")
    @PostMapping("/batch-register")
    public R<List<RegisterResult>> batchRegister(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        try {
            return R.ok(batchRegisterService.registerFromAvatars(limit));
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 注册人脸到底库(subject = EMP-工号,重复注册=追加样本取均值)
     */
    @SaCheckPermission("cv:face:register")
    @PostMapping("/register")
    public R<RegisterResult> register(@RequestParam("file") MultipartFile file,
                                      @RequestParam("subject") String subject,
                                      @RequestParam(value = "displayName", required = false) String displayName,
                                      @RequestParam(value = "remark", required = false) String remark) {
        try {
            return R.ok(visionClient.registerFace(file.getBytes(), subject, displayName, remark));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 1:1 人脸验证
     */
    @PostMapping("/verify")
    public R<VerifyResult> verify(@RequestParam("file") MultipartFile file,
                                  @RequestParam("subject") String subject) {
        try {
            return R.ok(visionClient.verifyFace(file.getBytes(), subject));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 1:N 人脸识别(返回 top-N 候选,不直接签发身份,供前端二次确认)
     */
    @PostMapping("/recognize")
    public R<RecognizeResult> recognize(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "limit", defaultValue = "3") int limit) {
        try {
            return R.ok(visionClient.recognizeFace(file.getBytes(), limit));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 底库分页列表
     */
    @SaCheckPermission("cv:face:list")
    @GetMapping("/list")
    public R<SubjectList> list(@RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        try {
            return R.ok(visionClient.listFaces(page, pageSize));
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 底库详情
     */
    @SaCheckPermission("cv:face:list")
    @GetMapping("/{subject}")
    public R<SubjectItem> get(@PathVariable("subject") String subject) {
        try {
            return R.ok(visionClient.getFace(subject));
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除底库条目
     */
    @SaCheckPermission("cv:face:remove")
    @DeleteMapping("/{subject}")
    public R<Boolean> delete(@PathVariable("subject") String subject) {
        try {
            return R.ok(visionClient.deleteFace(subject));
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 按员工重新注册(avatar 变更后手动触发;底库不自动跟随 avatar,见方案 3.2.4)
     */
    @SaCheckPermission("cv:face:register")
    @PostMapping("/re-register")
    public R<RegisterResult> reRegister(@RequestParam("file") MultipartFile file,
                                        @RequestParam("subject") String subject,
                                        @RequestParam(value = "displayName", required = false) String displayName,
                                        @RequestParam(value = "remark", required = false) String remark) {
        try {
            // 重新注册 = 先删旧再注册新样本(避免旧样本残留拉低精度)
            visionClient.deleteFace(subject);
            return R.ok(visionClient.registerFace(file.getBytes(), subject, displayName, remark));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 视觉服务探活(管理端状态展示)
     */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        try {
            var result = visionClient.ready();
            data.put("available", true);
            data.put("status", result.getStatus());
            data.put("models", result.getModels());
        } catch (VisionException e) {
            data.put("available", false);
            data.put("error", e.getMessage());
        }
        return R.ok(data);
    }
}
