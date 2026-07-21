package com.nexus.erp.prd.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 生产工单状态枚举
 * 集中管理状态值与合法转换矩阵, 替换散落的 String 常量
 */
public enum WoStatusEnum {

    CREATED("CREATED", "已创建"),
    APPROVING("APPROVING", "审批中"),
    APPROVED("APPROVED", "已批准"),
    RELEASED("RELEASED", "已下达"),
    IN_PROGRESS("IN_PROGRESS", "生产中"),
    COMPLETED("COMPLETED", "已完工"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    WoStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 获取状态编码
     *
     * @return 状态编码（如 CREATED、RELEASED）
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取状态中文名称
     *
     * @return 状态中文标签（如 已创建、已下达）
     */
    public String getLabel() {
        return label;
    }

    /**
     * 合法状态转换矩阵
     * key = 当前状态, value = 可转换到的状态集合
     */
    public boolean canTransitionTo(WoStatusEnum target) {
        Set<WoStatusEnum> allowed = switch (this) {
            case CREATED -> EnumSet.of(APPROVING, RELEASED, CLOSED);
            case APPROVING -> EnumSet.of(APPROVED, CREATED);
            case APPROVED -> EnumSet.of(RELEASED, CLOSED);
            case RELEASED -> EnumSet.of(IN_PROGRESS, CLOSED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CLOSED);
            case COMPLETED -> EnumSet.of(CLOSED);
            case CLOSED -> EnumSet.noneOf(WoStatusEnum.class);
        };
        return allowed.contains(target);
    }

    /**
     * 终态判断(不可再转换)
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /**
     * 根据状态编码解析对应的枚举值
     *
     * @param code 状态编码
     * @return 对应的工单状态枚举，编码为空返回 {@code null}
     * @throws IllegalArgumentException 当编码不匹配任何已知状态时抛出
     */
    public static WoStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (WoStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知工单状态: " + code);
    }
}
