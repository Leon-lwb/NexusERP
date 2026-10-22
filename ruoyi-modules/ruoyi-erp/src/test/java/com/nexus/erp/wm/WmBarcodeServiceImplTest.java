package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.barcode.BarcodeSequenceProvider;
import com.nexus.erp.wm.domain.WmBarcode;
import com.nexus.erp.wm.domain.WmBarcodeConfig;
import com.nexus.erp.wm.mapper.WmBarcodeConfigMapper;
import com.nexus.erp.wm.mapper.WmBarcodeMapper;
import com.nexus.erp.wm.service.impl.WmBarcodeServiceImpl;
import com.nexus.erp.wm.trace.WmBarcodeIdentityGuard;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

/**
 * 条码生成：生成的条码实例必须携带 snId（问题①回归）
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmBarcodeServiceImplTest {

    @Mock
    private WmBarcodeMapper baseMapper;
    @Mock
    private WmBarcodeConfigMapper barcodeConfigMapper;
    @Mock
    private BarcodeGenerator barcodeGenerator;
    @Mock
    private BarcodeSequenceProvider barcodeSequenceProvider;

    @Mock
    private WmBarcodeIdentityGuard identities;

    @InjectMocks
    private WmBarcodeServiceImpl wmBarcodeService;

    /**
     * 预热 MyBatis-Plus Lambda 缓存。
     * <p>单一 Mockito 单元测试无 Spring 上下文，实体 mapper 是 mock，不会自动注册 TableInfo；
     * service 内部基于 {@code WmBarcodeConfig::getConfigCode} 等 lambda 构造查询，必须手动 initTableInfo
     * 预热相关实体，否则抛 "can not find lambda cache"（ExceptionInInitializerError）。</p>
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, WmBarcode.class);
        TableInfoHelper.initTableInfo(assistant, WmBarcodeConfig.class);
    }

    private WmBarcodeConfig config() {
        WmBarcodeConfig config = new WmBarcodeConfig();
        config.setConfigId(1L);
        config.setConfigCode("WM_RECEIVE_DEFAULT");
        config.setPrefix("RC");
        config.setDatePattern("yyyyMMdd");
        config.setSequenceLength(6);
        config.setWithCheckDigit(0L);
        config.setEnableFlag(1L);
        return config;
    }

    @Test
    void generateAndSaveByCode_persistsSnId() {
        when(barcodeConfigMapper.selectOne(any())).thenReturn(config());
        when(barcodeSequenceProvider.nextSequence("WM_RECEIVE_DEFAULT")).thenReturn(1L);
        when(barcodeGenerator.generate(any(BarcodeRule.class), any(Date.class), anyLong()))
                .thenReturn("RC20260816000001");
        when(baseMapper.insert(any(WmBarcode.class))).thenReturn(1);

        wmBarcodeService.generateAndSaveByCode(
                "WM_RECEIVE_DEFAULT", "RECEIVE_IN", 1L, null, 100L, 9L, 5L, 77L, new Date());

        ArgumentCaptor<WmBarcode> captor = ArgumentCaptor.forClass(WmBarcode.class);
        verify(baseMapper).insert(captor.capture());
        WmBarcode saved = captor.getValue();
        assertEquals(77L, saved.getSnId(), "条码实例必须落 snId");
        assertEquals(9L, saved.getBatchId());
        assertEquals(5L, saved.getStockId());
        assertEquals(100L, saved.getMaterialId());
        var locks = inOrder(identities, barcodeSequenceProvider, barcodeGenerator, baseMapper);
        locks.verify(identities).lockBinding(any(WmBarcode.class), eq(true));
        locks.verify(barcodeSequenceProvider).nextSequence("WM_RECEIVE_DEFAULT");
        locks.verify(barcodeGenerator).generate(any(BarcodeRule.class), any(Date.class), anyLong());
        locks.verify(identities).finishRegistration(any(WmBarcode.class));
        locks.verify(baseMapper).insert(any(WmBarcode.class));
    }
}
