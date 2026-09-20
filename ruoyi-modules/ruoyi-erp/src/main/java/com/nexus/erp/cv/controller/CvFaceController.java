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
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.cv.domain.vo.CvBatchRegisterResultVo;
import com.nexus.erp.cv.service.CvFaceBatchRegisterService;
import com.nexus.erp.cv.service.CvSubjectHelper;
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
 * <p><b>租户隔离</b>:底库 subject 统一由后端按当前租户归一化为
 * {@code T{tenantId}-EMP-{工号}}(见 {@link CvSubjectHelper}),前端传入的 subject 只作为工号载体,
 * 无法通过传参访问/删除他租户底库。</p>
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
     * 批量注册(含失败明细, ERP-CV-04)
     * <p>原批量注册端点只返回成功项, 被跳过/失败的人（无工人档案、头像读取失败、vision 报错）
     * 没有任何返回信息, 管理员无法对账与重试。本端点返回 {success[], failed[]} 摘要,
     * 失败项含 subject/工号/原因。</p>
     */
    @SaCheckPermission("cv:face:register")
    @PostMapping("/batch-register/detail")
    public R<CvBatchRegisterResultVo> batchRegisterDetail(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        try {
            return R.ok(batchRegisterService.registerFromAvatarsDetailed(limit));
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 注册人脸到底库(subject = T{tenantId}-EMP-工号,重复注册=追加样本取均值)
     */
    @SaCheckPermission("cv:face:register")
    @PostMapping("/register")
    public R<RegisterResult> register(@RequestParam("file") MultipartFile file,
                                      @RequestParam("subject") String subject,
                                      @RequestParam(value = "displayName", required = false) String displayName,
                                      @RequestParam(value = "remark", required = false) String remark) {
        try {
            return R.ok(visionClient.registerFace(file.getBytes(), scopedSubject(subject), displayName, remark));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 1:1 人脸验证
     * <p>底库含全租户人脸,识别类端点必须鉴权(否则任意登录账号可拿工号/刷底库)。</p>
     */
    @SaCheckPermission("cv:face:verify")
    @PostMapping("/verify")
    public R<VerifyResult> verify(@RequestParam("file") MultipartFile file,
                                  @RequestParam("subject") String subject) {
        try {
            return R.ok(visionClient.verifyFace(file.getBytes(), scopedSubject(subject)));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 1:N 人脸识别(返回 top-N 候选,不直接签发身份,供前端二次确认)
     */
    @SaCheckPermission("cv:face:recognize")
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
            return R.ok(visionClient.getFace(scopedSubject(subject)));
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
            return R.ok(visionClient.deleteFace(scopedSubject(subject)));
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
            String scoped = scopedSubject(subject);
            visionClient.deleteFace(scoped);
            return R.ok(visionClient.registerFace(file.getBytes(), scoped, displayName, remark));
        } catch (IOException e) {
            return R.fail("读取照片失败: " + e.getMessage());
        } catch (VisionException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 视觉服务探活(管理端状态展示)
     */
    @SaCheckPermission("cv:face:list")
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

    /**
     * 把前端传入的 subject 归一到当前登录租户(T{tenantId}-EMP-工号),防止跨租户读写底库。
     */
    private String scopedSubject(String subject) {
        return CvSubjectHelper.normalize(TenantHelper.getTenantId(), subject);
    }
}
