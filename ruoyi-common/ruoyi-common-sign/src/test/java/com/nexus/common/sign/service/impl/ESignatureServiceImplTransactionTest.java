package com.nexus.common.sign.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code signInNewTransaction}（REQUIRES_NEW）与 {@code sign} 的事务语义/行为等价测试。
 *
 * <p><b>为什么需要这个入口：</b>{@code sign} 的传播行为是 {@code REQUIRED}，会加入调用方事务。
 * 签名失败抛出 RuntimeException 时，Spring 会先把<b>共享</b>事务标记为 rollback-only；
 * 调用方即便 try/catch 住异常，提交时仍会抛 {@code UnexpectedRollbackException}，
 * 把本该受保护的业务写（如 SOP 查阅记录）一起回滚 —— 即"降级"落空。
 * 因此 SOP 查阅走 {@code signInNewTransaction}。</p>
 *
 * <p><b>本测试的边界：</b>{@code @Transactional} 只在 Spring 代理处生效，单元测试没有事务管理器，
 * 所以这里验证的是 ① 两个入口的成功/失败行为完全一致（共用同一段签名逻辑，不会漂移）；
 * ② 事务注解声明正确（反射守卫，防止有人把 REQUIRES_NEW 删掉）。
 * 真实的事务回滚隔离语义需要带真实事务管理器的集成测试（Task 17 E2E）覆盖。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class ESignatureServiceImplTransactionTest {

    @Mock
    private EsSignatureMapper signatureMapper;
    @Mock
    private EsSealMapper sealMapper;
    @Mock
    private EsSealGrantMapper grantMapper;
    @Mock
    private SignAuthVerifier authVerifier;
    @Mock
    private LockTemplate lockTemplate;
    @Mock
    private EsSignDocumentMapper documentMapper;
    @Mock
    private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @Captor
    private ArgumentCaptor<EsSignature> sigCaptor;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long ME = 7L;
    private static final String BIZ = SignBizType.SOP_VIEW;

    /** 每次调用天然变化的字段：主键（雪花ID）、签名时间、以及依赖前两者的链哈希。 */
    private static final Set<String> PER_CALL_FIELDS = Set.of("signatureId", "signedAt", "chainHash");

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

        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(ME);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn("000000");
        loginHelperMock.when(LoginHelper::getDeptId).thenReturn(100L);

        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(newLock());
        when(signatureMapper.selectOne(any())).thenReturn(null);
        when(signatureMapper.insert(any(EsSignature.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    private SignCommand cmd() {
        return new SignCommand(BIZ, 100L, "V2", "SOP 已阅确认（版本 V2）", null,
            SignAuthMethod.LOGIN_ONLY, null);
    }

    /** Lock4j 的 LockInfo 只有全参构造器，这里造一个可用于 releaseLock 断言的实例。 */
    private LockInfo newLock() {
        return new LockInfo("es:sign:chain:000000", "v", 30000L, 10000L, 1, null, null);
    }

    /**
     * 取出「一次调用应当产出完全相同的字段」快照：跳过 PER_CALL_FIELDS 与静态字段，
     * 其余字段（含将来新增的字段）全部纳入比对 —— 新增字段若只加在其中一个入口，测试即失败。
     */
    private static Map<String, Object> comparableSnapshot(EsSignature s) {
        Map<String, Object> snapshot = new TreeMap<>();
        for (Field f : EsSignature.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || PER_CALL_FIELDS.contains(f.getName())) {
                continue;
            }
            f.setAccessible(true);
            try {
                snapshot.put(f.getName(), f.get(s));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("无法读取字段 " + f.getName(), e);
            }
        }
        return snapshot;
    }

    /** 用记录自身字段重算链哈希（与验签口径一致）。 */
    private static String recomputeChainHash(EsSignature s) {
        return SignatureChainCalculator.computeV2(
            s.getPrevHash(), s.getSignatureId(), s.getBizType(), s.getBizId(),
            s.getSignerUserId(), s.getSealId(), s.getSignatureMeaning(),
            s.getDocHash(), s.getSignedAt().getTime(), s.getAuthMethod(), s.getBizNo());
    }

    @Test
    @DisplayName("signInNewTransaction: 成功路径与 sign 产出的签名记录字段完全一致（无逻辑漂移）")
    void signInNewTransaction_successPath_identicalRowToSign() {
        SignCommand cmd = cmd();

        Long idViaSign = service.sign(cmd);
        Long idViaNewTx = service.signInNewTransaction(cmd);

        assertNotNull(idViaSign);
        assertNotNull(idViaNewTx);
        assertNotEquals(idViaSign, idViaNewTx, "两次调用各自生成独立签名ID");

        verify(signatureMapper, times(2)).insert(sigCaptor.capture());
        List<EsSignature> saved = sigCaptor.getAllValues();
        EsSignature viaSign = saved.get(0);
        EsSignature viaNewTx = saved.get(1);

        assertEquals(comparableSnapshot(viaSign), comparableSnapshot(viaNewTx),
            "两个入口除事务边界外必须产出完全相同的签名记录内容");

        // 两条记录的链哈希都能被计算器复现 —— 证明走的是同一段链式计算
        assertEquals(recomputeChainHash(viaSign), viaSign.getChainHash());
        assertEquals(recomputeChainHash(viaNewTx), viaNewTx.getChainHash());
        assertNotNull(viaNewTx.getSignedAt());
    }

    @Test
    @DisplayName("signInNewTransaction: 链接前驱（prevHash）行为与 sign 一致")
    void signInNewTransaction_linksToPrevious_likeSign() {
        EsSignature prev = new EsSignature();
        prev.setSignatureId(1L);
        prev.setChainHash("b".repeat(64));
        when(signatureMapper.selectOne(any())).thenReturn(prev);

        service.signInNewTransaction(cmd());

        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();
        assertEquals("b".repeat(64), saved.getPrevHash(), "链尾 chainHash 应作为本条 prevHash");
        assertEquals(recomputeChainHash(saved), saved.getChainHash());
    }

    @Test
    @DisplayName("signInNewTransaction: 校验失败与 sign 同样直接拒绝且不写库（不因换事务边界而放宽）")
    void signInNewTransaction_invalidCommand_rejectedLikeSign() {
        SignCommand blankBizType = new SignCommand("  ", 100L, null, "SOP 已阅确认", null,
            SignAuthMethod.LOGIN_ONLY, null);

        assertThrows(ServiceException.class, () -> service.sign(blankBizType));
        assertThrows(ServiceException.class, () -> service.signInNewTransaction(blankBizType));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("signInNewTransaction: 取不到链锁（签名通道繁忙）同样抛异常且不写库")
    void signInNewTransaction_lockUnavailable_throwsServiceException() {
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.signInNewTransaction(cmd()));
        assertTrue(ex.getMessage().contains("签名通道繁忙"),
            "SOP 查阅降级场景正是由这条异常触发，异常语义必须与 sign 保持一致");
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("signInNewTransaction: 无论成功失败都释放锁")
    void signInNewTransaction_alwaysReleasesLock() {
        LockInfo info = newLock();
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(info);

        service.signInNewTransaction(cmd());

        verify(lockTemplate).releaseLock(info);
    }

    @Test
    @DisplayName("反射守卫: signInNewTransaction 必须声明 REQUIRES_NEW（丢失该注解降级即失效）")
    void signInNewTransaction_annotationIsRequiresNew() throws NoSuchMethodException {
        Transactional tx = transactionalOf("signInNewTransaction");
        assertNotNull(tx, "signInNewTransaction 缺少 @Transactional：事务边界消失，签署失败仍会污染调用方事务");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
            "必须是 REQUIRES_NEW：REQUIRED 会加入调用方事务，失败时把调用方事务标记为 rollback-only");
        assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor());
    }

    @Test
    @DisplayName("反射守卫: sign 必须保持默认传播（加入调用方事务，NCR/审批流依赖该语义）")
    void sign_annotationIsRequired() throws NoSuchMethodException {
        Transactional tx = transactionalOf("sign");
        assertNotNull(tx, "sign 缺少 @Transactional");
        assertEquals(Propagation.REQUIRED, tx.propagation(),
            "sign 必须保持 REQUIRED：NCR 处置/审批签署属于授权，应与调用方同一事务");
        assertArrayEquals(new Class<?>[]{Exception.class}, tx.rollbackFor());
    }

    private static Transactional transactionalOf(String methodName) throws NoSuchMethodException {
        Method method = ESignatureServiceImpl.class.getMethod(methodName, SignCommand.class);
        return method.getAnnotation(Transactional.class);
    }
}
