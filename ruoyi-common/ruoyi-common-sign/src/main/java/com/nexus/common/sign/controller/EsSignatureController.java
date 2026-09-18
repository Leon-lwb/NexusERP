package com.nexus.common.sign.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 签名记录Controller
 *
 * <p>只读查询与验签：签名记录的<b>写入统一走 {@code ESignatureService.sign()}</b>，
 * 由各业务模块在审批/查阅/处置动作中调用，不对外暴露创建接口 ——
 * 签名链 append-only，任何补录都会破坏链哈希的可信度。</p>
 *
 * @author nexus
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/record")
public class EsSignatureController extends BaseController {

    private final ESignatureService signatureService;

    /**
     * 按业务查询签名记录
     * <p>权限标识：{@code system:sign:record:list}</p>
     * <p>按签署时间正序返回，{@code bizId} 为空时返回该业务类型下的全部记录。</p>
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（可选）
     * @return 签名记录列表
     */
    @SaCheckPermission("system:sign:record:list")
    @GetMapping("/list")
    public R<List<SignatureVo>> list(@RequestParam String bizType,
                                     @RequestParam(required = false) Long bizId) {
        return R.ok(signatureService.listByBiz(bizType, bizId));
    }

    /**
     * 单条验签
     * <p>权限标识：{@code system:sign:record:verify}</p>
     * <p>校验记录自身哈希可复现，及其与租户全局链上真实前驱的衔接。</p>
     *
     * @param signatureId 签名ID
     * @return 验签结果
     */
    @SaCheckPermission("system:sign:record:verify")
    @GetMapping("/{signatureId}/verify")
    public R<VerifyResultVo> verify(@PathVariable Long signatureId) {
        return R.ok(signatureService.verify(signatureId));
    }

    /**
     * 全链验签
     * <p>权限标识：{@code system:sign:record:verify}</p>
     * <p>对该业务范围内的全部签名逐条重算并校验链接，{@code bizId} 为空时校验该业务类型下全部记录。</p>
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（可选）
     * @return 验签结果
     */
    @SaCheckPermission("system:sign:record:verify")
    @GetMapping("/verify-chain")
    public R<VerifyResultVo> verifyChain(@RequestParam String bizType,
                                         @RequestParam(required = false) Long bizId) {
        return R.ok(signatureService.verifyChain(bizType, bizId));
    }

}
