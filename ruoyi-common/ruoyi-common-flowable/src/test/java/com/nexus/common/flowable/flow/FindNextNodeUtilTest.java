package com.nexus.common.flowable.flow;

import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FindNextNodeUtil 网关条件表达式求值与流程图遍历单元测试。
 * <p>守护历史回归：
 * 1) 旧实现手工截取变量名做文本替换，`${approved}`、`count &gt; 3`（带空格）等写法全部求值失败；
 * 2) next() 递归无 visited 集合，非用户任务构成的环会导致 StackOverflowError；
 * 3) 表达式不含 ${} 包裹时 substring(lastIndexOf("{")+1, lastIndexOf("}")) 抛越界异常。</p>
 */
class FindNextNodeUtilTest {

    private Map<String, Object> vars(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ==================== evalCondition：条件表达式求值 ====================

    @Test
    @DisplayName("无条件表达式视为可通过")
    void blankExpressionPasses() {
        assertTrue(FindNextNodeUtil.evalCondition(vars(), null));
        assertTrue(FindNextNodeUtil.evalCondition(vars(), "  "));
    }

    @Test
    @DisplayName("布尔变量标准写法 ${approved}")
    void booleanVariableWrappedInBraces() {
        assertTrue(FindNextNodeUtil.evalCondition(vars("approved", true), "${approved}"));
        assertFalse(FindNextNodeUtil.evalCondition(vars("approved", false), "${approved}"));
    }

    @Test
    @DisplayName("比较运算符带空格的写法（旧实现回归场景）")
    void comparisonWithSpaces() {
        assertTrue(FindNextNodeUtil.evalCondition(vars("count", 5), "${count > 3}"));
        assertFalse(FindNextNodeUtil.evalCondition(vars("count", 2), "${count > 3}"));
        assertTrue(FindNextNodeUtil.evalCondition(vars("count", 5), "${ count == 5 }"));
        assertTrue(FindNextNodeUtil.evalCondition(vars("count", 2), "${count <= 3}"));
        assertTrue(FindNextNodeUtil.evalCondition(vars("count", 2), "${count<3}"));
    }

    @Test
    @DisplayName("无花括号包裹的裸表达式不抛越界异常")
    void bareExpressionWithoutBraces() {
        // 旧实现 lastIndexOf("}") = -1 时 substring 抛 StringIndexOutOfBoundsException
        assertDoesNotThrow(() -> FindNextNodeUtil.evalCondition(vars("count", 5), "count>3"));
        assertTrue(FindNextNodeUtil.evalCondition(vars("count", 5), "count > 3"));
    }

    @Test
    @DisplayName("逻辑组合与括号分组")
    void logicalCombination() {
        Map<String, Object> v = vars("a", true, "b", false, "n", 10);
        assertTrue(FindNextNodeUtil.evalCondition(v, "${a && n >= 10}"));
        assertTrue(FindNextNodeUtil.evalCondition(v, "${b || n == 10}"));
        assertFalse(FindNextNodeUtil.evalCondition(v, "${a && b}"));
    }

    @Test
    @DisplayName("字符串比较（FEL 字符串字面量用双引号）")
    void stringComparison() {
        assertTrue(FindNextNodeUtil.evalCondition(vars("status", "pass"), "${status == \"pass\"}"));
    }

    @Test
    @DisplayName("未知变量求值失败时视为不可通过而非抛异常")
    void unknownVariableFailsSafe() {
        assertDoesNotThrow(() -> assertFalse(FindNextNodeUtil.evalCondition(vars(), "${missing == 1}")));
    }

    @Test
    @DisplayName("result 对合法表达式返回求值结果，非法表达式返回 null")
    void resultBehavior() {
        assertEquals(Boolean.TRUE, FindNextNodeUtil.result(vars("ok", true), "ok"));
        assertNull(FindNextNodeUtil.result(vars("x", 1), "this is not valid fel !!!"));
    }

    // ==================== GetExclusiveGatewayExpression ====================

    @Test
    @DisplayName("网关出线含 ${approved} 条件时识别为通用审批网关")
    void gatewayExpressionDetection() {
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        SequenceFlow flow = new SequenceFlow("gw", "task1") {{ setId("f1"); }};
        flow.setConditionExpression("${approved}");
        gateway.setOutgoingFlows(Arrays.asList(flow));

        assertTrue(FindNextNodeUtil.GetExclusiveGatewayExpression(gateway));
    }

    @Test
    @DisplayName("网关出线无 ${approved} 条件时返回 false")
    void gatewayWithoutApprovedExpression() {
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        SequenceFlow flow = new SequenceFlow("gw", "task1") {{ setId("f1"); }};
        flow.setConditionExpression("${count > 3}");
        gateway.setOutgoingFlows(Arrays.asList(flow));

        assertFalse(FindNextNodeUtil.GetExclusiveGatewayExpression(gateway));
    }

    // ==================== next()：遍历与环防护 ====================

    /**
     * 构造「排他网关 A ↔ 排他网关 B」互相指出的环（无任何用户任务）。
     * 旧实现在此场景无限递归直至 StackOverflowError；新实现应安全终止并返回空列表。
     */
    @Test
    @DisplayName("非用户任务构成的环不会导致 StackOverflowError")
    void gatewayLoopDoesNotStackOverflow() {
        ExclusiveGateway gwA = new ExclusiveGateway();
        gwA.setId("gwA");
        ExclusiveGateway gwB = new ExclusiveGateway();
        gwB.setId("gwB");

        SequenceFlow ab = new SequenceFlow("gwA", "gwB") {{ setId("fAB"); }};
        ab.setTargetFlowElement(gwB);
        gwA.setOutgoingFlows(new ArrayList<>(Arrays.asList(ab)));

        SequenceFlow ba = new SequenceFlow("gwB", "gwA") {{ setId("fBA"); }};
        ba.setTargetFlowElement(gwA);
        gwB.setOutgoingFlows(new ArrayList<>(Arrays.asList(ba)));

        Collection<org.flowable.bpmn.model.FlowElement> elements =
            Arrays.asList(gwA, gwB);

        List<UserTask> nextUser = new ArrayList<>();
        assertDoesNotThrow(() ->
            FindNextNodeUtil.next(elements, gwA, new HashMap<>(), nextUser));
        assertTrue(nextUser.isEmpty());
    }

    @Test
    @DisplayName("沿出线找到后续用户任务")
    void findsDownstreamUserTask() {
        ExclusiveGateway gw = new ExclusiveGateway();
        gw.setId("gw");
        UserTask task = new UserTask();
        task.setId("approveTask");

        SequenceFlow f1 = new SequenceFlow("gw", "approveTask") {{ setId("f1"); }};
        f1.setTargetFlowElement(task);
        gw.setOutgoingFlows(new ArrayList<>(Arrays.asList(f1)));
        // getFlowElementById 从集合按 ID 查找下一节点
        Collection<org.flowable.bpmn.model.FlowElement> elements =
            Arrays.asList(gw, task);

        List<UserTask> nextUser = new ArrayList<>();
        FindNextNodeUtil.next(elements, gw, vars("approved", true), nextUser);

        assertEquals(1, nextUser.size());
        assertEquals("approveTask", nextUser.get(0).getId());
    }

    @Test
    @DisplayName("条件为 false 的分支被跳过")
    void skipsFalseBranch() {
        ExclusiveGateway gw = new ExclusiveGateway();
        gw.setId("gw");
        UserTask approve = new UserTask();
        approve.setId("approve");
        UserTask reject = new UserTask();
        reject.setId("reject");

        SequenceFlow toApprove = new SequenceFlow("gw", "approve") {{ setId("t1"); }};
        toApprove.setConditionExpression("${approved}");
        toApprove.setTargetFlowElement(approve);
        // t2 无条件表达式：默认分支，条件分支不可通行时应继续走它
        SequenceFlow toReject = new SequenceFlow("gw", "reject") {{ setId("t2"); }};
        toReject.setTargetFlowElement(reject);
        gw.setOutgoingFlows(new ArrayList<>(Arrays.asList(toApprove, toReject)));

        Collection<org.flowable.bpmn.model.FlowElement> elements =
            Arrays.asList(gw, approve, reject);

        List<UserTask> nextUser = new ArrayList<>();
        FindNextNodeUtil.next(elements, gw, vars("approved", false), nextUser);

        assertEquals(1, nextUser.size());
        assertEquals("reject", nextUser.get(0).getId());
    }
}
