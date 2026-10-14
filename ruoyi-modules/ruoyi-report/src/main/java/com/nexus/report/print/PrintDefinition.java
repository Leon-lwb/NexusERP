package com.nexus.report.print;

import java.util.*;

/**
 * 第二版固定打印能力；一个增长明细区和一组复合分组键。
 *
 * @param orientation 当前契约的orientation配置或状态
 * @param groupBy 当前契约的groupBy配置或状态
 * @param repeatFooter 当前契约的repeatFooter配置或状态
 * @param media 当前契约的media配置或状态
 */
public record PrintDefinition(
        String orientation, List<String> groupBy, boolean repeatFooter, List<Media> media) {

    /**
     * 固定打印配置，未提供分组或媒体时使用空集合。
     *
     * @param orientation 当前契约的orientation配置或状态
     * @param groupBy 当前契约的groupBy配置或状态
     * @param repeatFooter 当前契约的repeatFooter配置或状态
     * @param media 当前契约的media配置或状态
     */
    public PrintDefinition {
        orientation = orientation == null ? "PORTRAIT" : orientation;
        groupBy = List.copyOf(groupBy == null ? List.of() : groupBy);
        media = List.copyOf(media == null ? List.of() : media);
    }

    /**
     * IMAGE绑定不可变私有资产版本，条码只绑定可信业务字段。
     *
     * @param kind 当前契约的kind配置或状态
     * @param assetVersionId 当前契约的assetVersionId配置或状态
     * @param field 已批准的逻辑字段
     * @param x 当前契约的x配置或状态
     * @param y 当前契约的y配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     * @param repeat 当前契约的repeat配置或状态
     */
    public record Media(
            String kind,
            String assetVersionId,
            String field,
            double x,
            double y,
            double width,
            double height,
            boolean repeat) {}
}
