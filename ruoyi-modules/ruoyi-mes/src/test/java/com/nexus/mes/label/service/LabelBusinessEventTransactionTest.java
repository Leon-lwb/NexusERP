package com.nexus.mes.label.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.trace.SerialIdentity;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.listener.SnLabelPrintEventListener;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.sql.SQLException;
import java.util.List;

/**
 * 用真实Spring事件发布与真实事务验证业务行和打印任务同时提交或回滚。
 */
class LabelBusinessEventTransactionTest extends LabelDurableTestSupport {
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesLabelTemplate.class);
    }

    AnnotationConfigApplicationContext publisher() {
        var templates = mock(MesLabelTemplateMapper.class);
        var sns = mock(ProSnMapper.class);
        var template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateRevision(7L);
        template.setLabelType("SN");
        template.setContent("{\"items\":[{\"type\":\"barcode\",\"field\":\"snCode\"}]}");
        when(templates.selectList(any())).thenReturn(List.of(template));
        var sn = new ProSn();
        sn.setTenantId("000001");
        sn.setSnId(1L);
        sn.setIdentityId(1001L);
        sn.setMaterialId(100L);
        sn.setSnCode("SN-1");
        when(sns.selectById(1L)).thenReturn(sn);
        var identities = mock(SerialIdentityPort.class);
        when(identities.find("000001", 1001L))
                .thenReturn(new SerialIdentity(1001L, "SN-1", 100L, null, null));
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(
                "labelListener",
                SnLabelPrintEventListener.class,
                () ->
                        new SnLabelPrintEventListener(
                                templates, sns, new LabelRenderService(identities), service));
        context.refresh();
        return context;
    }

    @Test
    void businessCommitSavesPendingTaskButDoesNotSendInsideTransaction() {
        jdbc.execute("CREATE TABLE synthetic_sn(sn_id BIGINT PRIMARY KEY)");
        DynamicDataSourceContextHolder.push("mes");
        try (var publisher = publisher()) {
            business.executeWithoutResult(
                    tx -> {
                        jdbc.update("INSERT INTO synthetic_sn VALUES(1)");
                        publisher.publishEvent(new SnCreatedEvent(1L, "SN-1", null, 100L));
                        assertEquals(1, count("mes_label_print_task"));
                        assertEquals(0, sends.get());
                        try (var independent = jdbc.getDataSource().getConnection();
                                var statement = independent.createStatement();
                                var rows =
                                        statement.executeQuery(
                                                "SELECT COUNT(*) FROM mes_label_print_task")) {
                            assertTrue(rows.next());
                            assertEquals(0, rows.getInt(1), "提交前任务不能被其他连接领取");
                        } catch (SQLException error) {
                            throw new IllegalStateException(error);
                        }
                    });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(1, count("synthetic_sn"));
        assertEquals("PENDING", onlyTask().getStatus());
        assertEquals(0, sends.get());
        dispatcher.pollTenant();
        assertEquals(1, sends.get());
    }

    @Test
    void taskStorageFailureRollsBackBusinessEventPublisherTransaction() {
        jdbc.execute("CREATE TABLE synthetic_sn(sn_id BIGINT PRIMARY KEY)");
        jdbc.execute("DROP TABLE mes_label_print_task");
        DynamicDataSourceContextHolder.push("mes");
        try (var publisher = publisher()) {
            assertThrows(
                    Exception.class,
                    () ->
                            business.executeWithoutResult(
                                    tx -> {
                                        jdbc.update("INSERT INTO synthetic_sn VALUES(1)");
                                        publisher.publishEvent(
                                                new SnCreatedEvent(1L, "SN-1", null, 100L));
                                    }));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(0, count("synthetic_sn"));
        assertEquals(0, sends.get());
    }

    @Test
    void duplicateBusinessEventDoesNotChangeFrozenTaskOrProduceSecondSend() {
        DynamicDataSourceContextHolder.push("mes");
        try (var publisher = publisher()) {
            business.executeWithoutResult(
                    tx -> {
                        publisher.publishEvent(new SnCreatedEvent(1L, "SN-1", null, 100L));
                        publisher.publishEvent(new SnCreatedEvent(1L, "SN-1", null, 100L));
                    });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        String frozen = onlyTask().getFrozenRecord();
        assertEquals(1, count("mes_label_print_task"));
        dispatcher.pollTenant();
        dispatcher.pollTenant();
        assertEquals(1, sends.get());
        assertEquals(frozen, onlyTask().getFrozenRecord());
    }
}
