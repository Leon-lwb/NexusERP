package com.nexus.mes.sop.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.domain.SopProcessBinding;
import com.nexus.mes.sop.domain.bo.SopDocumentBo;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopProcessBindingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * SOP 文档列表「默认排除已归档」与归档绑定守卫的单元测试。
 *
 * <p>两个被测行为都只是查询条件/前置校验，不走库：
 * ① {@code buildQueryWrapper} 在状态留空时 {@code ne(status,'ARCHIVED')}，状态显式给值时按 {@code eq} 精确匹配——
 * 这是「归档不进日常列表、但仍可用状态下拉筛出（追溯入口）」的落点；
 * ② {@code archive} 在仍被工序绑定时拒绝，防止归档后推送停用导致工位侧 SOP 无声消失。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SopDocumentServiceImplListTest {

    @Mock
    private SopDocumentMapper baseMapper;
    @Mock
    private SopProcessBindingMapper bindingMapper;

    @InjectMocks
    private SopDocumentServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 依赖实体的列缓存，纯单测环境需手动预热（与 LtLotServiceImplTest 同法）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SopDocument.class);
        TableInfoHelper.initTableInfo(assistant, SopProcessBinding.class);
    }

    @AfterAll
    static void cleanTableInfo() {
        TableInfoHelper.remove(SopDocument.class);
        TableInfoHelper.remove(SopProcessBinding.class);
    }

    @BeforeEach
    void setUp() {
        // InjectMocks 会按构造器注入全部 6 个依赖，未 mock 的两个传 null 即可（本测试不触碰）
        service = new SopDocumentServiceImpl(baseMapper, null, bindingMapper, null, null, null);
    }

    private SopDocumentBo boWithStatus(String status) {
        SopDocumentBo bo = new SopDocumentBo();
        bo.setStatus(status);
        return bo;
    }

    @Test
    @DisplayName("状态留空：默认排除已归档（ne ARCHIVED），归档 SOP 不进日常列表")
    void blankStatusExcludesArchived() {
        LambdaQueryWrapper<SopDocument> wrapper = service.buildQueryWrapper(boWithStatus(null));
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("<>"), "留空状态应以 <> 排除 ARCHIVED，实际: " + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ARCHIVED"), "排除值应为 ARCHIVED");
    }

    @Test
    @DisplayName("状态留空(空串)同样走默认排除")
    void emptyStringStatusAlsoExcludes() {
        LambdaQueryWrapper<SopDocument> wrapper = service.buildQueryWrapper(boWithStatus(""));
        assertTrue(wrapper.getSqlSegment().contains("<>"));
    }

    @Test
    @DisplayName("状态=ARCHIVED：按 eq 精确筛出（追溯入口，不能被默认排除吃掉）")
    void archivedFilterStillWorks() {
        LambdaQueryWrapper<SopDocument> wrapper = service.buildQueryWrapper(boWithStatus("ARCHIVED"));
        String sql = wrapper.getSqlSegment();
        assertFalse(sql.contains("<>"), "显式筛已归档时不得再附加 <> 排除，实际: " + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ARCHIVED"));
    }

    @Test
    @DisplayName("状态=PUBLISHED：与改造前逐字等价（既有选择器依赖此分支）")
    void publishedFilterUnchanged() {
        LambdaQueryWrapper<SopDocument> wrapper = service.buildQueryWrapper(boWithStatus("PUBLISHED"));
        String sql = wrapper.getSqlSegment();
        assertFalse(sql.contains("<>"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PUBLISHED"));
    }

    private SopDocument publishedDoc() {
        SopDocument doc = new SopDocument();
        doc.setSopId(1L);
        doc.setStatus("PUBLISHED");
        return doc;
    }

    @Test
    @DisplayName("归档守卫：仍被工序绑定时拒绝，提示先解绑（而非静默级联解绑）")
    void archiveBlockedWhileBound() {
        when(baseMapper.selectById(1L)).thenReturn(publishedDoc());
        when(bindingMapper.selectCount(any())).thenReturn(2L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.archive(1L, "废止"));
        assertTrue(ex.getMessage().contains("工序绑定"), "提示应指向解绑动作，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("非 PUBLISHED 状态归档：拒绝")
    void archiveRequiresPublished() {
        SopDocument draft = publishedDoc();
        draft.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(draft);
        assertThrows(ServiceException.class, () -> service.archive(1L, null));
    }

    @Test
    @DisplayName("无绑定时归档：放行")
    void archiveAllowedWhenUnbound() {
        when(baseMapper.selectById(1L)).thenReturn(publishedDoc());
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        assertDoesNotThrow(() -> service.archive(1L, "废止"));
    }
}
