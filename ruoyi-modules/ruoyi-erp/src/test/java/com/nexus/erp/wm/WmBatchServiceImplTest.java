package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.service.WmBatchWriteGuard;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import com.nexus.erp.wm.service.impl.WmBatchServiceImpl;
import com.nexus.erp.wm.trace.ErpTraceContext;

import io.github.linpeilie.Converter;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

/**
 * 批次管理Service单元测试（重写：旧版反射已不存在的静态计数器字段，随并发修复整体替换）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmBatchServiceImplTest {

    @Mock
    private WmBatchMapper baseMapper;
    @Mock
    private DailyCodeSequenceProvider dailyCodeSequenceProvider;

    @Mock
    private WmMaterialReceiveLineMapper materialReceiveLineMapper;
    @Mock
    private ErpTraceContext traceContext;

    private WmBatchServiceImpl wmBatchService;

    /**
     * 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter)，纯 Mockito 下先注入真实转换器子类
     */
    private static MockedStatic<SpringUtil> springUtilMock;

    private static Converter converter;

    @BeforeAll
    static void initMapstruct() {
        springUtilMock = mockStatic(SpringUtil.class);
        // Converter 为实体类：只覆写单对象转换（BeanUtil 属性拷贝），其余重载不触发
        converter =
                new Converter() {
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

    /**
     * 预热 MyBatis-Plus Lambda 缓存。
     * <p>单一 Mockito 单元测试无 Spring 上下文，校验批次编码需解析 WmBatch::getBatchCode 等 lambda 列名。</p>
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, WmBatch.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(dailyCodeSequenceProvider.next(anyString(), any())).thenReturn(6L);
        lenient().when(traceContext.tenant()).thenReturn("000001");
        wmBatchService =
                new WmBatchServiceImpl(
                        baseMapper,
                        dailyCodeSequenceProvider,
                        materialReceiveLineMapper,
                        new WmBatchWriteGuard(baseMapper, traceContext));
    }

    /**
     * createBatch：自动生成批次编码（provider 提供序号），格式 B{yyyyMMdd}{4位} 且默认值补齐
     */
    @Test
    void testCreateBatch_AutoCodeAndDefaults() {
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(100L);
        bo.setMaterialCode("MAT001");

        when(baseMapper.insert(any(WmBatch.class))).thenReturn(1);
        WmBatchVo vo = new WmBatchVo();
        vo.setBatchId(1L);
        vo.setBatchCode("B202608160006");
        vo.setMaterialId(100L);
        vo.setStatus("ACTIVE");
        // createBatch 在 insert 后按 entity.getBatchId() 回查（Mockito 下为 null），须用 any() 匹配
        when(baseMapper.selectVoById(any())).thenReturn(vo);

        WmBatchVo result = wmBatchService.createBatch(bo);

        assertEquals("B202608160006", result.getBatchCode());
        assertEquals("ACTIVE", result.getStatus());
        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).insert(captor.capture());
        WmBatch inserted = captor.getValue();
        assertTrue(inserted.getBatchCode().startsWith("B"));
        assertTrue(inserted.getBatchCode().matches("B\\d{8}\\d{4}"));
        assertEquals(0, inserted.getReceivedQty().compareTo(BigDecimal.ZERO));
        assertEquals(0, inserted.getIssuedQty().compareTo(BigDecimal.ZERO));
    }

    /**
     * insertByBo：批次编码为空时自动生成，并补齐默认状态与数量（问题⑦回归）
     */
    @Test
    void testInsertByBo_AutoCodeAndDefaults() {
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(100L);
        bo.setMaterialCode("MAT001");
        when(baseMapper.insert(any(WmBatch.class))).thenReturn(1);

        wmBatchService.insertByBo(bo);

        ArgumentCaptor<WmBatch> captor = ArgumentCaptor.forClass(WmBatch.class);
        verify(baseMapper).insert(captor.capture());
        WmBatch inserted = captor.getValue();
        assertTrue(inserted.getBatchCode().matches("B\\d{8}\\d{4}"));
        assertEquals("ACTIVE", inserted.getStatus());
        assertEquals(0, inserted.getQuantity().compareTo(BigDecimal.ZERO));
    }

    /**
     * insertByBo：物料ID为空应报错
     */
    @Test
    void testInsertByBo_RequiresMaterial() {
        WmBatchBo bo = new WmBatchBo();
        ServiceException exception =
                assertThrows(ServiceException.class, () -> wmBatchService.insertByBo(bo));
        assertTrue(exception.getMessage().contains("物料ID不能为空"));
    }

    /**
     * 旧URL对应服务不再允许无来源累计，正数/负数均不能绕过单据。
     */
    @Test
    void testUpdateQuantity_RequiresSourceDocument() {
        ServiceException exception =
                assertThrows(
                        ServiceException.class,
                        () -> wmBatchService.updateQuantity(1L, new BigDecimal("20"), true));
        assertTrue(exception.getMessage().contains("来源单据"));
        verifyNoInteractions(baseMapper);
    }

    @Test
    void testUpdateQuantity_MissingBatchCannotBecomeAnUnguardedAdjustment() {
        assertThrows(
                ServiceException.class,
                () -> wmBatchService.updateQuantity(999L, BigDecimal.ONE, false));
        verifyNoInteractions(baseMapper);
    }

    /**
     * 过期任务只写状态，不把候选实体中的累计/QC快照写回。
     */
    @Test
    void testCheckExpiry() {
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        wmBatchService.checkExpiry();
        ArgumentCaptor<LambdaUpdateWrapper<WmBatch>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        String fields = captor.getValue().getSqlSet();
        assertTrue(fields.contains("status="));
        assertFalse(fields.contains("received_qty"));
        assertFalse(fields.contains("issued_qty"));
        assertFalse(fields.contains("qc_status"));
        verify(baseMapper, never()).updateById(any(WmBatch.class));
    }

    /**
     * 状态元数据采用锁定批次+局部更新，不能写回整实体。
     */
    @Test
    void testChangeStatus() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(batch("B202606010001"));
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        wmBatchService.changeStatus(1L, "CLOSED");
        ArgumentCaptor<LambdaUpdateWrapper<WmBatch>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("status="));
        assertFalse(captor.getValue().getSqlSet().contains("quantity"));
        verify(baseMapper, never()).updateById(any(WmBatch.class));
    }

    private WmBatch batch(String code) {
        WmBatch b = new WmBatch();
        b.setBatchId(1L);
        b.setBatchCode(code);
        b.setMaterialId(100L);
        b.setQuantity(new BigDecimal("100"));
        b.setReceivedQty(new BigDecimal("50"));
        b.setIssuedQty(new BigDecimal("10"));
        b.setStatus("ACTIVE");
        return b;
    }
}
