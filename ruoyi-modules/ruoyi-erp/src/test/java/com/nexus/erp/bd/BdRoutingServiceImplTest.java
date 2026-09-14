package com.nexus.erp.bd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.bo.BdRoutingBo;
import com.nexus.erp.bd.domain.bo.BdRoutingStepBo;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.service.impl.BdRoutingServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工艺路线Service单元测试。
 * <p>覆盖本次改动：编码留空时按 ROUTING_CODE 规则生成、(编码+版本号) 唯一性校验、
 * 版本号缺省兜底、以及更新时版本号沿用库中现值（不被回退成 1.0）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class BdRoutingServiceImplTest {

    /** 规则 ROUTING_CODE 的期望产出形态（RT + yyyyMMdd + 3 位日循环流水号） */
    private static final String GENERATED_CODE = "RT20260914001";
    private static final String ROUTING_CODE_RULE = "ROUTING_CODE";

    @Mock
    private BdRoutingMapper baseMapper;
    @Mock
    private BdRoutingStepMapper stepMapper;
    @Mock
    private BdBomMapper bomMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private BdRoutingServiceImpl routingService;

    /** 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter)，纯 Mockito 下先注入真实转换器实现 */
    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        // 预热 MyBatis-Plus Lambda 缓存：查重与步骤保存都会解析 BdRouting/BdRoutingStep 的 lambda 列名
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdRouting.class);
        TableInfoHelper.initTableInfo(assistant, BdRoutingStep.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    /** 编码留空：按 ROUTING_CODE 规则生成，版本号缺省补齐 1.0，并汇总工序数与工时 */
    @Test
    void insertByBo_blankCode_generatesByRule() {
        BdRoutingBo bo = new BdRoutingBo();
        bo.setRoutingName("自行车装配路线");
        bo.setSteps(List.of(step(new BigDecimal("1.5000"), new BigDecimal("0.5000"))));

        when(autoCodeUtil.genSerialCode(ROUTING_CODE_RULE, null)).thenReturn(GENERATED_CODE);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(BdRouting.class))).thenReturn(1);

        assertEquals(1, routingService.insertByBo(bo));

        ArgumentCaptor<BdRouting> captor = ArgumentCaptor.forClass(BdRouting.class);
        verify(baseMapper).insert(captor.capture());
        BdRouting inserted = captor.getValue();
        assertEquals(GENERATED_CODE, inserted.getRoutingCode());
        assertEquals("1.0", inserted.getRoutingVersion());
        assertEquals(1, inserted.getTotalSteps().intValue());
        assertEquals(0, new BigDecimal("2.0000").compareTo(inserted.getTotalHours()));
    }

    /** 手工填写编码：不应触发规则生成 */
    @Test
    void insertByBo_explicitCode_skipsGeneration() {
        BdRoutingBo bo = new BdRoutingBo();
        bo.setRoutingCode("RT-BIKE-001");
        bo.setRoutingName("自行车装配路线");
        bo.setRoutingVersion("1.0");

        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(BdRouting.class))).thenReturn(1);

        routingService.insertByBo(bo);

        verify(autoCodeUtil, never()).genSerialCode(anyString(), any());
        ArgumentCaptor<BdRouting> captor = ArgumentCaptor.forClass(BdRouting.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("RT-BIKE-001", captor.getValue().getRoutingCode());
    }

    /** 编码+版本重复：抛出可读的业务异常且不落库（对应唯一索引 uk_routing_code_version） */
    @Test
    void insertByBo_duplicateCodeAndVersion_rejected() {
        BdRoutingBo bo = new BdRoutingBo();
        bo.setRoutingCode("RT-BIKE-001");
        bo.setRoutingName("自行车装配路线");
        bo.setRoutingVersion("1.0");

        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> routingService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("RT-BIKE-001"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1.0"), ex.getMessage());
        assertTrue(ex.getMessage().contains("已存在"), ex.getMessage());
        verify(baseMapper, never()).insert(any(BdRouting.class));
    }

    /** 编辑态编码留空：同样兜底生成（否则 updateById 忽略 null，会静默保留旧编码） */
    @Test
    void updateByBo_blankCode_regenerates() {
        BdRoutingBo bo = new BdRoutingBo();
        bo.setRoutingId(9L);
        bo.setRoutingName("自行车装配路线");

        BdRouting existing = existingRouting(9L, "RT-OLD-001", "2.0");
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(autoCodeUtil.genSerialCode(ROUTING_CODE_RULE, null)).thenReturn(GENERATED_CODE);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.updateById(any(BdRouting.class))).thenReturn(1);

        assertEquals(1, routingService.updateByBo(bo));

        ArgumentCaptor<BdRouting> captor = ArgumentCaptor.forClass(BdRouting.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(GENERATED_CODE, captor.getValue().getRoutingCode());
        // 版本号留空时沿用库中现值，不能被回退成 1.0
        assertEquals("2.0", captor.getValue().getRoutingVersion());
    }

    /** 修改不存在的工艺路线：直接报错，避免 updateById 影响 0 行却返回成功 */
    @Test
    void updateByBo_notFound_rejected() {
        BdRoutingBo bo = new BdRoutingBo();
        bo.setRoutingId(404L);
        bo.setRoutingName("不存在的路线");

        when(baseMapper.selectById(404L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> routingService.updateByBo(bo));
        assertEquals("工艺路线不存在", ex.getMessage());
        verify(baseMapper, never()).updateById(any(BdRouting.class));
    }

    private BdRouting existingRouting(Long routingId, String routingCode, String routingVersion) {
        BdRouting routing = new BdRouting();
        routing.setRoutingId(routingId);
        routing.setRoutingCode(routingCode);
        routing.setRoutingVersion(routingVersion);
        return routing;
    }

    private BdRoutingStepBo step(BigDecimal standardHours, BigDecimal setupHours) {
        BdRoutingStepBo step = new BdRoutingStepBo();
        step.setProcessId(1001L);
        step.setProcessCode("OP10");
        step.setSequenceNum(1);
        step.setStandardHours(standardHours);
        step.setSetupHours(setupHours);
        return step;
    }
}
