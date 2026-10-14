package com.nexus.report.domain;

/** 面向客户端的安全异常，消息中不包含原始 SQL、凭证或业务值。 */
public final class ReportFault extends RuntimeException {
    /** 业务编码。 */
    private final String code;

    /** 保存当前 {@code status} 值。 */
    private final int status;

    /**
     * 初始化报表安全异常并保存所需依赖或不可变值。
     *
     * @param code 业务编码
     * @param message 当前操作的 {@code message} 输入
     */
    public ReportFault(String code, String message) {
        this(code, message, 400);
    }

    /**
     * 初始化报表安全异常并保存所需依赖或不可变值。
     *
     * @param code 业务编码
     * @param message 当前操作的 {@code message} 输入
     * @param status 当前操作的 {@code status} 输入
     */
    public ReportFault(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * 完成 {@code code} 对应的报表安全异常处理。
     *
     * @return 当前处理返回的固定结果
     */
    public String code() {
        return code;
    }

    /**
     * 重新鉴权后读取运行状态。
     *
     * @return 当前处理返回的固定结果
     */
    public int status() {
        return status;
    }

    /**
     * 完成 {@code invalid} 对应的报表安全异常处理。
     *
     * @param message 当前操作的 {@code message} 输入
     * @return 当前处理返回的固定结果
     */
    public static ReportFault invalid(String message) {
        return new ReportFault("INVALID_TEMPLATE", message);
    }

    /**
     * 完成 {@code denied} 对应的报表安全异常处理。
     *
     * @return 当前处理返回的固定结果
     */
    public static ReportFault denied() {
        return new ReportFault("SCOPE_DENIED", "无权访问该报表资源或数据", 403);
    }

    /**
     * 完成 {@code capacity} 对应的报表安全异常处理。
     *
     * @return 当前处理返回的固定结果
     */
    public static ReportFault capacity() {
        return new ReportFault("CAPACITY_EXCEEDED", "超出首期容量限制，请缩小范围或选择明细导出");
    }
}
