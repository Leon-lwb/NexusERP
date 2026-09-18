package com.nexus.common.core.constant;

/**
 * 系统常量信息
 *
 * @author Lion Li
 */
public interface SystemConstants {

    /**
     * 正常状态
     */
    String NORMAL = "0";

    /**
     * 异常状态
     */
    String DISABLE = "1";

    /**
     * 是否为系统默认（是）
     */
    String YES = "Y";

    /**
     * 是否为系统默认（否）
     */
    String NO = "N";

    /**
     * 是否菜单外链（是）
     */
    String YES_FRAME = "0";

    /**
     * 是否菜单外链（否）
     */
    String NO_FRAME = "1";

    /**
     * 菜单类型（目录）
     */
    String TYPE_DIR = "M";

    /**
     * 菜单类型（菜单）
     */
    String TYPE_MENU = "C";

    /**
     * 菜单类型（按钮）
     */
    String TYPE_BUTTON = "F";

    /**
     * Layout组件标识
     */
    String LAYOUT = "Layout";

    /**
     * ParentView组件标识
     */
    String PARENT_VIEW = "ParentView";

    /**
     * InnerLink组件标识
     */
    String INNER_LINK = "InnerLink";

    /**
     * 超级管理员ID
     */
    Long SUPER_ADMIN_ID = 1L;

    /**
     * 根部门祖级列表
     */
    String ROOT_DEPT_ANCESTORS = "0";

    /**
     * 默认部门 ID
     */
    Long DEFAULT_DEPT_ID = 100L;

    /**
     * 排除敏感属性字段（操作日志/参数序列化全局脱敏：密码类 + 密钥类 + 二级认证凭证）
     * <p>{@code credential}：电子签章二级认证凭证（签署人登录密码），随 NCR 处置签署/文档签章等
     * 请求体提交，若不排除会被操作日志切面明文写入 {@code sys_oper_log.oper_param}。</p>
     */
    String[] EXCLUDE_PROPERTIES = { "password", "oldPassword", "newPassword", "confirmPassword",
        "apiKey", "apiSecret", "secretKey", "secret", "token", "accessToken", "clientSecret", "privateKey",
        "credential" };


}
