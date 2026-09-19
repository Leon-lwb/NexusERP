package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.service.impl.SoCustomerOrderServiceImpl;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户订单 Service 单元测试（加固轮）。
 * <p>此前客户订单无任何测试覆盖。本类锁定两点：单号留空自动生成、
 * 以及「被销售订单引用时不允许删除」的引用守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoCustomerOrderServiceImplTest {

    @Mock
    private SoCustomerOrderMapper baseMapper;
    @Mock
    private SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private SoCustomerOrderServiceImpl customerOrderService;

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

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoCustomerOrder.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderHeader.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoCustomerOrder customerOrder(Long id, String coNo) {
        SoCustomerOrder co = new SoCustomerOrder();
        co.setCoId(id);
        co.setCoNo(coNo);
        co.setClientId(1L);
        co.setClientName("测试客户");
        return co;
    }

    /** 单号留空时按 SO_CO_NO 规则自动生成 */
    @Test
    void insertByBo_generatesCoNoWhenBlank() {
        SoCustomerOrderBo bo = new SoCustomerOrderBo();
        bo.setClientId(1L);
        when(autoCodeUtil.genSerialCode(eq("SO_CO_NO"), any())).thenReturn("KHDD20260919001");
        when(baseMapper.insert(any(SoCustomerOrder.class))).thenReturn(1);

        assertEquals(1, customerOrderService.insertByBo(bo));

        ArgumentCaptor<SoCustomerOrder> captor = ArgumentCaptor.forClass(SoCustomerOrder.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("KHDD20260919001", captor.getValue().getCoNo());
    }

    /** 客户端已给单号时不覆盖、也不调用编码规则 */
    @Test
    void insertByBo_keepsClientCoNo() {
        SoCustomerOrderBo bo = new SoCustomerOrderBo();
        bo.setCoNo("CO-MANUAL-1");
        when(baseMapper.insert(any(SoCustomerOrder.class))).thenReturn(1);

        customerOrderService.insertByBo(bo);

        ArgumentCaptor<SoCustomerOrder> captor = ArgumentCaptor.forClass(SoCustomerOrder.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("CO-MANUAL-1", captor.getValue().getCoNo());
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
    }

    /** 被销售订单引用时拒绝删除（否则销售订单的 co_id 会悬空） */
    @Test
    void deleteByIds_rejectsWhenReferencedBySalesOrder() {
        when(soSalesOrderHeaderMapper.selectCount(any())).thenReturn(2L);
        when(baseMapper.selectById(1L)).thenReturn(customerOrder(1L, "CO-001"));

        ServiceException ex = assertThrows(ServiceException.class, () -> customerOrderService.deleteByIds(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("不允许删除"), "实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("CO-001"), "应提示单号: " + ex.getMessage());
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    /** 未被引用时正常删除 */
    @Test
    void deleteByIds_deletesWhenNotReferenced() {
        when(soSalesOrderHeaderMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.deleteBatchIds(anyList())).thenReturn(1);

        assertEquals(1, customerOrderService.deleteByIds(new Long[]{1L}));

        verify(baseMapper).deleteBatchIds(anyList());
    }

    /** 空入参直接返回 0，不做任何查询 */
    @Test
    void deleteByIds_emptyIds_returnsZero() {
        assertEquals(0, customerOrderService.deleteByIds(new Long[0]));
        verify(soSalesOrderHeaderMapper, never()).selectCount(any());
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    /** 入参含 null 时过滤掉，不触发 List.of 的 NPE */
    @Test
    void deleteByIds_ignoresNullElements() {
        when(soSalesOrderHeaderMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.deleteBatchIds(anyList())).thenReturn(1);

        assertEquals(1, customerOrderService.deleteByIds(new Long[]{1L, null}));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(baseMapper).deleteBatchIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(1L, captor.getValue().get(0));
    }

    /** 单号重复交给数据库唯一键拦截（uk_co_no + tenant_id），全局处理器会返回 409 */
    @Test
    void insertByBo_duplicateCoNo_bubblesUp() {
        SoCustomerOrderBo bo = new SoCustomerOrderBo();
        bo.setCoNo("CO-DUP");
        when(baseMapper.insert(any(SoCustomerOrder.class)))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_co_no"));

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> customerOrderService.insertByBo(bo));
    }
}
