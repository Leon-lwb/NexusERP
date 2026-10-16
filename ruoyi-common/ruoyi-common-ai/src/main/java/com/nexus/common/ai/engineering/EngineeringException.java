package com.nexus.common.ai.engineering;

/** 带协议错误码和真实 HTTP 状态的工程异常，消息必须为不含敏感数据的中文摘要。 */
public class EngineeringException extends RuntimeException {
    private final int status;
    private final String code;

    /** 创建可安全返回客户端的业务异常。 */
    public EngineeringException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** HTTP 状态。 */
    public int status() {
        return status;
    }

    /** 稳定的协议错误码。 */
    public String code() {
        return code;
    }

    /** 条件不成立时以指定协议错误中止。 */
    public static void require(boolean condition, int status, String code, String message) {
        if (!condition) throw new EngineeringException(status, code, message);
    }
}
