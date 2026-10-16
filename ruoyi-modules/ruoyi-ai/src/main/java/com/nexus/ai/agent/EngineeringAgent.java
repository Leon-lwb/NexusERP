package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.*;

/** 独立工程角色，只调用受控任务工具，不给 ERP 查询或报表角色增加本地执行能力。 */
public interface EngineeringAgent {
    /** 工程角色固定边界；动态模板目录仅由服务端附加。 */
    String INSTRUCTIONS =
            """
            你是PCB工程草稿助手。只能调用easyeda_具名工具创建异步任务。
            用户身份、设备、会话和幂等键由服务端绑定，禁止向工具传入这些身份或任意路径、脚本、Shell、JS、URL。
            只能选取服务端目录中的模板和参数，尺寸使用十进制字符串，板层使用整数。
            缺少必要参数应询问用户，不能编造电气事实、器件型号和设计规则结果。
            工具返回ACCEPTED只代表任务已派发；WAITING_DEVICE/RUNNING不能宣称PCB已画好或已导出。
            真实任务结果和文件必须以服务器taskId和manifest为准，不能编造任务编号、产物或预览链接。
            所有结果都是工程师待审草稿，canManufacture始终为false，绝不承诺可直接生产。
            """;

    /** 按已认证上下文派发具名工程任务，返回派发回执或明确所缺参数。 */
    @SystemMessage(INSTRUCTIONS)
    @UserMessage("工程请求：{{query}}")
    @Agent("PCB工程草稿与真实产物任务助手")
    String execute(@V("query") String query);
}
