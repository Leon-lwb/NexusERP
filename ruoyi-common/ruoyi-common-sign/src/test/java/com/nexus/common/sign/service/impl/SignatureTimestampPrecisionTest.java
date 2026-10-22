package com.nexus.common.sign.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签名时间戳精度回归测试 —— 锁死「落库毫秒截断导致验签必然失败」的线上缺陷。
 *
 * <p><b>线上实测现象：</b>全新写入、未经任何篡改的签名记录，单条验签返回
 * {@code passed=false, messages=["签名记录内容与链哈希不一致，疑似被篡改"]}。</p>
 *
 * <p><b>根因：</b>{@code es_signature.signed_at} 是 MySQL {@code datetime}(0)（无小数秒），
 * 而 {@code SignatureChainCalculator.compute(..., signedAt.getTime())} 把<b>毫秒级</b>时间戳编进链哈希。
 * 毫秒在落库时被静默截断，验签重算时拿到的是 {@code ...000}，与写入时使用的真实毫秒不等，
 * 于是几乎每条记录（约 999/1000）都会「自证被篡改」。</p>
 *
 * <p><b>为什么原有的 88 个单测抓不到：</b>它们全部在内存里断言 {@code sign()} 的返回值，
 * 从未模拟过「经数据库列精度往返」。本类补上的正是这一步 —— {@link #readBackFromDbAsMysqlDatetime}
 * 严格按 {@code datetime}(0) 的语义截断 {@code signed_at}，再用与 {@code verify()} 相同的方式重算链哈希。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class SignatureTimestampPrecisionTest {

    /**
     * 单条签名的毫秒恰好为 0 的概率约 1/1000（Windows 时钟粒度下约 1/64）。
     * 采样若干条后，「缺陷存在却全部侥幸通过」的概率趋近于 0，使本回归测试稳定可靠。
     */
    private static final int SAMPLES = 8;

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

    /** 逐条收集真正传给 {@code insert} 的实体（模拟数据库写入的入口）。 */
    private final List<EsSignature> inserted = new ArrayList<>();

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

        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(ME);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn("000000");
        loginHelperMock.when(LoginHelper::getDeptId).thenReturn(100L);

        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(newLock());
        when(signatureMapper.selectOne(any())).thenReturn(null);
        inserted.clear();
        when(signatureMapper.insert(any(EsSignature.class))).thenAnswer(inv -> {
            inserted.add(inv.getArgument(0));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    private SignCommand cmd() {
        return new SignCommand(BIZ, 100L, "AP-001", "审批通过", null, SignAuthMethod.LOGIN_ONLY, null);
    }

    private LockInfo newLock() {
        return new LockInfo("es:sign:chain:000000", "v", 30000L, 10000L, 1, null, null);
    }

    /** 连续签署 {@link #SAMPLES} 次，逐条捕获真正传给 {@code insert} 的实体。 */
    private List<EsSignature> signAndCaptureSamples() {
        for (int i = 0; i < SAMPLES; i++) {
            int before = inserted.size();
            Long id = service.sign(cmd());
            assertNotNull(id, "应返回非空签名ID");
            assertEquals(before + 1, inserted.size(), "每次签署应恰好写入一条记录");
            EsSignature entity = inserted.get(before);
            assertEquals(id, entity.getSignatureId(), "返回的ID应与入库实体一致");
        }
        assertEquals(SAMPLES, inserted.size(), "签名条数应与采样次数一致");
        return new ArrayList<>(inserted);
    }

    /**
     * 模拟 MySQL {@code datetime}(0) 的落库往返：小数秒被静默截断。
     *
     * @param d 写入前的 Java 时间
     * @return 数据库实际存下、并被读回的时间（毫秒归零）
     */
    private static Date readBackFromDbAsMysqlDatetime(Date d) {
        return new Date((d.getTime() / 1000L) * 1000L);
    }

    /**
     * 复刻「从库里读回的那一行」：字段逐一拷贝，{@code signed_at} 按 {@code datetime}(0) 截断，
     * {@code chain_hash} 保持库里存的原值不动。
     *
     * @param saved 写入时传给 insert 的实体
     * @return 数据库读回的等价实体
     */
    private static EsSignature readBackFromDbAsMysqlDatetime(EsSignature saved) {
        EsSignature db = new EsSignature();
        db.setSignatureId(saved.getSignatureId());
        db.setTenantId(saved.getTenantId());
        db.setBizType(saved.getBizType());
        db.setBizId(saved.getBizId());
        db.setBizNo(saved.getBizNo());
        db.setSignerUserId(saved.getSignerUserId());
        db.setSignerName(saved.getSignerName());
        db.setSignerDeptId(saved.getSignerDeptId());
        db.setSealId(saved.getSealId());
        db.setSealType(saved.getSealType());
        db.setSignatureMeaning(saved.getSignatureMeaning());
        db.setAuthMethod(saved.getAuthMethod());
        db.setSrcDocOssId(saved.getSrcDocOssId());
        db.setSrcDocHash(saved.getSrcDocHash());
        db.setDocOssId(saved.getDocOssId());
        db.setDocHash(saved.getDocHash());
        db.setDocId(saved.getDocId());
        db.setSignIp(saved.getSignIp());
        db.setSignUa(saved.getSignUa());
        db.setPrevHash(saved.getPrevHash());
        db.setChainHash(saved.getChainHash());
        db.setRemark(saved.getRemark());
        db.setSignedAt(saved.getSignedAt() == null ? null
            : readBackFromDbAsMysqlDatetime(saved.getSignedAt()));
        return db;
    }

    /**
     * 用与 {@code ESignatureServiceImpl.verify()} / {@code checkRecord()} <b>完全相同</b>的方式重算链哈希。
     *
     * @param cur 待重算的记录
     * @return 重算出的链哈希
     */
    private static String recomputeLikeVerify(EsSignature cur) {
        long signedAtMs = cur.getSignedAt() == null ? 0L : cur.getSignedAt().getTime();
        return SignatureChainCalculator.computeV2(
            cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
            cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
            cur.getDocHash(), signedAtMs, cur.getAuthMethod(), cur.getBizNo());
    }

    @Test
    @DisplayName("sign: signed_at 必须是整秒 —— 落库 datetime(0) 不得丢失参与哈希的时间精度")
    void sign_signedAtMustBeWholeSecond() {
        for (EsSignature saved : signAndCaptureSamples()) {
            assertNotNull(saved.getSignedAt(), "签署时间不能为空");
            assertEquals(0L, saved.getSignedAt().getTime() % 1000L,
                "参与链哈希的 signed_at 必须归零毫秒（es_signature.signed_at 为 datetime(0)，"
                    + "毫秒会被落库静默截断，导致验签重算不出同一个 chain_hash）；实际毫秒="
                    + (saved.getSignedAt().getTime() % 1000L));
        }
    }

    @Test
    @DisplayName("sign: 经 MySQL datetime 截断往返后 chain_hash 仍可复现（本缺陷的核心回归测试）")
    void sign_chainHashSurvivesMysqlDatetimeRoundTrip() {
        for (EsSignature saved : signAndCaptureSamples()) {
            EsSignature asStoredInDb = readBackFromDbAsMysqlDatetime(saved);
            String recomputed = recomputeLikeVerify(asStoredInDb);
            assertEquals(saved.getChainHash(), recomputed,
                "库中存下的 chain_hash 必须等于「按库中 signed_at（已截断）重算」的结果，"
                    + "否则每条新签名都会自证被篡改：stored chainHash=" + saved.getChainHash()
                    + ", recomputed=" + recomputed
                    + ", hashed signedAt(ms)=" + saved.getSignedAt().getTime()
                    + ", stored signedAt(ms)=" + asStoredInDb.getSignedAt().getTime());
        }
    }

    @Test
    @DisplayName("verify: 刚签署的记录按库中截断后的值验签 -> 通过（复刻线上失败场景）")
    void verify_freshSignatureWithTruncatedStorage_passes() {
        Long id = service.sign(cmd());
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature asStoredInDb = readBackFromDbAsMysqlDatetime(sigCaptor.getValue());
        when(signatureMapper.selectById(id)).thenReturn(asStoredInDb);

        VerifyResultVo r = service.verify(id);

        assertTrue(r.getPassed(),
            "未经篡改的新签名不得被判为被篡改，messages=" + r.getMessages()
                + ", hashed signedAt(ms)=" + sigCaptor.getValue().getSignedAt().getTime()
                + ", stored signedAt(ms)=" + asStoredInDb.getSignedAt().getTime());
        assertTrue(r.getChainPassed());
        assertNull(r.getBrokenAt());
    }

    @Test
    @DisplayName("根因确定性复现：毫秒级时间戳入哈希，经 datetime(0) 往返后必然对不上")
    void rootCause_rawMillisecondHashingCannotSurviveDatetimeTruncation() {
        // 固定一个带毫秒的时刻，使本测试与真实时钟无关（不依赖"采样到非整秒"）。
        Date signedWithRawMillis = new Date(1_755_000_000_123L);
        Date storedInDb = readBackFromDbAsMysqlDatetime(signedWithRawMillis);

        assertEquals(123L, signedWithRawMillis.getTime() % 1000L, "前置条件：入哈希的时刻带毫秒");
        assertEquals(0L, storedInDb.getTime() % 1000L, "前置条件：datetime(0) 落库后毫秒被截断");

        String hashWithRawMillis = SignatureChainCalculator.compute(
            null, 1L, BIZ, 100L, ME, null, "审批通过", null, signedWithRawMillis.getTime());
        String hashWithStoredValue = SignatureChainCalculator.compute(
            null, 1L, BIZ, 100L, ME, null, "审批通过", null, storedInDb.getTime());

        assertNotEquals(hashWithRawMillis, hashWithStoredValue,
            "哈希把毫秒编了进去 —— 这正是线上「未篡改却验签失败」的根因");
        // 归零毫秒后，入哈希值与落库值完全一致，哈希才可复现
        assertEquals(hashWithStoredValue,
            SignatureChainCalculator.compute(
                null, 1L, BIZ, 100L, ME, null, "审批通过", null,
                readBackFromDbAsMysqlDatetime(new Date(1_755_000_000_555L)).getTime()),
            "归零毫秒后，不同毫秒的同秒时刻必须得到同一个链哈希");
    }
}
