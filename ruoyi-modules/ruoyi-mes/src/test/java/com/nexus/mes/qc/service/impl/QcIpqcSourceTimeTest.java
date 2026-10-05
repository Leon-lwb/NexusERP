package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.service.IQcInspectDetailService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 原检验时间从真实服务经提交后快照到共享消息的离线回归，所有外部端口均为替身。 */
@ExtendWith(MockitoExtension.class)
class QcIpqcSourceTimeTest {
    private static final long RECOVERY_DELAY = 5_050_000L;
    @Mock private QcIpqcMapper baseMapper;
    @Mock private ProWorkOrderMapper workOrderMapper;
    @Mock private INcrOrderService ncrOrderService;
    @Mock private IQcInspectDetailService detailService;
    @Mock private ReliableMessageProducer producer;
    private QcIpqcServiceImpl service;
    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;
    private boolean committed;

    @BeforeAll
    static void initializeColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcIpqc.class);
    }

    @AfterAll
    static void removeColumns() {
        TableInfoHelper.remove(QcIpqc.class);
    }

    @BeforeEach
    void setUp() {
        service = new QcIpqcServiceImpl(baseMapper, new MesMqSendService(producer),
            workOrderMapper, ncrOrderService, detailService);
        login = mockStatic(LoginHelper.class);
        tenant = mockStatic(TenantHelper.class);
        login.when(LoginHelper::getUserId).thenAnswer(inv -> {
            assertFalse(committed, "提交后不得读取检验员登录上下文");
            return 7L;
        });
        login.when(LoginHelper::getUsername).thenAnswer(inv -> {
            assertFalse(committed, "提交后不得读取检验员姓名");
            return "原检验员";
        });
        tenant.when(TenantHelper::getTenantId).thenAnswer(inv -> {
            assertFalse(committed, "提交后不得读取租户上下文");
            return "000123";
        });
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        login.close();
        tenant.close();
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryInspectionWithNullOldTimeWritesAndPublishesOneSecondPrecisionTime() {
        QcIpqc ipqc = inspection("PENDING", null);
        prepare(ipqc);
        Date[] written = new Date[1];
        when(baseMapper.update(isNull(), any())).thenAnswer(inv -> {
            LambdaUpdateWrapper<QcIpqc> wrapper = inv.getArgument(1);
            written[0] = wrapper.getParamNameValuePairs().values().stream()
                .filter(Date.class::isInstance).map(Date.class::cast).findFirst().orElseThrow();
            return 1;
        });
        long before = System.currentTimeMillis() / 1_000L * 1_000L;
        assertNull(ipqc.getInspectTime(), "原实体没有检验时间不能阻止首次通知");

        service.inspect(11L, "QUALIFIED", null, null, null, null);

        long source = written[0].getTime();
        assertTrue(source >= before && source <= System.currentTimeMillis());
        assertEquals(0L, source % 1_000L, "MES 与 ERP 的 datetime 列只保留秒精度");
        verifyNoInteractions(producer);
        // 写库参数也是可变日期，提交后快照不得持有这个引用。
        written[0].setTime(source + RECOVERY_DELAY);
        clearInvocations(baseMapper, workOrderMapper);
        afterCommit();

        assertEquals(source, sourceTime(published()), "写库与通知必须使用同一次捕获的源时间");
        assertNoAfterCommitReadsOrWrites();
    }

    @Test
    void recoveryAfter84Minutes10SecondsPublishesOriginalCompletedInspectionTime() {
        long deliveredAt = System.currentTimeMillis() / 1_000L * 1_000L;
        long source = deliveredAt - RECOVERY_DELAY;
        prepare(inspection("COMPLETED", new Date(source)));
        doAnswer(inv -> {
            IpqcCompletedMessage message = inv.getArgument(2);
            message.setTimestamp(deliveredAt);
            return null;
        }).when(producer).send(anyString(), anyString(), any());

        assertEquals("ENQUEUED", service.recoverNotification(11L));
        verifyNoInteractions(producer);
        clearInvocations(baseMapper, workOrderMapper);
        afterCommit();

        IpqcCompletedMessage message = published();
        assertEquals(source, sourceTime(message), "恢复延迟不能变成新的质量检验时间");
        assertEquals(RECOVERY_DELAY, message.getTimestamp() - sourceTime(message));
        assertNoAfterCommitReadsOrWrites();
    }

    @Test
    void recoveryFreezesMutableOriginalDateBeforeAfterCommit() {
        long source = System.currentTimeMillis() / 1_000L * 1_000L - RECOVERY_DELAY;
        Date original = new Date(source);
        QcIpqc ipqc = inspection("COMPLETED", original);
        prepare(ipqc);

        assertEquals("ENQUEUED", service.recoverNotification(11L));
        original.setTime(source + RECOVERY_DELAY);
        ipqc.setInspectTime(new Date(0L));
        clearInvocations(baseMapper, workOrderMapper);
        afterCommit();

        assertEquals(source, sourceTime(published()), "快照必须冻结 epochMillis 而非持有 Date 或实体");
        assertNoAfterCommitReadsOrWrites();
    }

    private void prepare(QcIpqc ipqc) {
        when(baseMapper.selectById(11L)).thenReturn(ipqc);
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(22L);
        wo.setErpWoId(33L);
        wo.setWoNo("原工单");
        wo.setTenantId("000123");
        wo.setDelFlag("0");
        when(workOrderMapper.selectById(22L)).thenAnswer(inv -> {
            assertFalse(committed, "原工单映射必须在提交前读取");
            return wo;
        });
    }

    private void afterCommit() {
        committed = true;
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private IpqcCompletedMessage published() {
        var message = ArgumentCaptor.forClass(IpqcCompletedMessage.class);
        verify(producer).send(eq(ExchangeConstants.TOPIC_EXCHANGE),
            eq(RoutingKeyConstants.ERP_IPQC_COMPLETED), message.capture());
        assertEquals(123L, message.getValue().getTenantId());
        return message.getValue();
    }

    private static long sourceTime(IpqcCompletedMessage message) {
        var value = new ObjectMapper().valueToTree(message).get("inspectTime");
        assertNotNull(value, "通知必须透传原检验时间，不能只携带发送元数据");
        assertFalse(value.isNull(), "原检验时间不可为空");
        return value.longValue();
    }

    private void assertNoAfterCommitReadsOrWrites() {
        verifyNoInteractions(baseMapper, workOrderMapper, ncrOrderService, detailService);
    }

    private static QcIpqc inspection(String status, Date sourceTime) {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(11L);
        ipqc.setIpqcNo("原IPQC");
        ipqc.setMesWoId(22L);
        ipqc.setTenantId("000123");
        ipqc.setDelFlag("0");
        ipqc.setStatus(status);
        ipqc.setInspectResult("QUALIFIED");
        ipqc.setInspectTime(sourceTime);
        ipqc.setInspectorId(7L);
        ipqc.setInspectorName("原检验员");
        return ipqc;
    }
}
