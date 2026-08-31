package com.nexus.ai.observability;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 跨线程事件总线
 * <p>
 * 写入端（异步线程）：Agent 执行线程
 * 读取端（SSE 线程）：AiChatController SSE 端点
 * </p>
 * <p>
 * 调用链路：
 *   SSE请求 -> 创建 OutputChannel
 *           -> SupervisorAgent.invoke() [同步阻塞调用子Agent]
 *              ├── AgentStreamListener -> channel.send()
 *              └── 子Agent 工具调用 -> channel.send()
 *           -> channel.complete()
 *   drain线程 -> channel.drain() -> SSE实时推送
 * </p>
 *
 * @author NexusERP
 */
public class OutputChannel {

    /** 结束标记，写入队列表示事件流完成。 */
    private static final String DONE = "__DONE__";
    /** 最大 drain 时长：10 分钟，防止 Agent 挂起导致线程永久阻塞 */
    private static final long MAX_DRAIN_MS = 10 * 60 * 1000;
    /** 全局 OutputChannel 注册表（requestId → channel）。 */
    private static final Map<String, OutputChannel> REGISTRY = new ConcurrentHashMap<>();

    /** 注册表条目最大存活时间：30 分钟，防止异常退出时泄漏 */
    private static final long MAX_ENTRY_AGE_MS = 30 * 60 * 1000;

    /** 事件消息队列（容量 4096），满时丢弃而非阻塞。 */
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(4096);
    /** 执行异常引用，drain 时若非空则包装抛出。 */
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    /** 完成信号锁存器，标记事件流结束。 */
    private final CountDownLatch completed = new CountDownLatch(1);
    /** 创建时间戳，用于过期与超时判定。 */
    private final long createTime = System.currentTimeMillis();

    /**
     * 创建并注册到全局注册表
     */
    public static OutputChannel create(String requestId) {
        OutputChannel ch = new OutputChannel();
        REGISTRY.put(requestId, ch);
        return ch;
    }

    /**
     * 从全局注册表移除
     */
    public static void remove(String requestId) {
        REGISTRY.remove(requestId);
    }

    /**
     * 从全局注册表获取
     */
    public static OutputChannel get(String requestId) {
        return REGISTRY.get(requestId);
    }

    /**
     * 清理注册表中的过期条目（已完成或超时）
     * <p>由调度任务定期调用，防止异常退出时注册表泄漏</p>
     */
    public static void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var iterator = REGISTRY.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            OutputChannel ch = entry.getValue();
            if (ch.isCompleted() || (now - ch.createTime > MAX_ENTRY_AGE_MS)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            System.err.println("[OutputChannel] 清理 " + removed + " 个过期注册表条目，剩余 " + REGISTRY.size());
        }
    }

    /**
     * 写入：线程安全，非阻塞，队列满时丢弃
     */
    public void send(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            if (!queue.offer(text, 100, TimeUnit.MILLISECONDS)) {
                System.err.println("[OutputChannel] 队列满，丢弃消息: " + truncate(text, 100));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 标记完成
     */
    public void complete() {
        queue.offer(DONE);
        completed.countDown();
    }

    /**
     * 标记错误完成
     */
    public void completeWithError(Throwable t) {
        error.set(t);
        queue.offer("\n[错误] 致命错误: " + t.getMessage());
        queue.offer(DONE);
        completed.countDown();
    }

    /**
     * 读取：阻塞迭代，配合 SSE 使用
     * <p>增加超时保护：超过 10 分钟自动退出，防止 Agent 挂起导致线程永久阻塞</p>
     */
    public void drain(Consumer<String> emitter) throws InterruptedException {
        while (true) {
            // 超时保护
            if (System.currentTimeMillis() - createTime > MAX_DRAIN_MS) {
                System.err.println("[OutputChannel] 超时退出（超过 " + (MAX_DRAIN_MS / 1000) + " 秒）");
                break;
            }
            String msg = queue.poll(200, TimeUnit.MILLISECONDS);
            if (msg != null) {
                if (DONE.equals(msg)) {
                    break;
                }
                emitter.accept(msg);
            } else {
                if (completed.getCount() == 0 && queue.isEmpty()) {
                    break;
                }
            }
        }
        Throwable t = error.get();
        if (t != null && !(t instanceof InterruptedException)) {
            throw new RuntimeException("Agent 执行出错", t);
        }
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return completed.getCount() == 0;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "null";
        }
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
