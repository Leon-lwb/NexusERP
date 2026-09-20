package com.nexus.mes.label.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelPrintLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 标签打印发送服务 — 网络打印机 TCP RAW 9100 直发 + 打印留痕
 * <p>自管 daemon 单线程池顺序发送: SN 批量造码的事件监听只做提交,
 * TCP 读写不阻塞造码请求线程; 打印失败仅告警不抛(打印是尽力而为的旁路动作)。</p>
 * <p><b>MES-LABEL-02 留痕</b>: 每次发送(成功/失败)与渲染失败都落 {@code mes_label_print_log},
 * 记录 SN/模板/打印机/时间/结果/失败原因/重打次数, 可追溯"一个 SN 打了几张"。</p>
 * <p><b>租户上下文</b>: 打印在自管异步线程执行, 该线程没有 Sa-Token/租户 ThreadLocal,
 * 故 {@link #submit} 先在调用线程固化 tenant_id, 落库/计数时再用 {@link TenantHelper#dynamic} 还原。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class LabelPrintService {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int SO_TIMEOUT_MS = 3_000;
    /** 打印机端口缺省值 */
    private static final int DEFAULT_PORT = 9100;
    /** 失败原因落库截断长度(与 mes_label_print_log.fail_reason 对齐) */
    private static final int FAIL_REASON_MAX_LEN = 500;

    private final MesLabelPrintLogMapper printLogMapper;

    /** 单线程顺序打印(标签打印机串行消费, 避免并发连接互踩) */
    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "label-print");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造打印留痕对象(补齐操作人/租户): 登录态取当前用户, 设备/事件通道留空
     *
     * @param snId        SN ID(可空)
     * @param snCode      SN 编码(可空)
     * @param template    模板
     * @param printSource 打印来源(AUTO/REPRINT/TEST)
     */
    public MesLabelPrintLog newLog(Long snId, String snCode, MesLabelTemplate template, String printSource) {
        MesLabelPrintLog printLog = MesLabelPrintLog.of(snId, snCode, template.getTemplateId(),
            template.getTemplateCode(), template.getPrinterHost(), template.getPrinterPort(), printSource);
        try {
            if (LoginHelper.isLogin()) {
                printLog.setOperatorId(LoginHelper.getUserId());
                printLog.setOperatorName(LoginHelper.getUsername());
            }
        } catch (Exception e) {
            // 无登录上下文(异步/设备通道/单测): 操作人留空, 不阻断打印
        }
        printLog.setTenantId(currentTenantId());
        return printLog;
    }

    /**
     * 异步提交打印任务(事件联动用, 失败静默告警)
     *
     * @param printLog 打印留痕对象(含 SN/模板/打印机/来源)
     * @param payload  指令串
     * @param charset  发送编码(TSPL=GBK, ZPL=UTF-8)
     */
    public void submit(MesLabelPrintLog printLog, String payload, Charset charset) {
        // 异步线程无租户/登录上下文: 先在调用线程固化 tenant_id, 留痕落库时再还原
        if (StringUtils.isBlank(printLog.getTenantId())) {
            printLog.setTenantId(currentTenantId());
        }
        printExecutor.submit(() -> {
            try {
                send(printLog, payload, charset);
            } catch (Exception e) {
                log.warn("标签打印任务异常(不影响业务): template={}, host={}:{}, error={}",
                    printLog.getTemplateCode(), printLog.getPrinterHost(), printLog.getPrinterPort(), e.getMessage());
            }
        });
    }

    /**
     * 同步直发并留痕(MES-LABEL-02): 成功/失败都落 {@code mes_label_print_log}(失败落 result=FAIL + 原因)
     *
     * @return true 发送成功; false 发送失败(打印机不在线/网络异常/未配置地址, 已记日志与留痕)
     */
    public boolean send(MesLabelPrintLog printLog, String payload, Charset charset) {
        String templateCode = printLog.getTemplateCode();
        String host = printLog.getPrinterHost();
        int port = printLog.getPrinterPort() == null ? DEFAULT_PORT : printLog.getPrinterPort();
        printLog.setRetryCount(countPriorPrints(printLog));
        printLog.setPrintTime(new Date());
        boolean success = false;
        String failReason = null;
        if (StringUtils.isBlank(host)) {
            failReason = "未配置打印机地址";
            log.warn("标签打印发送失败: 模板 {} 未配置打印机地址", templateCode);
        } else {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(SO_TIMEOUT_MS);
                try (OutputStream out = socket.getOutputStream()) {
                    out.write(payload.getBytes(charset));
                    out.flush();
                }
                success = true;
                log.info("标签打印已发送: template={}, host={}:{}, bytes={}, snCode={}",
                    templateCode, host, port, payload.getBytes(charset).length, printLog.getSnCode());
            } catch (IOException e) {
                failReason = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("标签打印发送失败: template={}, host={}:{}, error={}", templateCode, host, port, e.getMessage());
            }
        }
        printLog.setResult(success ? MesLabelPrintLog.RESULT_SUCCESS : MesLabelPrintLog.RESULT_FAIL);
        printLog.setFailReason(success ? null : truncate(failReason));
        inTenant(printLog, () -> {
            printLogMapper.insert(printLog);
            return Boolean.TRUE;
        }, Boolean.FALSE);
        return success;
    }

    /**
     * 渲染/校验失败留痕(MES-LABEL-03): 未发起 TCP 发送也落 result=FAIL 记录, 不再静默只打 warn
     *
     * @param printLog 打印留痕对象
     * @param reason   失败原因(渲染异常等)
     */
    public void recordFailure(MesLabelPrintLog printLog, String reason) {
        printLog.setPrintTime(new Date());
        printLog.setRetryCount(countPriorPrints(printLog));
        printLog.setResult(MesLabelPrintLog.RESULT_FAIL);
        printLog.setFailReason(truncate(reason));
        inTenant(printLog, () -> {
            printLogMapper.insert(printLog);
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    /** 同一 (sn_id, template_id) 的历史打印次数(重打计数; 无 SN 上下文时为 0) */
    private int countPriorPrints(MesLabelPrintLog printLog) {
        if (printLog.getSnId() == null || printLog.getTemplateId() == null) {
            return 0;
        }
        return inTenant(printLog, () -> {
            Long count = printLogMapper.selectCount(Wrappers.<MesLabelPrintLog>lambdaQuery()
                .eq(MesLabelPrintLog::getSnId, printLog.getSnId())
                .eq(MesLabelPrintLog::getTemplateId, printLog.getTemplateId()));
            return count == null ? 0 : count.intValue();
        }, 0);
    }

    /**
     * 在打印记录所属租户上下文中执行(打印在异步线程, 无 ThreadLocal 上下文);
     * 留痕失败只告警, 绝不影响打印主流程。
     */
    private <T> T inTenant(MesLabelPrintLog printLog, Supplier<T> action, T fallback) {
        String tenantId = printLog.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            tenantId = currentTenantId();
            printLog.setTenantId(tenantId);
        }
        try {
            if (tenantUsable() && StringUtils.isNotBlank(tenantId)) {
                final String tid = tenantId;
                return TenantHelper.dynamic(tid, action);
            }
            return action.get();
        } catch (Exception e) {
            log.warn("标签打印留痕失败(不影响打印): template={}, snCode={}, error={}",
                printLog.getTemplateCode(), printLog.getSnCode(), e.getMessage());
            return fallback;
        }
    }

    /** 当前租户ID(无 Spring/租户上下文时返回 null, 不抛异常) */
    private static String currentTenantId() {
        try {
            return TenantHelper.getTenantId();
        } catch (Exception | Error e) {
            return null;
        }
    }

    /** 租户能力可用性探测: 无 Spring 上下文(如纯单测)时 TenantHelper 会抛错, 此时按无租户执行 */
    private static boolean tenantUsable() {
        try {
            TenantHelper.isEnable();
            return true;
        } catch (Exception | Error e) {
            return false;
        }
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > FAIL_REASON_MAX_LEN ? reason.substring(0, FAIL_REASON_MAX_LEN) : reason;
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
