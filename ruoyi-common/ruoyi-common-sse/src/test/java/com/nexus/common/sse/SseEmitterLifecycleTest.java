package com.nexus.common.sse;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.sse.core.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SSE 重连后旧实例的回调不得误删新实例。 */
class SseEmitterLifecycleTest {
    @Test
    void replacedEmitterDuringFirstFrameDoesNotEscape() throws Exception {
        AtomicReference<SseEmitterManager> managerRef = new AtomicReference<>();
        try (var spring = mockStatic(SpringUtil.class);
             var emitters = mockConstruction(SseEmitter.class, (emitter, context) -> {
                 if (context.getCount() == 1) {
                     doAnswer(call -> {
                         managerRef.get().connect(14L, "racing-token");
                         throw new IllegalStateException("旧实例已完成");
                     }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
                 }
             })) {
            spring.when(() -> SpringUtil.getBean(ScheduledExecutorService.class))
                .thenReturn(mock(ScheduledExecutorService.class));
            SseEmitterManager manager = new SseEmitterManager();
            managerRef.set(manager);

            assertDoesNotThrow(() -> manager.connect(14L, "racing-token"));
            SseEmitter replacement = emitters.constructed().get(1);
            manager.sendMessage(14L, "新连接继续接收");
            verify(replacement, never()).complete();
            verify(replacement, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            manager.disconnect(14L, "racing-token");
        }
    }

    @Test
    void oldCompletionDoesNotCompleteReplacement() throws Exception {
        try (var spring = mockStatic(SpringUtil.class);
             var emitters = mockConstruction(SseEmitter.class)) {
            spring.when(() -> SpringUtil.getBean(ScheduledExecutorService.class))
                .thenReturn(mock(ScheduledExecutorService.class));
            SseEmitterManager manager = new SseEmitterManager();
            SseEmitter oldEmitter = manager.connect(12L, "completion-token");
            ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onCompletion(callback.capture());
            SseEmitter newEmitter = manager.connect(12L, "completion-token");

            callback.getValue().run();
            manager.sendMessage(12L, "重连后的消息");

            verify(newEmitter, never()).complete();
            // 首次连接注释及后续业务消息均应发送给新实例。
            verify(newEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            manager.disconnect(12L, "completion-token");
        }
    }

    @Test
    void oldErrorDoesNotRemoveReplacement() throws Exception {
        try (var spring = mockStatic(SpringUtil.class);
             var emitters = mockConstruction(SseEmitter.class)) {
            spring.when(() -> SpringUtil.getBean(ScheduledExecutorService.class))
                .thenReturn(mock(ScheduledExecutorService.class));
            SseEmitterManager manager = new SseEmitterManager();
            SseEmitter oldEmitter = manager.connect(13L, "error-token");
            ArgumentCaptor<Consumer<Throwable>> callback = ArgumentCaptor.forClass(Consumer.class);
            verify(oldEmitter).onError(callback.capture());
            SseEmitter newEmitter = manager.connect(13L, "error-token");

            callback.getValue().accept(new IllegalStateException("旧连接断开"));
            manager.sendMessage(13L, "新连接消息");

            verify(newEmitter, never()).complete();
            verify(newEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            manager.disconnect(13L, "error-token");
        }
    }
}