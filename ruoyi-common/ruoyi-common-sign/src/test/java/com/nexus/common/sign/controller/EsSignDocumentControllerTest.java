package com.nexus.common.sign.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.bo.DocSignBo;
import com.nexus.common.sign.domain.dto.DocSignCommand;
import com.nexus.common.sign.domain.dto.SealPlacement;
import com.nexus.common.sign.domain.vo.DocumentVerifyVo;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.service.IESSignDocumentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code EsSignDocumentController} 的接口层测试（P2a-3）。
 *
 * <p><b>为什么这些用例值得写：</b>本项目的失败模式不是「服务层写错了」，而是
 * 「服务层写对了却没人能调到」—— {@code ActHandleBtn.vue}、{@code signDisposition}
 * 都是这么烂在仓库里的。所以这里逐条钉住三件事：
 * ① 每个端点真的委托到了实现签章/验签的那两个服务方法（且入参没有被改写）；
 * ② URL 与 multipart 字段名就是前端要调的那个（改名 = 静默 404 / 422）；
 * ③ 控制器上出现的每一个权限标识都已在 {@code script/sql/ry-vue.sql} 种下
 * —— 本项目已发生过「代码引用权限、库里没种」把用户锁死且无从授权的缺陷。</p>
 *
 * <p>不启 Spring 上下文：控制器只是薄薄一层委托，直接实例化 + Mockito 断言委托关系，
 * 与本模块其它测试（{@code EsSealGrantController} 等所在的 common 模块）保持一致。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class EsSignDocumentControllerTest {

    private static final Long DOC_ID = 88001L;
    private static final Long BIZ_ID = 100L;
    private static final Long FILE_OSS_ID = 5001L;
    private static final Long SIGNED_OSS_ID = 6001L;
    private static final Long SIGNATURE_ID = 7001L;
    private static final Long SEAL_ID = 9L;
    private static final String BIZ = SignBizType.SO_CONTRACT;

    /** 权限种子的真值来源：初始化脚本 */
    private static final String SEED_FILE = "script/sql/ry-vue.sql";

    @Mock
    private IESSignDocumentService documentService;
    @Mock
    private ESignatureService signatureService;
    @Captor
    private ArgumentCaptor<DocSignCommand> signCmdCaptor;

    private EsSignDocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new EsSignDocumentController(documentService, signatureService);
    }

    // ---------------------------------------------------------------- 委托

    @Test
    @DisplayName("上传：委托服务登记并返回新文档ID（multipart 字段名固定为 file）")
    void upload_delegatesAndReturnsDocId() {
        MultipartFile file = new MockMultipartFile("file", "采购合同.pdf", "application/pdf",
            "%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        when(documentService.uploadDocument(BIZ, BIZ_ID, file)).thenReturn(DOC_ID);

        R<Long> result = controller.upload(file, BIZ, BIZ_ID);

        assertEquals(DOC_ID, result.getData(), "必须把服务返回的文档ID透给前端，前端要拿它去签章");
        verify(documentService).uploadDocument(BIZ, BIZ_ID, file);
    }

    @Test
    @DisplayName("列表 / 当前版本 / 详情：均委托查询服务，不做二次加工")
    void queryEndpoints_delegate() {
        EsSignDocumentVo doc = doc();
        when(documentService.listByBiz(BIZ, BIZ_ID)).thenReturn(List.of(doc));
        when(documentService.getCurrent(BIZ, BIZ_ID)).thenReturn(doc);
        when(documentService.getByDocId(DOC_ID)).thenReturn(doc);

        assertEquals(List.of(doc), controller.list(BIZ, BIZ_ID).getData());
        assertEquals(doc, controller.current(BIZ, BIZ_ID).getData());
        assertEquals(doc, controller.getInfo(DOC_ID).getData());

        verify(documentService).listByBiz(BIZ, BIZ_ID);
        verify(documentService).getCurrent(BIZ, BIZ_ID);
        verify(documentService).getByDocId(DOC_ID);
    }

    @Test
    @DisplayName("签章：业务键取自文档行（不信客户端），其余入参原样交给 signDocument，返回签名ID")
    void sign_delegatesWithBusinessKeyFromDocumentRow() {
        when(documentService.getByDocId(DOC_ID)).thenReturn(doc());
        when(signatureService.signDocument(any())).thenReturn(SIGNATURE_ID);
        DocSignBo bo = signBo();

        R<Long> result = controller.sign(DOC_ID, bo);

        assertEquals(SIGNATURE_ID, result.getData(), "必须返回 signatureId，前端要拿它去验签");

        verify(signatureService).signDocument(signCmdCaptor.capture());
        DocSignCommand cmd = signCmdCaptor.getValue();
        assertEquals(BIZ, cmd.bizType(), "bizType 必须取自文档行，否则客户端能把章盖到别的单据上");
        assertEquals(BIZ_ID, cmd.bizId(), "bizId 必须取自文档行");
        assertEquals(DOC_ID, cmd.docId());
        assertEquals(bo.getPlacements(), cmd.placements(), "落位必须原样下发（坐标语义由 PDF 层负责）");
        assertEquals("合同签署", cmd.signatureMeaning());
        assertEquals(SignAuthMethod.LOGIN_ONLY, cmd.authMethod());
        assertEquals("token", cmd.credential());
        verify(documentService).getByDocId(DOC_ID);
    }

    @Test
    @DisplayName("验签：委托 verifyDocument（文档级验签，而非签名记录自校验）")
    void verify_delegatesToVerifyDocument() {
        DocumentVerifyVo vo = new DocumentVerifyVo();
        vo.setSignatureId(SIGNATURE_ID);
        vo.setPassed(true);
        when(signatureService.verifyDocument(SIGNATURE_ID)).thenReturn(vo);

        R<DocumentVerifyVo> result = controller.verify(SIGNATURE_ID);

        assertEquals(vo, result.getData());
        assertTrue(result.getData().getPassed());
        verify(signatureService).verifyDocument(SIGNATURE_ID);
    }

    @Test
    @DisplayName("下载地址：已签给签署后文件，未签给原始正文")
    void download_prefersSignedFile() {
        when(documentService.getByDocId(DOC_ID)).thenReturn(doc());

        assertEquals(SIGNED_OSS_ID, controller.download(DOC_ID).getData(),
            "已签署的文档，业务方要看的是签完的那一份");

        EsSignDocumentVo unsigned = doc();
        unsigned.setSignedOssId(null);
        when(documentService.getByDocId(DOC_ID)).thenReturn(unsigned);
        assertEquals(FILE_OSS_ID, controller.download(DOC_ID).getData(),
            "未签署时回落到上传的原始正文");
    }

    // ---------------------------------------------------------------- 契约守卫

    @Test
    @DisplayName("路径守卫：端点路径即前端调用契约（改路径 = 前端静默 404）")
    void endpointPathsAreStable() {
        Map<String, String> expected = Map.of(
            "upload", "/system/sign/document/upload",
            "list", "/system/sign/document/list",
            "current", "/system/sign/document/current",
            "getInfo", "/system/sign/document/{docId}",
            "download", "/system/sign/document/{docId}/download",
            "sign", "/system/sign/document/{docId}/sign",
            "verify", "/system/sign/document/signature/{signatureId}/verify");

        Map<String, String> actual = new HashMap<>();
        for (Method m : EsSignDocumentController.class.getDeclaredMethods()) {
            if (m.getAnnotation(GetMapping.class) != null || m.getAnnotation(PostMapping.class) != null) {
                actual.put(m.getName(), fullPath(m));
            }
        }
        assertEquals(expected, actual, "端点路径集合必须与前端契约完全一致");
    }

    @Test
    @DisplayName("multipart 字段名守卫：必须是 file（改名会让请求 422，参考 CvDetect 的既有约定）")
    void uploadUsesFileFieldName() throws Exception {
        Method upload = EsSignDocumentController.class
            .getMethod("upload", MultipartFile.class, String.class, Long.class);

        List<String> partNames = new ArrayList<>();
        for (Parameter parameter : upload.getParameters()) {
            RequestPart part = parameter.getAnnotation(RequestPart.class);
            if (part != null) {
                partNames.add(part.value());
            }
        }
        assertEquals(List.of("file"), partNames,
            "multipart 字段名必须叫 file —— 前端表单/FormData 用的就是这个名字");
        assertTrue(EsSignDocumentController.class.getAnnotation(RequestMapping.class) != null);
    }

    @Test
    @DisplayName("防重守卫：签章接口防重复提交；上传接口不能防重（重复拦截会把「重新上传正确版本」误杀）")
    void repeatSubmitPolicy() throws Exception {
        Method sign = EsSignDocumentController.class.getMethod("sign", Long.class, DocSignBo.class);
        assertNotNull(sign.getAnnotation(RepeatSubmit.class), "签章是不可逆的对外动作，必须防重复提交");

        Method upload = EsSignDocumentController.class
            .getMethod("upload", MultipartFile.class, String.class, Long.class);
        assertNull(upload.getAnnotation(RepeatSubmit.class),
            "上传即换版：重复提交拦截按「token + URL」判重，会把重新上传新版本误判成重复提交");

        assertNotNull(sign.getAnnotation(Log.class), "签章必须留操作日志");
        assertNotNull(upload.getAnnotation(Log.class), "上传必须留操作日志");
    }

    @Test
    @DisplayName("权限守卫：控制器引用的每个权限标识都必须在 ry-vue.sql 里种下（否则用户被锁死且无从授权）")
    void everyReferencedPermissionIsSeeded() throws IOException {
        // 控制器（含类级）上出现的全部权限标识
        List<String> referenced = new ArrayList<>();
        SaCheckPermission classLevel =
            EsSignDocumentController.class.getAnnotation(SaCheckPermission.class);
        if (classLevel != null) {
            referenced.addAll(List.of(classLevel.value()));
        }
        for (Method m : EsSignDocumentController.class.getDeclaredMethods()) {
            SaCheckPermission perm = m.getAnnotation(SaCheckPermission.class);
            if (perm != null) {
                referenced.addAll(List.of(perm.value()));
            }
        }
        assertFalse(referenced.isEmpty(), "控制器必须声明权限标识，否则接口对任何登录用户开放");

        // 只读守卫：逐行读仓库里的初始化脚本（39MB，不整文件读进内存），本测试不修改任何东西
        Path seedFile = locateSeedFile();
        Set<String> seeded = new HashSet<>();
        try (Stream<String> lines = Files.lines(seedFile, StandardCharsets.UTF_8)) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext() && seeded.size() < referenced.size()) {
                String line = it.next();
                for (String perm : referenced) {
                    if (line.contains("'" + perm + "'")) {
                        seeded.add(perm);
                    }
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String perm : referenced) {
            if (!seeded.contains(perm)) {
                missing.add(perm);
            }
        }
        assertTrue(missing.isEmpty(),
            "以下权限标识被代码引用但未在 " + SEED_FILE + " 中种下，用户将无从授权（历史缺陷重演）: " + missing);
    }

    // ---------------------------------------------------------------- 夹具

    private static DocSignBo signBo() {
        DocSignBo bo = new DocSignBo();
        bo.setPlacements(List.of(new SealPlacement(0, 0.1f, 0.6f, 0.25f, 0.15f, 0, SEAL_ID)));
        bo.setSignatureMeaning("合同签署");
        bo.setAuthMethod(SignAuthMethod.LOGIN_ONLY);
        bo.setCredential("token");
        return bo;
    }

    private static EsSignDocumentVo doc() {
        EsSignDocumentVo vo = new EsSignDocumentVo();
        vo.setDocId(DOC_ID);
        vo.setBizType(BIZ);
        vo.setBizId(BIZ_ID);
        vo.setFileOssId(FILE_OSS_ID);
        vo.setSignedOssId(SIGNED_OSS_ID);
        vo.setFileName("采购合同.pdf");
        vo.setDocVersion(1);
        vo.setIsCurrent("1");
        vo.setSignStatus("2");
        return vo;
    }

    /** 拼出端点的完整路径（类级 {@code @RequestMapping} + 方法级映射）。 */
    private static String fullPath(Method m) {
        RequestMapping base = EsSignDocumentController.class.getAnnotation(RequestMapping.class);
        String path = base.value()[0];
        PostMapping post = m.getAnnotation(PostMapping.class);
        GetMapping get = m.getAnnotation(GetMapping.class);
        String[] values = post != null ? post.value() : (get != null ? get.value() : new String[0]);
        return values.length == 0 ? path : path + values[0];
    }

    /**
     * 定位初始化脚本 {@code script/sql/ry-vue.sql}。
     *
     * <p>从当前工作目录逐级上溯查找：Surefire 的工作目录是模块目录
     * （{@code ruoyi-common/ruoyi-common-sign}），IDE 里可能是仓库根目录，两种都能命中。</p>
     *
     * @return 脚本路径
     */
    private static Path locateSeedFile() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(SEED_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到初始化脚本 " + SEED_FILE
            + "（自 " + System.getProperty("user.dir") + " 上溯 6 级仍未命中）");
    }
}
