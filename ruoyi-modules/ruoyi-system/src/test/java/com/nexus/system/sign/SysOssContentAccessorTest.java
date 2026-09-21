package com.nexus.system.sign;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SysOssContentAccessor} 单元测试：mock {@code ISysOssService}，不触达真实对象存储。
 *
 * <p>核心契约：<b>read 永不抛异常</b>（对象不存在/远端缺失/读取失败一律 {@code null}），
 * <b>store 落库并返回新 ossId</b>，且临时文件必须清理。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class SysOssContentAccessorTest {

    private static final Long OSS_ID = 1001L;
    private static final String CONFIG_KEY = "minio";
    private static final String OBJECT_KEY = "2026/08/30/seal_abc.png";
    private static final byte[] CONTENT = "原始印章图片字节".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ISysOssService sysOssService;
    @Mock
    private OssClient ossClient;

    private MockedStatic<OssFactory> ossFactoryMock;

    private OssContentAccessor accessor;

    @BeforeEach
    void setUp() {
        accessor = new SysOssContentAccessor(sysOssService);
    }

    @AfterEach
    void tearDown() {
        if (ossFactoryMock != null) {
            ossFactoryMock.close();
        }
    }

    /** 桩：ossId 命中一条 sys_oss 记录，且其服务的 OssClient 已就绪。 */
    private void givenStoredObject() throws IOException {
        SysOssVo vo = new SysOssVo();
        vo.setOssId(OSS_ID);
        vo.setFileName(OBJECT_KEY);
        vo.setService(CONFIG_KEY);
        when(sysOssService.getById(OSS_ID)).thenReturn(vo);

        ossFactoryMock = mockStatic(OssFactory.class);
        ossFactoryMock.when(() -> OssFactory.instance(CONFIG_KEY)).thenReturn(ossClient);
        when(ossClient.getObjectContent(OBJECT_KEY)).thenReturn(new ByteArrayInputStream(CONTENT));
    }

    private static SysOssVo uploaded(Long ossId) {
        SysOssVo vo = new SysOssVo();
        vo.setOssId(ossId);
        return vo;
    }

    @Test
    @DisplayName("read：对象存在 -> 返回原始字节")
    void read_presentObject_returnsBytes() throws Exception {
        givenStoredObject();

        byte[] actual = accessor.read(OSS_ID);

        assertArrayEquals(CONTENT, actual);
        verify(ossClient, times(1)).getObjectContent(OBJECT_KEY);
    }

    @Test
    @DisplayName("read：ossId 未知（sys_oss 无记录）-> 返回 null 且不抛异常")
    void read_unknownOssId_returnsNull() {
        when(sysOssService.getById(OSS_ID)).thenReturn(null);

        assertNull(accessor.read(OSS_ID), "记录不存在应视为「对象不存在」，返回 null");
    }

    @Test
    @DisplayName("read：ossId 为 null -> 返回 null 且不查库")
    void read_nullOssId_returnsNull() {
        assertNull(accessor.read(null));
        verifyNoInteractions(sysOssService);
    }

    @Test
    @DisplayName("read：取流失败（OSS 不可达/对象被删）-> 返回 null 且不抛异常")
    void read_remoteFailure_returnsNull() throws Exception {
        SysOssVo vo = new SysOssVo();
        vo.setOssId(OSS_ID);
        vo.setFileName(OBJECT_KEY);
        vo.setService(CONFIG_KEY);
        when(sysOssService.getById(OSS_ID)).thenReturn(vo);
        ossFactoryMock = mockStatic(OssFactory.class);
        ossFactoryMock.when(() -> OssFactory.instance(CONFIG_KEY)).thenReturn(ossClient);
        when(ossClient.getObjectContent(OBJECT_KEY)).thenThrow(new IOException("NoSuchKey"));

        assertNull(accessor.read(OSS_ID), "远端读取失败必须降级为 null");
    }

    @Test
    @DisplayName("read：查询本身抛业务异常 -> 返回 null 且不抛异常")
    void read_queryThrows_returnsNull() {
        when(sysOssService.getById(OSS_ID)).thenThrow(new ServiceException("数据库忙"));

        assertNull(accessor.read(OSS_ID));
    }

    @Test
    @DisplayName("store：写入成功 -> 返回新的 ossId")
    void store_returnsNewOssId() {
        when(sysOssService.upload(any(File.class))).thenReturn(uploaded(4242L));

        Long ossId = accessor.store(CONTENT, "seal-preview-1001.png", "image/png");

        assertEquals(4242L, ossId);
        verify(sysOssService, times(1)).upload(any(File.class));
    }

    @Test
    @DisplayName("store：内容按原样写入临时文件，且临时文件在返回后被清理")
    void store_writesContentAndCleansTempFile() throws Exception {
        AtomicReference<File> seen = new AtomicReference<>();
        AtomicReference<byte[]> seenContent = new AtomicReference<>();
        when(sysOssService.upload(any(File.class))).thenAnswer(invocation -> {
            File temp = invocation.getArgument(0);
            seen.set(temp);
            assertTrue(temp.exists(), "上传开始前临时文件必须存在");
            seenContent.set(Files.readAllBytes(temp.toPath()));
            return uploaded(777L);
        });

        Long ossId = accessor.store(CONTENT, "seal-preview-1001.png", "image/png");

        assertEquals(777L, ossId);
        assertArrayEquals(CONTENT, seenContent.get(), "写入对象存储的内容必须与原字节一致");
        assertNotNull(seen.get());
        assertFalse(seen.get().exists(), "临时文件必须在 finally 中清理");
    }

    @Test
    @DisplayName("store：临时文件名保留后缀（否则对象存储无法推导 MIME）")
    void store_tempFileKeepsSuffix() {
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        when(sysOssService.upload(any(File.class))).thenReturn(uploaded(1L));

        accessor.store(CONTENT, "seal-preview-1001.png", "image/png");

        verify(sysOssService).upload(captor.capture());
        assertTrue(captor.getValue().getName().endsWith(".png"),
            "临时文件后缀决定 sys_oss 的 file_suffix/MIME：" + captor.getValue().getName());
    }

    @Test
    @DisplayName("store：fileName 带目录穿越时只取基名，避免影响临时文件名")
    void store_sanitizesFileName() {
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        when(sysOssService.upload(any(File.class))).thenReturn(uploaded(1L));

        accessor.store(CONTENT, "../../etc/passwd.png", "image/png");

        verify(sysOssService).upload(captor.capture());
        String name = captor.getValue().getName();
        assertTrue(name.endsWith(".png"), name);
        assertFalse(name.contains(".."), "临时文件名不得包含路径穿越字符：" + name);
        assertFalse(name.contains("/"), name);
        assertFalse(name.contains("\\"), name);
    }

    @Test
    @DisplayName("store：空内容 -> 直接拒绝，不产生任何对象存储记录")
    void store_emptyContent_rejected() {
        assertThrows(ServiceException.class, () -> accessor.store(null, "a.png", "image/png"));
        assertThrows(ServiceException.class, () -> accessor.store(new byte[0], "a.png", "image/png"));
        verify(sysOssService, never()).upload(any(File.class));
    }

    @Test
    @DisplayName("store：上传失败 -> 抛出异常（由调用方决定是否降级），临时文件仍被清理")
    void store_uploadFailure_propagatesAndCleansTempFile() {
        AtomicReference<File> seen = new AtomicReference<>();
        when(sysOssService.upload(any(File.class))).thenAnswer(invocation -> {
            seen.set(invocation.getArgument(0));
            throw new ServiceException("对象存储不可用");
        });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> accessor.store(CONTENT, "seal-preview-1001.png", "image/png"));

        assertEquals("对象存储不可用", ex.getMessage());
        assertNotNull(seen.get());
        assertFalse(seen.get().exists(), "上传失败路径同样必须清理临时文件");
    }
}
