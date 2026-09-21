package com.nexus.common.sign.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 印章编辑校验测试（图片字段分组后的服务层兜底）
 * + P2a-1 水印预览图生成/降级/惰性补生成测试。
 *
 * <p>背景：{@code SealBo} 的图片三字段 {@code @NotNull} 已收敛到 {@code AddGroup}
 * （修改不再强制重传图片），故「提供了图片 ⇒ 必须同时给出宽高」这条配对规则
 * 改由 {@code validateSealBo} 兜底。</p>
 *
 * <p>预览图部分通过 mock {@link OssContentAccessor} 验证：<b>不触达真实对象存储</b>，
 * 但渲染走真实的 {@code SealPreviewRenderer}（写入的字节必须真的是 PNG）。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class EsSealServiceImplTest {

    private static final Long SEAL_ID = 88L;

    @Mock
    private EsSealMapper baseMapper;
    @Mock
    private EsSealGrantMapper sealGrantMapper;
    @Mock
    private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @Mock
    private OssContentAccessor ossAccessor;

    private EsSealServiceImpl service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    /** 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter)，纯 Mockito 下先注入真实转换器子类 */
    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initMapstruct() {
        springUtilMock = mockStatic(SpringUtil.class);
        // Converter 为实体类：只覆写单对象转换（BeanUtil 属性拷贝），其余重载不触发
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);
    }

    @AfterAll
    static void closeMapstruct() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 在祖父类 CrudRepository 中声明，@InjectMocks 注入不到，
        // 这里用子类在构造时显式塞入（protected 字段，子类可赋值）
        service = new TestableEsSealServiceImpl(sealGrantMapper, ossAccessorProvider, baseMapper);

        // 名称唯一性校验会走 lambda 查询，预热 MyBatis-Plus 列缓存（与 ESignatureServiceImplTest 同法）
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SEALID", "SEALNAME", "SEALTYPE", "OWNERUSERID", "SEALOSSID",
            "PREVIEWOSSID", "SEALVERSION", "STATUS"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSeal.class)).thenReturn(cols);

        // 捕获 WARN（预览图失败必须留痕，不允许静默降级）
        serviceLogger = (Logger) LoggerFactory.getLogger(EsSealServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) {
            lambdaUtilsMock.close();
        }
        if (serviceLogger != null && logAppender != null) {
            serviceLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    /** 测试用子类：把 mock 的 baseMapper 注入到 ServiceImpl 继承来的 protected 字段。 */
    private static class TestableEsSealServiceImpl extends EsSealServiceImpl {

        TestableEsSealServiceImpl(EsSealGrantMapper sealGrantMapper,
                                  ObjectProvider<OssContentAccessor> ossContentAccessor,
                                  EsSealMapper baseMapper) {
            super(sealGrantMapper, ossContentAccessor);
            this.baseMapper = baseMapper;
        }
    }

    /** 已存在的启用态公司章。 */
    private static EsSeal existingCompanySeal() {
        EsSeal seal = new EsSeal();
        seal.setSealId(SEAL_ID);
        seal.setSealType(SealType.COMPANY);
        seal.setSealName("公司公章");
        seal.setSealOssId(1001L);
        seal.setImgWidth(200);
        seal.setImgHeight(200);
        seal.setStatus(SealStatus.ENABLED);
        seal.setSealVersion(1);
        return seal;
    }

    /** 编辑入参：不重传图片（前端 SealVo 不回传 sealOssId）。 */
    private static SealBo editBo() {
        SealBo bo = new SealBo();
        bo.setSealId(SEAL_ID);
        bo.setSealType(SealType.COMPANY);
        bo.setSealName("公司公章");
        return bo;
    }

    /** 新增入参（公司章 + 章面图片三字段齐全）。 */
    private static SealBo newSealBo() {
        SealBo bo = new SealBo();
        bo.setSealType(SealType.COMPANY);
        bo.setSealName("公司公章");
        bo.setSealOssId(1001L);
        bo.setImgWidth(600);
        bo.setImgHeight(400);
        return bo;
    }

    /** 生成一张真实 PNG 作为「印章原图」。 */
    private static byte[] sourceImage(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 20, 20));
        g.fillOval(w / 6, h / 6, w * 2 / 3, h * 2 / 3);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", bos));
        return bos.toByteArray();
    }

    /** 断言字节确实是 PNG 且最长边不超过上限。 */
    private static void assertIsDownscaledPng(byte[] bytes) throws Exception {
        assertNotNull(bytes);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(img, "写入对象存储的内容必须是可解码的图片");
        assertTrue(Math.max(img.getWidth(), img.getHeight()) <= 240,
            "预览图最长边应 <= 240px，实际 " + img.getWidth() + "x" + img.getHeight());
    }

    private List<ILoggingEvent> warns() {
        return logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    // ------------------------------------------------------------------
    // 原有：图片字段配对校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑：换了图片却没给宽高 -> ServiceException，且不落库")
    void updateByBo_replacingImageWithoutSize_rejected() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgHeight(300);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("图片宽度"), "提示语应指明缺少图片宽高：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("编辑：换了图片只给宽不给高 -> ServiceException")
    void updateByBo_replacingImageWithoutHeight_rejected() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgWidth(200);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("图片宽度"), "提示语应指明缺少图片宽高：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("编辑：不重传印章图片不会被尺寸配对校验拦下（继续走到名称唯一性校验）")
    void updateByBo_omittedImage_passesSizePairingGuard() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        // 名称重复：用它的报错证明「图片配对校验已通过、流程继续往下走」
        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateByBo(editBo()));
        assertTrue(ex.getMessage().contains("已存在"),
            "留空图片应通过尺寸配对校验，继续到名称唯一性校验：" + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // P2a-1：新增时生成水印预览图
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新增：读取原图 -> 渲染 -> 存储，previewOssId 落到实体，写入内容确实是降采样 PNG")
    void insertByBo_generatesWatermarkedPreview() throws Exception {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(1001L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString())).thenReturn(9001L);

        service.insertByBo(newSealBo());

        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).insert(entity.capture());
        assertEquals(9001L, entity.getValue().getPreviewOssId(),
            "新增时 previewOssId 必须被写入，否则前端只能拿到原图");

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> storedName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> storedType = ArgumentCaptor.forClass(String.class);
        verify(ossAccessor).store(stored.capture(), storedName.capture(), storedType.capture());
        assertIsDownscaledPng(stored.getValue());
        assertEquals("image/png", storedType.getValue(), "预览图应声明为 PNG");
        assertTrue(storedName.getValue().endsWith(".png"),
            "文件名后缀决定 sys_oss 的 MIME：" + storedName.getValue());
    }

    @Test
    @DisplayName("新增：写盘失败 -> 印章照常保存，previewOssId 为空并记 WARN")
    void insertByBo_storeFailure_stillSavesSealWithNullPreview() throws Exception {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(EsSeal.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, EsSeal.class).setSealId(SEAL_ID);
            return 1;
        });
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(1001L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString()))
            .thenThrow(new ServiceException("对象存储不可用"));

        Long sealId = service.insertByBo(newSealBo());

        assertEquals(SEAL_ID, sealId, "预览图失败绝不能阻断印章保存");
        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).insert(entity.capture());
        assertNull(entity.getValue().getPreviewOssId(), "生成失败应留空，由读取侧惰性补生成");
        assertTrue(warns().stream().anyMatch(e -> e.getFormattedMessage().contains("预览图")),
            "WARN 内容应指明预览图问题：" + warns());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("新增：未装配 SPI 实现（ObjectProvider 拿不到 bean）-> 不 NPE，印章照常保存并记 WARN")
    void insertByBo_missingSpiImplementation_degradesGracefully() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(ossAccessorProvider.getIfAvailable()).thenReturn(null);

        service.insertByBo(newSealBo());

        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).insert(entity.capture());
        assertNull(entity.getValue().getPreviewOssId());
        assertTrue(warns().stream().anyMatch(e -> e.getFormattedMessage().contains("OssContentAccessor")),
            "缺实现必须留下可诊断的 WARN：" + warns());
    }

    // ------------------------------------------------------------------
    // P2a-1：更换章面时重建预览图
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑换章面：版本号 +1，同时按新原图重建预览图")
    void updateByBo_imageChanged_rebuildsPreviewAndBumpsVersion() throws Exception {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(2002L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString())).thenReturn(9002L);

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgWidth(600);
        bo.setImgHeight(400);
        service.updateByBo(bo);

        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).updateById(entity.capture());
        assertEquals(2002L, entity.getValue().getSealOssId());
        assertEquals(2, entity.getValue().getSealVersion(), "换章面必须自增版本号（P1 既有行为）");
        assertEquals(9002L, entity.getValue().getPreviewOssId(), "换章面必须重建预览图，否则预览还是旧章");
    }

    @Test
    @DisplayName("编辑换章面：预览生成失败 -> 显式清空预览列，绝不留下上一版章面的预览（错章）")
    void updateByBo_imageChangedButPreviewFailed_clearsStalePreview() throws Exception {
        EsSeal existing = existingCompanySeal();
        existing.setPreviewOssId(8888L);
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existing);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(2002L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString()))
            .thenThrow(new ServiceException("对象存储不可用"));

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgWidth(600);
        bo.setImgHeight(400);
        service.updateByBo(bo);

        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).updateById(entity.capture());
        assertEquals(2, entity.getValue().getSealVersion());
        assertNull(entity.getValue().getPreviewOssId());
        // updateById 跳过 null 字段，必须补一条显式清空语句
        verify(baseMapper).update(isNull(), any());
        assertTrue(warns().stream().anyMatch(e -> e.getFormattedMessage().contains("预览图")),
            "必须记 WARN：" + warns());
    }

    @Test
    @DisplayName("编辑不换章面：不重建预览图，也不碰 previewOssId")
    void updateByBo_imageUnchanged_keepsPreview() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.updateByBo(editBo());

        ArgumentCaptor<EsSeal> entity = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).updateById(entity.capture());
        assertNull(entity.getValue().getPreviewOssId(), "未换章面时应留 null，交给 updateById 跳过该列");
        verify(ossAccessor, never()).read(any());
        verify(ossAccessor, never()).store(any(), anyString(), anyString());
        verify(baseMapper, never()).update(isNull(), any());
    }

    // ------------------------------------------------------------------
    // P2a-1：预览读取（惰性补生成，绝不回退原图）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("读取预览：已有 previewOssId -> 直接返回，不读原图不生成")
    void loadPreviewImage_previewExists_returnsIt() {
        EsSeal seal = existingCompanySeal();
        seal.setPreviewOssId(7777L);
        when(baseMapper.selectById(SEAL_ID)).thenReturn(seal);

        assertEquals(7777L, service.loadPreviewImage(SEAL_ID));
        verify(ossAccessor, never()).read(any());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("读取预览：历史数据无 previewOssId -> 惰性补生成并回写，返回的是预览图而非原图")
    void loadPreviewImage_legacyRow_generatesOnDemand() throws Exception {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(1001L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString())).thenReturn(9001L);

        Long previewOssId = service.loadPreviewImage(SEAL_ID);

        assertEquals(9001L, previewOssId);
        assertNotEquals(1001L, previewOssId, "绝不能把原图 ossId 当预览图下发");
        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(ossAccessor).store(stored.capture(), anyString(), anyString());
        assertIsDownscaledPng(stored.getValue());
        // 回写：下次直接命中
        ArgumentCaptor<EsSeal> patch = ArgumentCaptor.forClass(EsSeal.class);
        verify(baseMapper).updateById(patch.capture());
        assertEquals(9001L, patch.getValue().getPreviewOssId());
        assertEquals(SEAL_ID, patch.getValue().getSealId());
    }

    @Test
    @DisplayName("读取预览：历史数据且补生成失败 -> 抛异常，绝不下发原图")
    void loadPreviewImage_legacyRowGenerationFails_throwsInsteadOfLeakingOriginal() throws Exception {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(1001L)).thenReturn(sourceImage(600, 400));
        when(ossAccessor.store(any(byte[].class), anyString(), anyString()))
            .thenThrow(new ServiceException("对象存储不可用"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.loadPreviewImage(SEAL_ID));

        assertTrue(ex.getMessage().contains("预览图"), "错误信息应指明预览图不可用：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("读取预览：原图读取不到（对象被删）-> 抛异常，不下发任何 ossId")
    void loadPreviewImage_originalUnreadable_throws() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossAccessor);
        when(ossAccessor.read(1001L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.loadPreviewImage(SEAL_ID));
    }

    @Test
    @DisplayName("读取预览：印章不存在 -> ServiceException")
    void loadPreviewImage_sealMissing_throws() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.loadPreviewImage(SEAL_ID));
        assertTrue(ex.getMessage().contains("印章不存在"));
    }
}
