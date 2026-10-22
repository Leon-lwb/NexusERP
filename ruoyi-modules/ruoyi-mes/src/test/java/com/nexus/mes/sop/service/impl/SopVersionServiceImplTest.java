package com.nexus.mes.sop.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.sop.domain.SopVersion;
import com.nexus.mes.sop.mapper.SopVersionMapper;
import com.nexus.mes.sop.service.ISopDocumentService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

/**
 * SOP 版本 Service 单元测试 — MES-SOP-04（值域外状态不再绕过删除守卫）。
 *
 * <p>核心断言（修复前必失败）：当选中版本的状态不是合法值域内的 CURRENT，而是种子数据里的
 * 非法值 {@code 'PUBLISHED'}（或任何未知值）时，删除必须被拒绝 —— 原守卫只查
 * {@code status='CURRENT'}，值域漂移即可绕过，导致已发布正文版本被删、主档悬空引用。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SopVersionServiceImplTest {

    @Mock
    private SopVersionMapper baseMapper;
    @InjectMocks
    private SopVersionServiceImpl service;

    @Test
    void deleteRejectsNonPendingVersionsRegardlessOfValueDrift() {
        // 非 PENDING 计数 > 0（含值域外 'PUBLISHED'）-> 拒绝
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteByIds(new Long[]{1L, 2L}));

        assertTrue(ex.getMessage().contains("PENDING"), ex.getMessage());
        verify(baseMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteAllowsPendingOnlyVersions() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(baseMapper.deleteByIds(anyList())).thenReturn(1);

        assertTrue(service.deleteByIds(new Long[]{9L}));
        verify(baseMapper).deleteByIds(anyList());
    }

    @Test
    void deleteReturnsFalseForEmptyOrNullIds() {
        assertFalse(service.deleteByIds(new Long[0]));
        assertFalse(service.deleteByIds(null));
        verifyNoInteractions(baseMapper);
    }

    @Test
    void versionStatusConstantsMatchLegalDomain() {
        assertEquals("PENDING", ISopDocumentService.VERSION_PENDING);
        assertEquals("CURRENT", ISopDocumentService.VERSION_CURRENT);
        assertEquals("HISTORICAL", ISopDocumentService.VERSION_HISTORICAL);
        // 'PUBLISHED' 是 sop_document 的状态值，不是 sop_version 的合法状态（MES-SOP-04 数据订正目标）
        assertNotEquals("PUBLISHED", ISopDocumentService.VERSION_CURRENT);
        SopVersion v = new SopVersion();
        v.setStatus("PUBLISHED");
        assertNotEquals(ISopDocumentService.VERSION_PENDING, v.getStatus());
    }

    @Test
    void selectBySopDelegatesToMapper() {
        when(baseMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.selectBySop(1L).isEmpty());
    }
}
