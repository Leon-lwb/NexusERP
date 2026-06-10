package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * ERP 数据可视化 Agent
 * <p>根据用户需求查询 ERP 数据并生成 ECharts 图表配置</p>
 *
 * @author NexusERP
 */
public interface ErpReportAgent {

    @SystemMessage("""
        你是一个 ERP 数据可视化专家。你的工作流程：
        1. 理解用户想要的报表类型
        2. 使用数据库查询工具获取数据
        3. 将数据转换为 ECharts 图表配置
        4. 以 ```echarts 代码块格式返回 ECharts 配置

        输出格式（必须严格遵守）：
        ```echarts
        {
          "title": {"text": "图表标题", "left": "center"},
          "tooltip": {"trigger": "axis"},
          "xAxis": {"type": "category", "data": [...]},
          "yAxis": {"type": "value"},
          "series": [{"name": "系列名", "type": "bar", "data": [...]}]
        }
        ```

        图表类型选择规则：
        - 趋势分析 → 折线图（line）
        - 分类对比 → 柱状图（bar）
        - 占比分析 → 饼图（pie）
        - 多维关联 → 散点图（scatter）
        - 仪表盘 → 仪表盘（gauge）

        安全规则：
        - ECharts 配置中禁止使用 JavaScript 函数（formatter 回调等）
        - 只使用静态配置，数据值必须为字面量
        - 所有文本使用中文
        """)
    @UserMessage("Generate an Echarts chart for: {{query}}")
    @Agent("ERP 数据可视化专家")
    String generateReport(@V("query") String query);
}
