package com.nexus.common.sign.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.sign.domain.bo.DocSignBo;
import com.nexus.common.sign.domain.dto.DocSignCommand;
import com.nexus.common.sign.domain.vo.DocumentVerifyVo;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.service.IESSignDocumentService;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 待签文档正文Controller（P2a-3）。
 *
 * <p>本控制器是「PDF 签章」的对外入口：先用 {@code /upload} 把合同 PDF 登记进
 * {@code es_sign_document}，再用 {@code /{docId}/sign} 盖章并施加数字签名，
 * 最后用 {@code /signature/{signatureId}/verify} 做文档级验签。
 * 没有它，{@code ESignatureService#signDocument} / {@code verifyDocument} 无从调用。</p>
 *
 * <p><b>权限标识（全部已在 {@code script/sql/ry-vue.sql} 种下，menu_id 4363-4366）：</b>
 * {@code system:sign:document:list} / {@code :upload} / {@code :sign} / {@code :verify}。
 * 曾经出现过「代码里引用了权限但库里没有种」导致用户被锁死且无从授权的缺陷，
 * {@code EsSignDocumentControllerTest} 里有一条守卫用例专门盯这件事。</p>
 *
 * @author nexus
 * @date 2026-09-17
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/document")
public class EsSignDocumentController extends BaseController {

    private final IESSignDocumentService documentService;
    private final ESignatureService signatureService;

    /**
     * 上传并登记待签文档正文（PDF）
     * <p>权限标识：{@code system:sign:document:upload}</p>
     * <p>日志记录：title="待签文档"，businessType=INSERT</p>
     * <p>multipart 字段名固定为 {@code file}（本仓库既有约定，改名会让请求 422）；
     * 同一业务键重复上传即换版，所以<b>不能</b>加 {@code @RepeatSubmit}
     * —— 重复提交拦截按「token + URL」判重，会把「发现传错版本后重新上传」
     * 这种正常操作误判为重复提交。</p>
     * <p>字节级校验全在服务端（魔数/大小/文件名/可解析性），不依赖前端。</p>
     *
     * @param file    上传的 PDF 文件（multipart 字段名 {@code file}）
     * @param bizType 业务类型（如 {@code SO_CONTRACT}）
     * @param bizId   业务单据ID
     * @return 新登记的文档ID（{@code es_sign_document.doc_id}）
     */
    @SaCheckPermission("system:sign:document:upload")
    @Log(title = "待签文档", businessType = BusinessType.INSERT)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> upload(@RequestPart("file") MultipartFile file,
                          @RequestParam String bizType,
                          @RequestParam Long bizId) {
        return R.ok(documentService.uploadDocument(bizType, bizId, file));
    }

    /**
     * 按业务键查询文档列表
     * <p>权限标识：{@code system:sign:document:list}</p>
     * <p>按版本倒序返回；{@code bizId} 为空时返回该业务类型下的全部文档。</p>
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（可选）
     * @return 文档列表
     */
    @SaCheckPermission("system:sign:document:list")
    @GetMapping("/list")
    public R<List<EsSignDocumentVo>> list(@RequestParam String bizType,
                                          @RequestParam(required = false) Long bizId) {
        return R.ok(documentService.listByBiz(bizType, bizId));
    }

    /**
     * 查询业务键下当前生效版本的文档
     * <p>权限标识：{@code system:sign:document:list}</p>
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（必填）
     * @return 当前生效文档；尚无文档时 {@code data} 为 null
     */
    @SaCheckPermission("system:sign:document:list")
    @GetMapping("/current")
    public R<EsSignDocumentVo> current(@RequestParam String bizType,
                                       @RequestParam Long bizId) {
        return R.ok(documentService.getCurrent(bizType, bizId));
    }

    /**
     * 获取文档详细信息
     * <p>权限标识：{@code system:sign:document:list}</p>
     *
     * @param docId 文档ID
     * @return 文档详情
     */
    @SaCheckPermission("system:sign:document:list")
    @GetMapping("/{docId}")
    public R<EsSignDocumentVo> getInfo(@PathVariable Long docId) {
        return R.ok(documentService.getByDocId(docId));
    }

    /**
     * 获取文档正文当前应下载的 OSS ID
     * <p>权限标识：{@code system:sign:document:list}</p>
     * <p>已签署则返回签署后文件（{@code signed_oss_id}），否则返回上传的原始正文
     * （{@code file_oss_id}）—— 业务方要看的是「最后生效的那一份」。</p>
     * <p><b>为何返回 ossId 而不是直接流式输出：</b>本模块为 common 模块，
     * 唯一能把 ossId 解析成字节流的 {@code ISysOssService} 位于
     * {@code ruoyi-modules/ruoyi-system}，而该系统模块<b>反向依赖本模块</b>，
     * common → system 会构成 Maven 循环依赖。故此处只返回 ossId，
     * 由前端经既有的鉴权端点 {@code /resource/oss/download/{ossId}} 取流
     * （与 {@code EsSealController#preview} 返回预览图 ossId 同一手法）。</p>
     *
     * @param docId 文档ID
     * @return 可下载文件的 OSS ID
     */
    @SaCheckPermission("system:sign:document:list")
    @GetMapping("/{docId}/download")
    public R<Long> download(@PathVariable Long docId) {
        EsSignDocumentVo doc = documentService.getByDocId(docId);
        return R.ok(doc.getSignedOssId() != null ? doc.getSignedOssId() : doc.getFileOssId());
    }

    /**
     * 文档签章（盖章 + 数字签名，产出新 PDF）
     * <p>权限标识：{@code system:sign:document:sign}</p>
     * <p>日志记录：title="文档签章"，businessType=UPDATE</p>
     * <p><b>业务键一律取自文档行</b>（不信客户端）：{@code bizType}/{@code bizId} 由服务端
     * 从 {@code es_sign_document} 读出后交给 {@code signDocument}，因此客户端无法
     * 把章盖到别的单据上。用章授权、二级认证、源正文魔数与摘要复核全部在服务层完成。</p>
     *
     * @param docId 文档ID
     * @param bo    签章入参（印章落位 / 签署含义 / 认证方式 / 凭证）
     * @return 新写入的签名ID（{@code es_signature.signature_id}）
     */
    @SaCheckPermission("system:sign:document:sign")
    @Log(title = "文档签章", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{docId}/sign")
    public R<Long> sign(@PathVariable Long docId, @Validated @RequestBody DocSignBo bo) {
        EsSignDocumentVo doc = documentService.getByDocId(docId);
        return R.ok(signatureService.signDocument(new DocSignCommand(
            doc.getBizType(), doc.getBizId(), docId, bo.getPlacements(),
            bo.getSignatureMeaning(), bo.getAuthMethod(), bo.getCredential())));
    }

    /**
     * 文档级验签（校验签署后 PDF 的完整性）
     * <p>权限标识：{@code system:sign:document:verify}</p>
     * <p>检查 CMS/PKCS#7 验签、ByteRange 覆盖与增量修订，并用台账登记的 SM3 做旁证；
     * 结论见返回体的 {@code verdict}（{@code OK}/{@code MODIFIED_IN_PLACE}/…）。</p>
     * <p>路径取 {@code /signature/{signatureId}/verify} 而非复用 {@code /{docId}/verify}：
     * 验签的对象是「某一次签署产生的那个文件」，签名ID才是它的主键；
     * 顺带也避开了与 {@code GET /{docId}} 的路径歧义。</p>
     *
     * @param signatureId 签名记录ID（必须是 {@code /{docId}/sign} 产出的含文档签名）
     * @return 验签结果
     */
    @SaCheckPermission("system:sign:document:verify")
    @GetMapping("/signature/{signatureId}/verify")
    public R<DocumentVerifyVo> verify(@PathVariable Long signatureId) {
        return R.ok(signatureService.verifyDocument(signatureId));
    }
}
