package com.nexus.erp.pu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import com.nexus.erp.pu.service.impl.PuContractSignStatusFiller;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 采购合同「正文签署状态」批量补充器单元测试（采购合同接入，复用 {@code SoContractSignStatusFillerTest} 同一模式）。
 *
 * <p>本测试守的是列表签署状态列的三条硬约束：
 * <ol>
 *   <li><b>无 N+1</b>：整页 N 条合同只允许一次 {@code selectList}，绝不按行回查；</li>
 *   <li><b>查询条件正确</b>：一次 IN 查询同时限定 {@code biz_type} / {@code biz_id} / {@code is_current}
 *       （只取当前生效版本，历史版本的状态不得覆盖当前正文）；</li>
 *   <li><b>空值不误导</b>：该合同尚无正文时保持 {@code null}（前端显示占位符 '-'），
 *       而不是落成「未签」——也不能把上一轮的旧值留在 VO 上。</li>
 * </ol>
 * 数据源（{@code @DS("master")}，esign 表在 ry-vue 库）由 {@code SignDataSourceMasterTest} 守卫，本类不重复断言。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuContractSignStatusFillerTest {

    /** 列名断言用：一次 IN 查询命中的三列（表名 es_sign_document） */
    private static final String COL_BIZ_TYPE = "biz_type";
    private static final String COL_BIZ_ID = "biz_id";
    private static final String COL_IS_CURRENT = "is_current";

    @Mock
    private EsSignDocumentMapper signDocumentMapper;

    @InjectMocks
    private PuContractSignStatusFiller filler;

    /**
     * 预热 MyBatis-Plus Lambda 缓存（同 {@code SoContractSignStatusFillerTest}）。
     * <p>单一 Mockito 单元测试没有 Spring 上下文，实体的 mapper 是 mock、不会自动注册 {@code TableInfo}；
     * 而补充器用 {@code EsSignDocument::getBizId} 这类方法引用构造条件、断言又要读 {@code getSqlSegment()}，
     * 不预热会抛 {@code can not find lambda cache for this entity}。</p>
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EsSignDocument.class);
    }

    /**
     * 整页一次查询、逐行回填：3 条合同有 2 条有正文，缺失的那条保持 null。
     */
    @Test
    void fillDocSignStatus_queriesOnceForWholePageAndMapsStatusBack() {
        PuContractVo signed = vo(11L);
        PuContractVo unsigned = vo(22L);
        PuContractVo noDocument = vo(33L);
        when(signDocumentMapper.selectList(any())).thenReturn(List.of(doc(11L, "2"), doc(22L, "0")));

        filler.fillDocSignStatus(List.of(signed, unsigned, noDocument), SignBizType.PU_CONTRACT);

        // 核心：整页一条 IN 查询。按行回查（N+1）这里会是 3 次
        verify(signDocumentMapper, times(1)).selectList(any());
        verify(signDocumentMapper, never()).selectById(any());
        verify(signDocumentMapper, never()).selectOne(any());
        verifyNoMoreInteractions(signDocumentMapper);

        assertEquals("2", signed.getDocSignStatus());
        assertEquals("0", unsigned.getDocSignStatus());
        assertNull(noDocument.getDocSignStatus(), "该合同尚无正文，签署状态必须为 null 以让前端显示占位符");
    }

    /**
     * 分页越大也仍然只有一次查询：25 条合同不允许退化成 25 次回查。
     */
    @Test
    void fillDocSignStatus_singleQueryNoMatterHowBigThePage() {
        List<PuContractVo> page = new ArrayList<>();
        List<EsSignDocument> docs = new ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            page.add(vo(i));
            docs.add(doc(i, i % 2 == 0 ? "2" : "0"));
        }
        when(signDocumentMapper.selectList(any())).thenReturn(docs);

        filler.fillDocSignStatus(page, SignBizType.PU_CONTRACT);

        verify(signDocumentMapper, times(1)).selectList(any());
        verifyNoMoreInteractions(signDocumentMapper);
        assertEquals("2", page.get(1).getDocSignStatus());
        assertEquals("0", page.get(2).getDocSignStatus());
    }

    /**
     * 查询条件：一次 IN 查询必须同时限定业务类型、整页业务ID、以及「当前生效版本」。
     */
    @Test
    void fillDocSignStatus_restrictsToBizTypePageIdsAndCurrentVersion() {
        when(signDocumentMapper.selectList(any())).thenReturn(Collections.emptyList());

        filler.fillDocSignStatus(List.of(vo(11L), vo(22L)), SignBizType.PU_CONTRACT);

        String sql = captureQuerySql();
        assertTrue(sql.contains(COL_BIZ_TYPE), "查询条件必须限定 biz_type，实际: " + sql);
        assertTrue(sql.contains(COL_BIZ_ID), "查询条件必须按 biz_id 过滤（整页 ID 一次性进 IN），实际: " + sql);
        assertTrue(sql.contains(COL_IS_CURRENT),
            "查询条件必须限定 is_current=1（只取当前生效版本，历史版本的状态不得覆盖当前正文），实际: " + sql);
    }

    /**
     * 旧值不残留：VO 上已有的签署状态一律以本次查询结果为准，无正文的必须被清成 null。
     */
    @Test
    void fillDocSignStatus_overwritesStaleValueAndClearsContractWithoutDocument() {
        PuContractVo signed = vo(11L);
        signed.setDocSignStatus("2");
        PuContractVo noDocument = vo(22L);
        noDocument.setDocSignStatus("2");
        when(signDocumentMapper.selectList(any())).thenReturn(List.of(doc(11L, "0")));

        filler.fillDocSignStatus(List.of(signed, noDocument), SignBizType.PU_CONTRACT);

        assertEquals("0", signed.getDocSignStatus());
        assertNull(noDocument.getDocSignStatus(), "无正文的合同不得保留上一轮的旧签署状态");
    }

    /**
     * 无东西可查时不查库：空集合 / null / 整页没有业务ID —— 别为空 IN 白跑一趟。
     */
    @Test
    void fillDocSignStatus_skipsQueryWhenPageHasNothingToLookUp() {
        filler.fillDocSignStatus(Collections.emptyList(), SignBizType.PU_CONTRACT);
        filler.fillDocSignStatus(null, SignBizType.PU_CONTRACT);
        filler.fillDocSignStatus(List.of(vo(null), vo(null)), SignBizType.PU_CONTRACT);

        verifyNoInteractions(signDocumentMapper);
    }

    /**
     * 业务类型为空/空白时不查库（空 bizType 会命中全部业务类型的文档）。
     */
    @Test
    void fillDocSignStatus_skipsQueryWhenBizTypeBlank() {
        filler.fillDocSignStatus(List.of(vo(11L)), "  ");
        filler.fillDocSignStatus(List.of(vo(11L)), null);

        verifyNoInteractions(signDocumentMapper);
    }

    /**
     * 捕获本次查询并返回渲染后的 SQL 条件片段（含列名）。
     *
     * @return 查询条件片段，如 {@code (biz_type = ? AND biz_id IN (?,?) AND is_current = ?)}
     */
    @SuppressWarnings("unchecked")
    private String captureQuerySql() {
        ArgumentCaptor<Wrapper<EsSignDocument>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(signDocumentMapper).selectList(captor.capture());
        return captor.getValue().getSqlSegment();
    }

    private static PuContractVo vo(Long contractId) {
        PuContractVo vo = new PuContractVo();
        vo.setContractId(contractId);
        return vo;
    }

    private static EsSignDocument doc(Long bizId, String signStatus) {
        EsSignDocument doc = new EsSignDocument();
        doc.setBizId(bizId);
        doc.setSignStatus(signStatus);
        return doc;
    }
}
