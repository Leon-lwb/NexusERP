package com.nexus.mes.label.service;

import com.nexus.common.core.utils.StringUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 标签打印发送服务 — 网络打印机 TCP RAW 9100 直发
 * <p>自管 daemon 单线程池顺序发送: SN 批量造码的事件监听只做提交,
 * TCP 读写不阻塞造码请求线程; 发送失败仅告警不抛(打印是尽力而为的旁路动作)。</p>
 *
 * @author Nexus
 */
@Slf4j
@Service
public class LabelPrintService {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int SO_TIMEOUT_MS = 3_000;

    /** 单线程顺序打印(标签打印机串行消费, 避免并发连接互踩) */
    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "label-print");
        t.setDaemon(true);
        return t;
    });

    /**
     * 异步提交打印任务(事件联动用, 失败静默告警)
     *
     * @param templateCode 模板编码(日志定位)
     * @param host         打印机地址
     * @param port         打印机端口(RAW 9100)
     * @param payload      指令串
     * @param charset      发送编码(TSPL=GBK, ZPL=UTF-8)
     */
    public void submit(String templateCode, String host, int port, String payload, Charset charset) {
        printExecutor.submit(() -> {
            try {
                send(templateCode, host, port, payload, charset);
            } catch (Exception e) {
                log.warn("标签打印任务异常(不影响业务): template={}, host={}:{}, error={}",
                    templateCode, host, port, e.getMessage());
            }
        });
    }

    /**
     * 同步直发(测试打印用)
     *
     * @return true 发送成功; false 发送失败(打印机不在线/网络异常, 已记日志)
     */
    public boolean send(String templateCode, String host, int port, String payload, Charset charset) {
        if (StringUtils.isBlank(host)) {
            log.warn("标签打印发送失败: 模板 {} 未配置打印机地址", templateCode);
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SO_TIMEOUT_MS);
            try (OutputStream out = socket.getOutputStream()) {
                out.write(payload.getBytes(charset));
                out.flush();
            }
            log.info("标签打印已发送: template={}, host={}:{}, bytes={}",
                templateCode, host, port, payload.getBytes(charset).length);
            return true;
        } catch (IOException e) {
            log.warn("标签打印发送失败: template={}, host={}:{}, error={}", templateCode, host, port, e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        printExecutor.shutdown();
        try {
            if (!printExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                printExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            printExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
