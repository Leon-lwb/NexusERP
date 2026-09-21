package com.nexus.common.sign.service.impl;

import org.junit.jupiter.api.Tag;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 签名核心服务测试。
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class ESignatureServiceImplTest {

    @Mock private EsSignatureMapper signatureMapper;
    @Mock private EsSealMapper sealMapper;
    @Mock private EsSealGrantMapper grantMapper;
    @Mock private SignAuthVerifier authVerifier;
    @Mock private LockTemplate lockTemplate;
    @Mock private EsSignDocumentMapper documentMapper;
    @Mock private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @Captor private ArgumentCaptor<EsSignature> sigCaptor;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private MockedStatic<com.nexus.common.satoken.utils.LoginHelper> loginHelperMock;

    private static final Long ME = 7L;
    private static final String BIZ = SignBizType.APPROVAL;

    @BeforeEach
    void setUp() {
        service = new ESignatureServiceImpl(signatureMapper, sealMapper, grantMapper, authVerifier,
            lockTemplate, documentMapper, ossAccessorProvider);

        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SIGNATUREID", "TENANTID", "BIZTYPE", "BIZID", "BIZNO",
            "SIGNERUSERID", "SIGNERNAME", "SIGNERDEPTID", "SEALID", "SEALTYPE",
            "SIGNATUREMEANING", "AUTHMETHOD", "SRCDOCOSSID", "SRCDOCHASH", "DOCOSSID",
            "DOCHASH", "DOCID", "SIGNEDAT", "SIGNIP", "SIGNUA", "PREVHASH", "CHAINHASH", "REMARK"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignature.class)).thenReturn(cols);

        loginHelperMock = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getUserId).thenReturn(ME);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getTenantId).thenReturn("000000");
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getDeptId).thenReturn(100L);

        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(newLock());
        when(signatureMapper.selectOne(any())).thenReturn(null);
        when(signatureMapper.insert(any(EsSignature.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    private SignCommand cmd(Long sealId) {
        return new SignCommand(BIZ, 100L, "AP-001", "审批通过", sealId, SignAuthMethod.LOGIN_ONLY, null);
    }

    private EsSeal enabledPersonal() {
        EsSeal s = new EsSeal();
        s.setSealId(9L);
        s.setSealType(SealType.PERSONAL);
        s.setOwnerUserId(ME);
        s.setStatus(SealStatus.ENABLED);
        return s;
    }

    /** Lock4j 的 LockInfo 只有全参构造器，这里造一个可用于 releaseLock 断言的实例。 */
    private LockInfo newLock() {
        return new LockInfo("es:sign:chain:000000", "v", 30000L, 10000L, 1, null, null);
    }

    @Test
    @DisplayName("sign: 无印章的逻辑签名成功，链首 prevHash 为 null")
    void sign_withoutSeal_chainHead() {
        Long id = service.sign(cmd(null));
        assertNotNull(id, "应返回非空签名ID");
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();
        assertNull(saved.getPrevHash(), "链首 prevHash 应为 null");
        assertEquals(ME, saved.getSignerUserId());
        assertEquals("审批通过", saved.getSignatureMeaning());
        assertEquals(SignAuthMethod.LOGIN_ONLY, saved.getAuthMethod());
        assertEquals(BIZ, saved.getBizType());
        assertEquals(100L, saved.getBizId());
        assertNotNull(saved.getChainHash());
        assertEquals(64, saved.getChainHash().length());
        assertNotNull(saved.getSignedAt());
    }

    @Test
    @DisplayName("sign: chainHash 与计算器结果一致（可被验签复现）")
    void sign_chainHashReproducible() {
        service.sign(cmd(null));
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();
        String expected = SignatureChainCalculator.computeV2(
            saved.getPrevHash(), saved.getSignatureId(), saved.getBizType(), saved.getBizId(),
            saved.getSignerUserId(), saved.getSealId(), saved.getSignatureMeaning(),
            saved.getDocHash(), saved.getSignedAt().getTime(), saved.getAuthMethod(), saved.getBizNo());
        assertEquals(expected, saved.getChainHash());
    }

    @Test
    @DisplayName("sign: 链首之后 prevHash 取自上一条的 chainHash")
    void sign_linksToPrevious() {
        EsSignature prev = new EsSignature();
        prev.setSignatureId(1L);
        prev.setChainHash("a".repeat(64));
        when(signatureMapper.selectOne(any())).thenReturn(prev);

        service.sign(cmd(null));
        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals("a".repeat(64), sigCaptor.getValue().getPrevHash());
    }

    @Test
    @DisplayName("sign: 本人个人章 -> 通过并记录 sealId/sealType")
    void sign_personalSealOfSelf_ok() {
        when(sealMapper.selectById(9L)).thenReturn(enabledPersonal());
        service.sign(cmd(9L));
        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals(9L, sigCaptor.getValue().getSealId());
        assertEquals(SealType.PERSONAL, sigCaptor.getValue().getSealType());
    }

    @Test
    @DisplayName("sign: 他人个人章 -> 抛异常且不写库")
    void sign_personalSealOfOthers_rejected() {
        EsSeal other = enabledPersonal();
        other.setOwnerUserId(999L);
        when(sealMapper.selectById(9L)).thenReturn(other);
        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 印章不存在 -> 抛异常")
    void sign_sealNotFound_throws() {
        when(sealMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
    }

    @Test
    @DisplayName("sign: 停用印章 -> 抛异常")
    void sign_disabledSeal_rejected() {
        EsSeal s = enabledPersonal();
        s.setStatus(SealStatus.DISABLED);
        when(sealMapper.selectById(9L)).thenReturn(s);
        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
    }

    @Test
    @DisplayName("sign: 认证失败 -> 抛异常且不写库（不允许降级）")
    void sign_authFailure_rejected() {
        doThrow(new ServiceException("签署密码错误"))
            .when(authVerifier).verify(anyLong(), anyString(), any());
        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(BIZ, 100L, null, "审批通过", null, SignAuthMethod.PASSWORD, "bad")));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 缺 bizType -> 抛异常")
    void sign_blankBizType_rejected() {
        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(" ", 100L, null, "审批通过", null, SignAuthMethod.LOGIN_ONLY, null)));
    }

    @Test
    @DisplayName("sign: 缺签署含义 -> 抛异常")
    void sign_blankMeaning_rejected() {
        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(BIZ, 100L, null, "  ", null, SignAuthMethod.LOGIN_ONLY, null)));
    }

    @Test
    @DisplayName("sign: 取不到锁 -> 抛异常且不写库")
    void sign_lockUnavailable_rejected() {
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.sign(cmd(null)));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 无论成功失败都释放锁")
    void sign_alwaysReleasesLock() {
        LockInfo info = newLock();
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(info);
        service.sign(cmd(null));
        verify(lockTemplate).releaseLock(info);
    }
}
