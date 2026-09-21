package com.nexus.erp.fin.arap;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.bo.FinReceiptBo;
import com.nexus.erp.fin.arap.mapper.FinReceiptMapper;
import com.nexus.erp.fin.arap.service.impl.FinReceiptServiceImpl;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收付款单服务单元测试 —— 固化 FIN-ARAP-01 修复口径。
 *
 * <p><b>背景</b>：{@code FinReceiptBo} 原样暴露 {@code status}/{@code source}，客户端可在
 * {@code POST /erp/fin/receipt} 直传 {@code status=CONFIRMED} 落库，而唯一会发出
 * {@code GlPostingMessage(RECEIPT)} 的入口是 {@code confirm()} —— 该笔收付款在 AR/AP 子账已发生，
 * 总账却永久缺失凭证。</p>
 *
 * <p>本类断言：① 直传 CONFIRMED 被拒；② 服务端强制 DRAFT/MANUAL/已结算额 0；
 * ③ 修改接口不允许变更状态、不覆盖服务端权威字段。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class FinReceiptServiceImplTest {

    @Mock
    private FinReceiptMapper baseMapper;
    @Mock
    private ReliableMessageProducer messageProducer;

    @InjectMocks
    private FinReceiptServiceImpl receiptService;

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
        TableInfoHelper.initTableInfo(assistant, FinReceipt.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private FinReceiptBo bo(String status, String source) {
        FinReceiptBo bo = new FinReceiptBo();
        bo.setDirection(ArapConstants.DIRECTION_AR);
        bo.setReceiptType("BANK");
        bo.setAmount(new BigDecimal("1000.00"));
        bo.setStatus(status);
        bo.setSource(source);
        return bo;
    }

    /** 客户端直传 CONFIRMED 必须被拒（修复前会落库为 CONFIRMED 且不发 GL 过账消息） */
    @Test
    void insertByBo_rejectsClientSuppliedConfirmedStatus() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.insertByBo(bo(ArapConstants.RECEIPT_STATUS_CONFIRMED, null)));
        assertTrue(ex.getMessage().contains("状态由服务端管理"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(FinReceipt.class));
    }

    /** 客户端直传 PAY_CALLBACK 来源必须被拒（该来源只能由 PayReceivedConsumer 内部产生） */
    @Test
    void insertByBo_rejectsClientSuppliedPayCallbackSource() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> receiptService.insertByBo(bo(null, ArapConstants.SOURCE_PAY_CALLBACK)));
        assertTrue(ex.getMessage().contains("来源由服务端管理"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(FinReceipt.class));
    }

    /** 正常新增：服务端强制 DRAFT / MANUAL / settled_amount=0 */
    @Test
    void insertByBo_forcesServerAuthoritativeFields() {
        when(baseMapper.insert(any(FinReceipt.class))).thenReturn(1);
        assertTrue(receiptService.insertByBo(bo(null, null)));

        ArgumentCaptor<FinReceipt> captor = ArgumentCaptor.forClass(FinReceipt.class);
        verify(baseMapper).insert(captor.capture());
        FinReceipt saved = captor.getValue();
        assertEquals(ArapConstants.RECEIPT_STATUS_DRAFT, saved.getStatus());
        assertEquals(ArapConstants.SOURCE_MANUAL, saved.getSource());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getSettledAmount()));
        assertNotNull(saved.getReceiptNo());
        assertTrue(saved.getReceiptNo().startsWith("RC"));
    }

    /** 修改接口不得把 DRAFT 改成 CONFIRMED（否则同样绕过 confirm 的 GL 过账） */
    @Test
    void updateByBo_rejectsStatusChange() {
        FinReceipt existing = new FinReceipt();
        existing.setReceiptId(9L);
        existing.setStatus(ArapConstants.RECEIPT_STATUS_DRAFT);
        existing.setSource(ArapConstants.SOURCE_MANUAL);
        existing.setVersion(0);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        FinReceiptBo bo = bo(ArapConstants.RECEIPT_STATUS_CONFIRMED, null);
        bo.setReceiptId(9L);
        ServiceException ex = assertThrows(ServiceException.class, () -> receiptService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("不可通过修改接口变更"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).updateById(any(FinReceipt.class));
    }

    /** 修改接口只拷贝可编辑字段：status/source/settledAmount 不落库 */
    @Test
    void updateByBo_copiesOnlyEditableFields() {
        FinReceipt existing = new FinReceipt();
        existing.setReceiptId(9L);
        existing.setStatus(ArapConstants.RECEIPT_STATUS_DRAFT);
        existing.setSource(ArapConstants.SOURCE_MANUAL);
        existing.setVersion(3);
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.updateById(any(FinReceipt.class))).thenReturn(1);

        FinReceiptBo bo = bo(ArapConstants.RECEIPT_STATUS_DRAFT, ArapConstants.SOURCE_MANUAL);
        bo.setReceiptId(9L);
        bo.setAmount(new BigDecimal("2000.00"));
        bo.setRemark("改金额");
        assertTrue(receiptService.updateByBo(bo));

        ArgumentCaptor<FinReceipt> captor = ArgumentCaptor.forClass(FinReceipt.class);
        verify(baseMapper).updateById(captor.capture());
        FinReceipt updated = captor.getValue();
        assertNull(updated.getStatus(), "status 不应由修改接口写入");
        assertNull(updated.getSource(), "source 不应由修改接口写入");
        assertNull(updated.getSettledAmount(), "settled_amount 不应由修改接口写入");
        assertEquals(3, updated.getVersion(), "乐观锁版本沿用库中值");
        assertEquals(0, new BigDecimal("2000.00").compareTo(updated.getAmount()));
    }
}
