package com.nexus.workflow.service.impl;

import com.nexus.common.flowable.utils.ModelUtils;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.util.io.StringStreamSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证「节点级签章要求」所依赖的平台前提：用户任务上的自定义扩展属性
 * {@code flowable:needSign} 在 BPMN 解析（部署）阶段被原样保留，可用
 * {@link ModelUtils#getUserTaskAttributeValue} 按节点 Key 读取；未设置的节点则不产生该属性。
 *
 * <p>这是 {@code WfTaskServiceImpl} 服务端读取节点签章要求的正确性基础：若解析器丢弃未知的
 * {@code flowable:*} 属性，关卡就会永远读不到节点配置（静默失效）。此处以真实 BPMN XML 走一次
 * {@link BpmnXMLConverter}（与引擎部署同一套转换器，与 {@code flowable:localScope} 同路径）固化该前提。</p>
 *
 * @author nexus
 */
class NeedSignBpmnAttributeTest {

    private static final String BPMN_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n"
        + "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
        + "             xmlns:flowable=\"http://flowable.org/bpmn\"\n"
        + "             targetNamespace=\"http://nexus/test\">\n"
        + "  <process id=\"esignProcess\" name=\"签章测试流程\" isExecutable=\"true\">\n"
        + "    <startEvent id=\"start\"/>\n"
        + "    <userTask id=\"approve\" name=\"部门审批\" flowable:needSign=\"true\" flowable:localScope=\"true\"/>\n"
        + "    <userTask id=\"countersign\" name=\"会签\" flowable:needSign=\"false\"/>\n"
        + "    <userTask id=\"other\" name=\"普通节点\"/>\n"
        + "    <endEvent id=\"end\"/>\n"
        + "  </process>\n"
        + "</definitions>\n";

    private static BpmnModel parse() {
        return new BpmnXMLConverter().convertToBpmnModel(new StringStreamSource(BPMN_XML), false, false);
    }

    @Test
    @DisplayName("flowable:needSign 随 BPMN 解析保留，可读回 true/false 两种取值")
    void attributeSurvivesBpmnParsing() {
        BpmnModel model = parse();
        assertEquals("true", ModelUtils.getUserTaskAttributeValue(model, "approve", ApprovalSignGate.NEED_SIGN_ATTRIBUTE));
        assertEquals("false", ModelUtils.getUserTaskAttributeValue(model, "countersign", ApprovalSignGate.NEED_SIGN_ATTRIBUTE));
    }

    @Test
    @DisplayName("未设置该属性的节点读回 null：「属性存在」才是可靠信号，存量节点不受影响")
    void absentAttributeReadsNull() {
        BpmnModel model = parse();
        assertNull(ModelUtils.getUserTaskAttributeValue(model, "other", ApprovalSignGate.NEED_SIGN_ATTRIBUTE));
    }

    @Test
    @DisplayName("同族的 localScope 与本属性互不影响，可同时挂在一个节点上")
    void coexistsWithLocalScope() {
        BpmnModel model = parse();
        assertEquals("true", ModelUtils.getUserTaskAttributeValue(model, "approve", "localScope"));
        assertNull(ModelUtils.getUserTaskAttributeValue(model, "countersign", "localScope"));
    }
}
