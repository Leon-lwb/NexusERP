package com.nexus.mes.label.service;

import com.nexus.mes.label.domain.MesLabelPrintLog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 一次TCP连接中的有界发送；不持有事务、不读取设备回执、不重连重写原载荷。
 */
@Component
public class LabelPrinterTransport {
    private static final long IO_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(3);

    /**
     * UNKNOWN 表示可能写出过部分或全部字节，必须人工核验。
     *
     * @param status 解析或业务处理的明确状态。
     * @param error 经过脱敏的错误说明。
     */
    public record Result(String status, String error) {}

    /**
     * 非阻塞连接和写入都有真实截止时间，避免Socket读超时被误当成写超时。
     */
    public Result send(MesLabelPrintLog record) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("打印发送不得持有数据库事务");
        }
        if (record.getPrinterHost() == null || record.getPrinterHost().isBlank()) {
            return new Result("FAIL", "未配置打印机地址");
        }
        boolean writeStarted = false;
        try (SocketChannel channel = openChannel()) {
            channel.configureBlocking(false);
            long connectDeadline = System.nanoTime() + IO_TIMEOUT_NANOS;
            InetSocketAddress destination =
                    new InetSocketAddress(
                            record.getPrinterHost(),
                            record.getPrinterPort() == null ? 9100 : record.getPrinterPort());
            if (System.nanoTime() >= connectDeadline) {
                throw new SocketTimeoutException("打印地址解析超过截止时间");
            }
            if (!channel.connect(destination)) {
                do {
                    awaitReady(channel, SelectionKey.OP_CONNECT, connectDeadline);
                } while (!channel.finishConnect());
            }
            if (System.nanoTime() >= connectDeadline) {
                throw new SocketTimeoutException("打印连接超过截止时间");
            }
            ByteBuffer bytes =
                    ByteBuffer.wrap(
                            record.getRenderedPayload()
                                    .getBytes(Charset.forName(record.getPayloadCharset())));
            long writeDeadline = System.nanoTime() + IO_TIMEOUT_NANOS;
            while (bytes.hasRemaining()) {
                if (System.nanoTime() >= writeDeadline) {
                    throw new SocketTimeoutException("打印写入超过截止时间");
                }
                writeStarted = true;
                if (channel.write(bytes) == 0) {
                    awaitReady(channel, SelectionKey.OP_WRITE, writeDeadline);
                }
            }
            return new Result("SENT", null);
        } catch (IOException | RuntimeException failure) {
            return new Result(
                    writeStarted ? "UNKNOWN" : "FAIL",
                    writeStarted ? "发送过程异常或超时，可能已有指令到达打印机；需人工核验后按原任务补打" : "打印机连接或发送参数不可用，任务不会自动重发");
        }
    }

    /**
     * 单独封装通道创建供无网络单元测试替换，不提供重试语义。
     */
    SocketChannel openChannel() throws IOException {
        return SocketChannel.open();
    }

    /**
     * 等待同一连接可读写事件；超时仅终止当前尝试，不重新发送已写出的字节。
     */
    void awaitReady(SocketChannel channel, int operation, long deadline) throws IOException {
        try (Selector selector = Selector.open()) {
            channel.register(selector, operation);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new SocketTimeoutException("打印网络等待超过截止时间");
                }
                if (selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))) > 0) {
                    return;
                }
            }
        }
    }
}
