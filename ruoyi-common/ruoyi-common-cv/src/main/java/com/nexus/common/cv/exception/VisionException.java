package com.nexus.common.cv.exception;

/**
 * 视觉服务调用异常
 * <p>统一包装 nexus-vision-service 的超时/4xx/5xx/服务不可用等错误,供业务层捕获降级</p>
 *
 * @author nexus
 * @since 1.0.0
 */
public class VisionException extends RuntimeException {

    /** 业务错误码(如上游返回的 code) */
    private final String code;

    /** 上游 HTTP 状态码(0 表示未产生 HTTP 响应,如超时/连接失败) */
    private final int httpStatus;

    /**
     * 通用构造(业务错误码 VISION_ERROR,无 HTTP 状态)
     *
     * @param message 错误描述
     */
    public VisionException(String message) {
        super(message);
        this.code = "VISION_ERROR";
        this.httpStatus = 0;
    }

    /**
     * 通用构造(携带原因,业务错误码 VISION_ERROR,无 HTTP 状态)
     *
     * @param message 错误描述
     * @param cause   原始异常
     */
    public VisionException(String message, Throwable cause) {
        super(message, cause);
        this.code = "VISION_ERROR";
        this.httpStatus = 0;
    }

    /**
     * 完整构造(含上游 HTTP 状态与业务错误码)
     *
     * @param message    错误描述
     * @param httpStatus 上游 HTTP 状态码(0 表示未产生 HTTP 响应)
     * @param code       业务错误码
     */
    public VisionException(String message, int httpStatus, String code) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * 业务错误码(如上游返回的 code)
     */
    public String getCode() {
        return code;
    }

    /**
     * 上游 HTTP 状态码(0 表示未产生 HTTP 响应,如超时/连接失败)
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
