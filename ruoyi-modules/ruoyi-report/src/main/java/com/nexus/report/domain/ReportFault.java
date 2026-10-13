package com.nexus.report.domain;
/** Client-safe fault. Raw SQL, credentials and business values never form its message. */
public final class ReportFault extends RuntimeException {
    private final String code;
    private final int status;
    public ReportFault(String code, String message) { this(code, message, 400); }
    public ReportFault(String code, String message, int status) { super(message); this.code=code; this.status=status; }
    public String code() { return code; }
    public int status() { return status; }
    public static ReportFault invalid(String message) { return new ReportFault("INVALID_TEMPLATE", message); }
    public static ReportFault denied() { return new ReportFault("SCOPE_DENIED", "无权访问该报表资源或数据", 403); }
    public static ReportFault capacity() { return new ReportFault("CAPACITY_EXCEEDED", "超出首期容量限制，请缩小范围或选择明细导出"); }
}
