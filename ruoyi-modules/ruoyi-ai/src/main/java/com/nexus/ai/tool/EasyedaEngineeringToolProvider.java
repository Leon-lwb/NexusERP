package com.nexus.ai.tool;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 独立工程类别提供者，仅显式授权的工程 Agent 可获得具名任务工具。 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EasyedaEngineeringToolProvider implements BuiltinToolProvider {
    /** 返回与工具表一致的稳定名称。 */
    public String getToolName() {
        return "easyeda_engineering";
    }

    /** 中文显示名称。 */
    public String getDisplayName() {
        return "嘉立创EDA工程工具";
    }

    /** 只描述实际任务能力，不承诺自动投产。 */
    public String getDescription() {
        return "受控工程草稿、连接检查、打开、导出、DRC与私有归档，工具调用只派发任务";
    }

    /** 工程工具 Bean 类型。 */
    public Class<?> getToolClass() {
        return EasyedaEngineeringTool.class;
    }

    /** 独立 ENGINEERING 类别，禁止挂到 QUERY/REPORT/ALL。 */
    public String getCategories() {
        return "ENGINEERING";
    }
}
