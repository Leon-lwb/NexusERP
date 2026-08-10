package com.nexus.ai.flow.node;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 节点参数 schema（对齐 n8n INodeProperties 的简化版）
 * <p>前端 {@code NodeParameterForm} 按 type 渲染表单；后端发布时校验 config 与 schema 对齐</p>
 *
 * @author NexusERP
 */
@Data
public class NodeParamSchema {

    /** 参数名 */
    private String name;

    /** 显示名 */
    private String label;

    /** 类型: string/number/boolean/options/textarea/json/conditionList */
    private String type;

    /** 默认值 */
    private String defaultValue;

    /** options 类型的候选 */
    private List<Map<String, String>> options;

    /** 是否必填 */
    private boolean required;

    /** 占位符 */
    private String placeholder;

    /** 编辑器增强标记: keyvalue（键值对行编辑，替代裸 JSON textarea） */
    private String editor;

    /** 候选来源: aiModel（AI 模型下拉）/ aiKnowledge（知识库下拉） */
    private String optionsSource;

    /** 扩展属性（节点类型自定义元数据，如 erp_tool 的工具参数定义 toolArgs） */
    private Map<String, Object> attrs;

    /** 参数说明（表单 hint，对齐 n8n ParameterInputHint；前端 NodeParameterForm 展示） */
    private String description;

    public NodeParamSchema() {
    }

    public NodeParamSchema(String name, String label, String type, boolean required) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.required = required;
    }

    public NodeParamSchema(String name, String label, String type, String defaultValue, boolean required) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.required = required;
    }
}
