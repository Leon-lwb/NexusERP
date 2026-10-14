package com.nexus.common.report;

/** 数据提供者仅向外返回稳定错误码，不泄露数据库错误或参数值。 */
public final class DatasetAccessException extends IllegalArgumentException {
    /** 业务编码。 */
    private final String code;

    /**
     * 初始化数据集访问异常并保存所需依赖或不可变值。
     *
     * @param code 业务编码
     * @param message 当前操作的 {@code message} 输入
     */
    public DatasetAccessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 完成 {@code code} 对应的数据集访问异常处理。
     *
     * @return 当前处理返回的固定结果
     */
    public String code() {
        return code;
    }
}
