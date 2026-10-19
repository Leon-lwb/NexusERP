package com.nexus.mes.label.config;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.label.service.LabelPrintDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 真实Spring调度注册回归，不连接数据库或打印机，不改变其他模块的默认调度器。
 */
class LabelPrintTaskSchedulingTest {
    @Test
    void dedicatedLabelSchedulerPreservesExistingUnqualifiedScheduler() throws Exception {
        var tenants = mock(MesSysTenantMapper.class);
        var dispatcher = mock(LabelPrintDispatcher.class);
        var labelTicks = new CountDownLatch(2);
        var labelThreads = new CopyOnWriteArrayList<String>();
        var scopes = new CopyOnWriteArrayList<String>();
        when(tenants.selectAllTenantIds())
                .thenAnswer(
                        invocation -> {
                            labelThreads.add(Thread.currentThread().getName());
                            scopes.add(DynamicDataSourceContextHolder.peek());
                            labelTicks.countDown();
                            return List.of();
                        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MesSysTenantMapper.class, () -> tenants);
            context.registerBean(LabelPrintDispatcher.class, () -> dispatcher);
            context.register(SharedScheduling.class, LabelPrintTaskScheduling.class);
            context.refresh();
            var control = context.getBean(Control.class);
            assertTrue(control.tick.await(5, TimeUnit.SECONDS), "既有任务必须实际运行");
            assertTrue(
                    control.thread.get().startsWith("existing-scheduler-"), control.thread.get());
            assertTrue(labelTicks.await(5, TimeUnit.SECONDS), "标签发送与租约回收均须注册并运行");
            assertTrue(
                    labelThreads.stream().allMatch(name -> name.startsWith("label-task-poll-")),
                    labelThreads.toString());
            assertTrue(scopes.stream().allMatch("master"::equals), scopes.toString());
            verifyNoInteractions(dispatcher);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SharedScheduling {
        @Bean("iotTaskScheduler")
        ThreadPoolTaskScheduler existingScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setThreadNamePrefix("existing-scheduler-");
            return scheduler;
        }

        @Bean
        Control control() {
            return new Control();
        }
    }

    static class Control {
        final CountDownLatch tick = new CountDownLatch(1);
        final AtomicReference<String> thread = new AtomicReference<>();

        @Scheduled(fixedDelay = 3600000)
        public void heartbeat() {
            thread.set(Thread.currentThread().getName());
            tick.countDown();
        }
    }
}
