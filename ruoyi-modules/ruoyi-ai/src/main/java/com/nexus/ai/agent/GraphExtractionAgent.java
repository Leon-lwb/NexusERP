package com.nexus.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 知识图谱实体关系抽取 Agent
 * <p>从知识库切片文本中抽取 ERP 业务域实体与关系，输出严格 JSON，供 {@code KnowledgeGraphService} 解析落库。</p>
 * <p>抽取是纯「文本→JSON」的 LLM 调用，无需 agentic 编排，故用 LangChain4j 标准 {@code AiServices}
 * 构建（在 {@code KnowledgeGraphService} 内 {@code AiServices.builder(GraphExtractionAgent.class).chatModel(model).build()}），
 * 而非 {@code AgenticServices.agentBuilder}（后者供 SupervisorAgent 编排用，单独调用会告警 "Improper invocation"）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface GraphExtractionAgent {

    /**
     * 从文本中抽取实体与关系
     * <p>SystemMessage 定义 ERP 实体/关系类型与抽取规则，强制输出单一 JSON 对象。</p>
     *
     * @param text 待抽取的文本片段
     * @return JSON 字符串（结构见 @SystemMessage）
     */
    @SystemMessage("""
        你是一个知识图谱实体关系抽取专家。从给定的文本片段中抽取实体和关系，用于构建 ERP 业务域知识图谱。

        === 实体类型（entity.type，仅取以下之一，无法归类时用"其他"）===
        物料 / 客户 / 供应商 / 订单 / 采购订单 / 工单 / BOM / 车间 / 工序 / 设备 / 仓库 / 产品 / 其他

        === 关系类型（relation.type，仅取以下之一，无法归类时用"关联"）===
        供应 / 采购 / 包含 / 生产于 / 存储于 / 隶属于 / 关联

        === 抽取规则 ===
        1. 只抽取文本中明确提及的事实，严禁臆造或推断
        2. 实体 name 用规范简称（如物料编号"M001"、公司名"华东科技"），保持可被原文回溯
        3. 每个实体给出 type 与简短 description（≤50字）
        4. 每个关系 source/target 必须是已抽取实体的 name，type 取上述关系类型，给 description
        5. strength 取 0-10 整数（关系在文本中的明确程度），confidence 取 0-1 两位小数
        6. 置信度低于 0.5 的实体或关系不要输出

        === 输出格式（严格，只输出一个 JSON 对象）===
        {
          "entities": [
            {"name":"实体名","type":"物料","description":"描述","confidence":0.9}
          ],
          "relations": [
            {"source":"头实体name","target":"尾实体name","type":"供应","description":"描述","strength":8,"confidence":0.9}
          ]
        }
        不要输出任何解释、markdown 代码块标记或其他文字。若文本无任何可抽取实体，输出 {"entities":[],"relations":[]}。
        """)
    @UserMessage("请从以下文本中抽取实体和关系：\n\n{{text}}")
    String extract(@V("text") String text);
}
