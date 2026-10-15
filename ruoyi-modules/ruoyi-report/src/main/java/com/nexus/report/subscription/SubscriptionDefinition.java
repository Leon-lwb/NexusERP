package com.nexus.report.subscription;

import java.util.*;

/**
 * 固定发布报告、接收人、参数规则及显式时间语义的自有订阅定义。
 *
 * @param formatVersion 契约格式版本
 * @param reportVersionId 绑定的不可变报表发布版本
 * @param format 受控格式
 * @param parameterRules 当前契约的parameterRules配置或状态
 * @param recipients 独立生成和领取文件的接收人列表
 * @param cron 受控cron表达式
 * @param timezone 计划日期计算使用的明确时区
 * @param dstPolicy 当前契约的dstPolicy配置或状态
 * @param catchupSeconds 允许补跑的时间窗口秒数
 * @param notifyOnReady 文件就绪时的通知偏好
 */
public record SubscriptionDefinition(
        int formatVersion,
        String reportVersionId,
        String format,
        Map<String, ParameterRule> parameterRules,
        List<String> recipients,
        String cron,
        String timezone,
        String dstPolicy,
        int catchupSeconds,
        boolean notifyOnReady) {
    /**
     * 参数和接收人集合发布后不可变。
     *
     * @param formatVersion 契约格式版本
     * @param reportVersionId 绑定的不可变报表发布版本
     * @param format 受控格式
     * @param parameterRules 当前契约的parameterRules配置或状态
     * @param recipients 独立生成和领取文件的接收人列表
     * @param cron 受控cron表达式
     * @param timezone 计划日期计算使用的明确时区
     * @param dstPolicy 当前契约的dstPolicy配置或状态
     * @param catchupSeconds 允许补跑的时间窗口秒数
     * @param notifyOnReady 文件就绪时的通知偏好
     */
    public SubscriptionDefinition {
        parameterRules = Map.copyOf(parameterRules == null ? Map.of() : parameterRules);
        recipients = List.copyOf(recipients == null ? List.of() : recipients);
    }

    /**
     * FIXED、DATE_RELATIVE或RECIPIENT_ID，禁止脚本和凭证输入。
     *
     * @param kind 当前契约的kind配置或状态
     * @param value 当前契约的value配置或状态
     */
    public record ParameterRule(String kind, String value) {}
}
