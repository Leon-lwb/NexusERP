package com.nexus.report.dashboard;

import com.nexus.report.domain.ReportDsl;

import java.util.*;

/**
 * 自有仪表盘定义；组件版本、报告版本及参数映射在发布时固定。
 *
 * @param formatVersion 契约格式版本
 * @param title 当前契约的title配置或状态
 * @param parameters 固定参数契约
 * @param widgets 当前契约的widgets配置或状态
 * @param refreshSeconds 当前契约的refreshSeconds配置或状态
 */
public record DashboardDefinition(
        int formatVersion,
        String title,
        List<ReportDsl.Parameter> parameters,
        List<Widget> widgets,
        int refreshSeconds) {
    /**
     * 固定参数和组件列表，阻止发布对象被客户端修改。
     *
     * @param formatVersion 契约格式版本
     * @param title 当前契约的title配置或状态
     * @param parameters 固定参数契约
     * @param widgets 当前契约的widgets配置或状态
     * @param refreshSeconds 当前契约的refreshSeconds配置或状态
     */
    public DashboardDefinition {
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        widgets = List.copyOf(widgets == null ? List.of() : widgets);
    }

    /**
     * 组件只使用安装的类型及版本，没有HTML、JS表达式或远程执行器。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param type 受控类型
     * @param componentVersion 当前契约的componentVersion配置或状态
     * @param title 当前契约的title配置或状态
     * @param reportVersionId 绑定的不可变报表发布版本
     * @param parameterBindings 当前契约的parameterBindings配置或状态
     * @param categoryField 当前契约的categoryField配置或状态
     * @param valueField 当前契约的valueField配置或状态
     * @param function 白名单聚合函数
     * @param comparison 当前契约的comparison配置或状态
     * @param startParameter 当前契约的startParameter配置或状态
     * @param endParameter 当前契约的endParameter配置或状态
     * @param layout 当前契约的layout配置或状态
     * @param links 当前契约的links配置或状态
     */
    public record Widget(
            String id,
            String type,
            int componentVersion,
            String title,
            String reportVersionId,
            Map<String, String> parameterBindings,
            String categoryField,
            String valueField,
            String function,
            String comparison,
            String startParameter,
            String endParameter,
            Layout layout,
            List<Link> links) {
        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param id 当前受控操作的id输入
         * @param type 当前受控操作的type输入
         * @param componentVersion 当前受控操作的componentVersion输入
         * @param title 当前受控操作的title输入
         * @param reportVersionId 当前受控操作的reportVersionId输入
         * @param parameterBindings 当前受控操作的parameterBindings输入
         * @param categoryField 当前受控操作的categoryField输入
         * @param valueField 当前受控操作的valueField输入
         * @param function 当前受控操作的function输入
         * @param comparison 当前受控操作的comparison输入
         * @param startParameter 当前受控操作的startParameter输入
         * @param endParameter 当前受控操作的endParameter输入
         * @param layout 当前受控操作的layout输入
         * @param links 当前受控操作的links输入
         */
        public Widget {
            parameterBindings =
                    Map.copyOf(parameterBindings == null ? Map.of() : parameterBindings);
            links = List.copyOf(links == null ? List.of() : links);
        }
    }

    /**
     * 十二列网格和移动显示顺序都经过服务器范围校验。
     *
     * @param x 当前契约的x配置或状态
     * @param y 当前契约的y配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     * @param mobileOrder 当前契约的mobileOrder配置或状态
     */
    public record Layout(int x, int y, int width, int height, int mobileOrder) {}

    /**
     * 联动只把已批准源字段传给目标组件的同类型参数。
     *
     * @param targetWidget 当前契约的targetWidget配置或状态
     * @param sourceField 当前契约的sourceField配置或状态
     * @param targetParameter 当前契约的targetParameter配置或状态
     */
    public record Link(String targetWidget, String sourceField, String targetParameter) {}
}
