package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.lock.LockTemplate;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 验签测试。
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ESignatureVerifyTest {

    @Mock private EsSignatureMapper signatureMapper;
    @Mock private EsSealMapper sealMapper;
    @Mock private EsSealGrantMapper grantMapper;
    @Mock private SignAuthVerifier authVerifier;
    @Mock private LockTemplate lockTemplate;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        service = new ESignatureServiceImpl(signatureMapper, sealMapper, grantMapper, authVerifier, lockTemplate);
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SIGNATUREID", "BIZTYPE", "BIZID", "SIGNERUSERID", "SEALID",
            "SIGNATUREMEANING", "AUTHMETHOD", "DOCHASH", "SIGNEDAT", "PREVHASH", "CHAINHASH"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignature.class)).thenReturn(cols);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    /** 构造一条自洽的记录（chainHash 已按算法算好） */
    private EsSignature consistent(Long id, String prevHash, String meaning) {
        return consistent(id, prevHash, meaning, SignBizType.APPROVAL, 100L);
    }

    private EsSignature consistent(Long id, String prevHash, String meaning, String bizType, Long bizId) {
        EsSignature s = new EsSignature();
        s.setSignatureId(id);
        s.setBizType(bizType);
        s.setBizId(bizId);
        s.setSignerUserId(7L);
        s.setSealId(null);
        s.setSignatureMeaning(meaning);
        s.setAuthMethod(SignAuthMethod.LOGIN_ONLY);
        s.setSignedAt(new Date(1_755_000_000_000L + id));
        s.setPrevHash(prevHash);
        s.setChainHash(SignatureChainCalculator.compute(
            prevHash, id, s.getBizType(), s.getBizId(), s.getSignerUserId(),
            s.getSealId(), meaning, s.getDocHash(), s.getSignedAt().getTime()));
        return s;
    }

    @Test
    @DisplayName("verify: 未被篡改的记录 -> 通过")
    void verify_intact_passes() {
        EsSignature s = consistent(1L, null, "审批通过");
        when(signatureMapper.selectById(1L)).thenReturn(s);
        when(signatureMapper.selectOne(any())).thenReturn(null);

        VerifyResultVo r = service.verify(1L);

        assertTrue(r.getPassed());
        assertTrue(r.getChainPassed());
        assertNull(r.getBrokenAt());
        assertEquals(1, r.getCheckedCount());
    }

    @Test
    @DisplayName("verify: 签署含义被篡改 -> 检出失败并定位")
    void verify_tamperedMeaning_detected() {
        EsSignature s = consistent(1L, null, "审批通过");
        s.setSignatureMeaning("审批驳回"); // 篡改，chainHash 未重算
        when(signatureMapper.selectById(1L)).thenReturn(s);

        VerifyResultVo r = service.verify(1L);

        assertFalse(r.getPassed());
        assertEquals(1L, r.getBrokenAt());
        assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("篡改") || m.contains("不一致")));
    }

    @Test
    @DisplayName("verify: 签署时间被篡改 -> 检出失败")
    void verify_tamperedTime_detected() {
        EsSignature s = consistent(1L, null, "审批通过");
        s.setSignedAt(new Date(s.getSignedAt().getTime() + 60_000L));
        when(signatureMapper.selectById(1L)).thenReturn(s);

        assertFalse(service.verify(1L).getPassed());
    }

    @Test
    @DisplayName("verify: prevHash 与真实前驱 chainHash 不匹配 -> 检出断链")
    void verify_brokenLink_detected() {
        EsSignature prev = consistent(1L, null, "审批通过");
        EsSignature cur = consistent(2L, "b".repeat(64), "复核通过"); // prevHash 乱填
        when(signatureMapper.selectById(2L)).thenReturn(cur);
        when(signatureMapper.selectOne(any())).thenReturn(prev);

        VerifyResultVo r = service.verify(2L);

        assertFalse(r.getPassed());
        assertEquals(2L, r.getBrokenAt());
    }

    @Test
    @DisplayName("verify: 记录不存在 -> 抛 ServiceException")
    void verify_notFound_throws() {
        when(signatureMapper.selectById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.verify(999L));
    }

    @Test
    @DisplayName("verifyChain: 三条自洽记录 -> 全通过")
    void verifyChain_allConsistent_passes() {
        EsSignature a = consistent(1L, null, "拟稿");
        EsSignature b = consistent(2L, a.getChainHash(), "复核");
        EsSignature c = consistent(3L, b.getChainHash(), "批准");
        when(signatureMapper.selectList(any())).thenReturn(List.of(a, b, c));
        when(signatureMapper.selectById(1L)).thenReturn(a);
        when(signatureMapper.selectById(2L)).thenReturn(b);
        when(signatureMapper.selectById(3L)).thenReturn(c);
        when(signatureMapper.selectOne(any())).thenReturn(null, a, b);

        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);

        assertTrue(r.getPassed());
        assertEquals(3, r.getCheckedCount());
        assertNull(r.getBrokenAt());
    }

    @Test
    @DisplayName("verifyChain: 中间一条被篡改 -> 在该条断链")
    void verifyChain_middleTampered_breaksThere() {
        EsSignature a = consistent(1L, null, "拟稿");
        EsSignature b = consistent(2L, a.getChainHash(), "复核");
        EsSignature c = consistent(3L, b.getChainHash(), "批准");
        b.setSignatureMeaning("被改了"); // 篡改 b
        when(signatureMapper.selectList(any())).thenReturn(List.of(a, b, c));
        when(signatureMapper.selectById(1L)).thenReturn(a);
        when(signatureMapper.selectById(2L)).thenReturn(b);
        when(signatureMapper.selectById(3L)).thenReturn(c);

        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);

        assertFalse(r.getPassed());
        assertEquals(2L, r.getBrokenAt());
    }

    @Test
    @DisplayName("verifyChain: 无记录 -> 通过且条数为 0")
    void verifyChain_noRecords_passes() {
        when(signatureMapper.selectList(any())).thenReturn(List.of());
        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);
        assertTrue(r.getPassed());
        assertEquals(0, r.getCheckedCount());
    }

    @Test
    @DisplayName("verifyChain: 与其它业务穿插的记录 -> 不得误报断链（链是租户全局的）")
    void verifyChain_interleavedOtherBusiness_noFalseBreak() {
        // 租户全局链：A1 -> B1 -> A2 -> B2 -> A3
        EsSignature a1 = consistent(1L, null,          "拟稿", SignBizType.APPROVAL, 100L);
        EsSignature b1 = consistent(2L, a1.getChainHash(), "其它业务", SignBizType.SO_CONTRACT, 900L);
        EsSignature a2 = consistent(3L, b1.getChainHash(), "复核", SignBizType.APPROVAL, 100L);
        EsSignature b2 = consistent(4L, a2.getChainHash(), "其它业务", SignBizType.SO_CONTRACT, 900L);
        EsSignature a3 = consistent(5L, b2.getChainHash(), "批准", SignBizType.APPROVAL, 100L);

        // 业务范围内查询只返回 APPROVAL/100 的三条
        when(signatureMapper.selectList(any())).thenReturn(List.of(a1, a2, a3));
        when(signatureMapper.selectById(any())).thenAnswer(inv ->
            Map.of(1L, a1, 2L, b1, 3L, a2, 4L, b2, 5L, a3).get((Long) inv.getArgument(0)));
        // 逐条查真实前驱：A1 -> null, A2 -> B1, A3 -> B2
        when(signatureMapper.selectOne(any())).thenReturn(null, b1, b2);

        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);

        assertTrue(r.getPassed(), "业务记录与其它业务穿插时不应误报断链");
        assertEquals(3, r.getCheckedCount());
        assertNull(r.getBrokenAt());
    }
}
