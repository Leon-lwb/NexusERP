package com.nexus.system.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.system.domain.*;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.mapper.SysOssMapper;
import com.nexus.system.service.impl.SysOssServiceImpl;

import org.junit.jupiter.api.Test;

import java.util.List;

class SysEngineeringOssBoundaryTest {
    @Test
    void genericIdDownloadAndDeleteCannotBypassTaskOwnership() throws Exception {
        var mapper = mock(SysOssMapper.class);
        var service = new SysOssServiceImpl(mapper);
        var vo = new SysOssVo();
        vo.setOssId(11L);
        vo.setFileName("engineering/000001/7/8/11");
        vo.setUrl("private-object");
        when(mapper.selectVoById(11L)).thenReturn(vo);
        assertNull(service.getById(11L));
        var entity = new SysOss();
        entity.setOssId(11L);
        entity.setFileName(vo.getFileName());
        when(mapper.selectByIds(List.of(11L))).thenReturn(List.of(entity));
        assertThrows(
                com.nexus.common.core.exception.ServiceException.class,
                () -> service.deleteWithValidByIds(List.of(11L), true));
        verify(mapper, never()).deleteByIds(anyCollection());
    }

    @Test
    void genericBatchUrlsDownloadAndPageExcludeEngineeringObjects() throws Exception {
        var mapper = mock(SysOssMapper.class);
        var service = new SysOssServiceImpl(mapper);
        var vo = new SysOssVo();
        vo.setOssId(11L);
        vo.setFileName("engineering/000001/7/8/11");
        vo.setUrl("private-object");
        when(mapper.selectVoById(11L)).thenReturn(vo);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysOssVo>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);
        doReturn(page).when(mapper).selectVoPage(any(), any());
        try (var spring = mockStatic(com.nexus.common.core.utils.SpringUtils.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getAopProxy(service))
                    .thenReturn(service);
            assertTrue(service.listByIds(List.of(11L)).isEmpty());
            assertEquals("", service.selectUrlByIds("11"));
            assertTrue(service.selectByIds("11").isEmpty());
            var response = mock(jakarta.servlet.http.HttpServletResponse.class);
            assertThrows(
                    com.nexus.common.core.exception.ServiceException.class,
                    () -> service.download(11L, response));
            verify(response, never()).getOutputStream();
            assertTrue(
                    service.queryPageList(
                                    new com.nexus.system.domain.bo.SysOssBo(),
                                    new com.nexus.common.mybatis.core.page.PageQuery(10, 1))
                            .getRows()
                            .isEmpty());
        }
        var ordinary = new SysOssVo();
        ordinary.setOssId(12L);
        ordinary.setFileName("normal/file.png");
        when(mapper.selectVoById(12L)).thenReturn(ordinary);
        assertSame(ordinary, service.getById(12L));
    }
}
