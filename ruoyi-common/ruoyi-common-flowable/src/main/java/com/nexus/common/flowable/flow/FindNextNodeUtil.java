package com.nexus.common.flowable.flow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.greenpineyu.fel.context.FelContext;

import lombok.extern.slf4j.Slf4j;

import org.flowable.bpmn.model.Process;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author wanghuan
 * @date 2023-09-25
 */
@Slf4j
public class FindNextNodeUtil {

    /**
     * 获取下一步骤的用户任务
     *
     * @param repositoryService
     * @param map
     * @return
     */
    public static List<UserTask> getNextUserTasks(RepositoryService repositoryService, org.flowable.task.api.Task task, Map<String, Object> map) {
        List<UserTask> data = new ArrayList<>();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process mainProcess = bpmnModel.getMainProcess();
        Collection<FlowElement> flowElements = mainProcess.getFlowElements();
        String key = task.getTaskDefinitionKey();
        FlowElement flowElement = bpmnModel.getFlowElement(key);
        next(flowElements, flowElement, map, data);
        return data;
    }

    public static void next(Collection<FlowElement> flowElements, FlowElement flowElement, Map<String, Object> map, List<UserTask> nextUser) {
        next(flowElements, flowElement, map, nextUser, new HashSet<>());
    }

    /**
     * 沿连线向下递归查找后续用户任务。
     *
     * @param visited 已访问节点 ID 集合，流程图中存在非用户任务构成的环时终止递归，防止 StackOverflowError
     */
    private static void next(Collection<FlowElement> flowElements, FlowElement flowElement, Map<String, Object> map, List<UserTask> nextUser, Set<String> visited) {
        //如果是结束节点
        if (flowElement instanceof EndEvent) {
            //如果是子任务的结束节点
            FlowElement subProcess = getSubProcess(flowElements, flowElement);
            if (subProcess != null) {
                flowElement = subProcess;
            }
        }
        if (flowElement == null || !visited.add(flowElement.getId())) {
            // 同一节点已访问（流程图中存在环）或节点为空，直接终止该分支
            return;
        }
        //获取Task的出线信息--可以拥有多个
        List<SequenceFlow> outGoingFlows = null;
        if (flowElement instanceof Task) {
            outGoingFlows = ((Task) flowElement).getOutgoingFlows();
        } else if (flowElement instanceof Gateway) {
            outGoingFlows = ((Gateway) flowElement).getOutgoingFlows();
        } else if (flowElement instanceof StartEvent) {
            outGoingFlows = ((StartEvent) flowElement).getOutgoingFlows();
        } else if (flowElement instanceof SubProcess) {
            outGoingFlows = ((SubProcess) flowElement).getOutgoingFlows();
        } else if (flowElement instanceof CallActivity) {
            outGoingFlows = ((CallActivity) flowElement).getOutgoingFlows();
        }
        if (outGoingFlows != null && outGoingFlows.size() > 0) {
            //遍历所有的出线--找到可以正确执行的那一条
            for (SequenceFlow sequenceFlow : outGoingFlows) {
                //1.有表达式，且为true
                //2.无表达式
                if (evalCondition(map, sequenceFlow.getConditionExpression())) {
                    //出线的下一节点
                    String nextFlowElementID = sequenceFlow.getTargetRef();
                    if (checkSubProcess(nextFlowElementID, flowElements, nextUser)) {
                        continue;
                    }

                    //查询下一节点的信息
                    FlowElement nextFlowElement = getFlowElementById(nextFlowElementID, flowElements);
                    //调用流程
                    if (nextFlowElement instanceof CallActivity) {
                        CallActivity ca = (CallActivity) nextFlowElement;
                        if (ca.getLoopCharacteristics() != null) {
                            UserTask userTask = new UserTask();
                            userTask.setId(ca.getId());

                            userTask.setId(ca.getId());
                            userTask.setLoopCharacteristics(ca.getLoopCharacteristics());
                            userTask.setName(ca.getName());
                            nextUser.add(userTask);
                        }
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //用户任务
                    if (nextFlowElement instanceof UserTask) {
                        nextUser.add((UserTask) nextFlowElement);
                    }
                    //排他网关
                    else if (nextFlowElement instanceof ExclusiveGateway) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //并行网关
                    else if (nextFlowElement instanceof ParallelGateway) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //接收任务
                    else if (nextFlowElement instanceof ReceiveTask) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //服务任务
                    else if (nextFlowElement instanceof ServiceTask) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //子任务的起点
                    else if (nextFlowElement instanceof StartEvent) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                    //结束节点
                    else if (nextFlowElement instanceof EndEvent) {
                        next(flowElements, nextFlowElement, map, nextUser, visited);
                    }
                }
            }
        }
    }

    /**
     * 判断是否是多实例子流程并且需要设置集合类型变量
     */
    public static boolean checkSubProcess(String Id, Collection<FlowElement> flowElements, List<UserTask> nextUser) {
        for (FlowElement flowElement1 : flowElements) {
            if (flowElement1 instanceof SubProcess && flowElement1.getId().equals(Id)) {

                SubProcess sp = (SubProcess) flowElement1;
                if (sp.getLoopCharacteristics() != null) {
                    String inputDataItem = sp.getLoopCharacteristics().getInputDataItem();
                    UserTask userTask = new UserTask();
                    userTask.setId(sp.getId());
                    userTask.setLoopCharacteristics(sp.getLoopCharacteristics());
                    userTask.setName(sp.getName());
                    nextUser.add(userTask);
                    return true;
                }
            }
        }

        return false;

    }

    /**
     * 查询一个节点的是否子任务中的节点，如果是，返回子任务
     *
     * @param flowElements 全流程的节点集合
     * @param flowElement  当前节点
     * @return
     */
    public static FlowElement getSubProcess(Collection<FlowElement> flowElements, FlowElement flowElement) {
        for (FlowElement flowElement1 : flowElements) {
            if (flowElement1 instanceof SubProcess) {
                for (FlowElement flowElement2 : ((SubProcess) flowElement1).getFlowElements()) {
                    if (flowElement.equals(flowElement2)) {
                        return flowElement1;
                    }
                }
            }
        }
        return null;
    }


    /**
     * 根据ID查询流程节点对象, 如果是子任务，则返回子任务的开始节点
     *
     * @param Id           节点ID
     * @param flowElements 流程节点集合
     * @return
     */
    public static FlowElement getFlowElementById(String Id, Collection<FlowElement> flowElements) {
        for (FlowElement flowElement : flowElements) {
            if (flowElement.getId().equals(Id)) {
                //如果是子任务，则查询出子任务的开始节点
                if (flowElement instanceof SubProcess) {
                    return getStartFlowElement(((SubProcess) flowElement).getFlowElements());
                }
                return flowElement;
            }
            if (flowElement instanceof SubProcess) {
                FlowElement flowElement1 = getFlowElementById(Id, ((SubProcess) flowElement).getFlowElements());
                if (flowElement1 != null) {
                    return flowElement1;
                }
            }
        }
        return null;
    }

    /**
     * 返回流程的开始节点
     *
     * @param flowElements 节点集合
     * @description:
     */
    public static FlowElement getStartFlowElement(Collection<FlowElement> flowElements) {
        for (FlowElement flowElement : flowElements) {
            if (flowElement instanceof StartEvent) {
                return flowElement;
            }
        }
        return null;
    }

    /**
     * 校验el表达式
     *
     * @param map
     * @param expression
     * @return
     */
    public static boolean expressionResult(Map<String, Object> map, String expression) {
        Expression exp = AviatorEvaluator.compile(expression);
        final Object execute = exp.execute(map);
        return Boolean.parseBoolean(String.valueOf(execute));
    }

    /**
     * 校验el表达示例
     *
     * @param map        流程变量
     * @param expression 条件表达式（不含 ${} 包裹）
     * @return 求值结果，失败时返回 null
     */
    public static Object result(Map<String, Object> map, String expression) {
        if (StringUtils.isBlank(expression)) {
            return null;
        }
    	try {
	        FelEngine fel = new FelEngineImpl();
	        FelContext ctx = fel.getContext();
	        for (Map.Entry<String, Object> entry : map.entrySet()) {
	            ctx.set(entry.getKey(), entry.getValue());
	        }
	        // 变量已全部放入 FEL 上下文，直接对原始表达式求值。
	        // 切勿手工截取变量名再做文本替换：`${approved}`、`count > 3`（带空格）等写法会截取出空串/带空格的变量名而求值失败
	        return fel.eval(expression);
		} catch (Exception e) {
			log.warn("流程网关条件表达式求值失败: {}, 变量key: {}", expression, map.keySet(), e);
			return null;
		}
    }

    /**
     * 求值网关连线条件表达式（自动剥离 ${}/#{} 包裹），返回是否可通过。
     * 无条件表达式视为可通过；求值失败视为不通过。
     *
     * @param map            流程变量
     * @param rawExpression  连线上的原始条件表达式（如 "${approved}"、"#{count > 3}"）
     */
    public static boolean evalCondition(Map<String, Object> map, String rawExpression) {
        if (StringUtils.isBlank(rawExpression)) {
            return true;
        }
        Object result = result(map, stripBraces(rawExpression.trim()));
        return result != null && Boolean.parseBoolean(String.valueOf(result));
    }

    /**
     * 剥离表达式最外层的 ${} / #{} 包裹；无花括号时原样返回（裸表达式）
     */
    private static String stripBraces(String expression) {
        int start = expression.lastIndexOf('{');
        int end = expression.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return expression.substring(start + 1, end);
        }
        return expression;
    }

    /**
     * 获取排他网关分支名称、分支表达式是否存在${approved}
     * @param flowElement
     * @param
     * add by wanghuan
     */
    public static boolean GetExclusiveGatewayExpression(FlowElement flowElement) {
    	// 获取所有网关分支
        List<SequenceFlow> targetFlows=((ExclusiveGateway)flowElement).getOutgoingFlows();
        // 循环每个网关分支
        for(SequenceFlow sequenceFlow : targetFlows){
            // 获取下一个网关和节点数据
            FlowElement targetFlowElement=sequenceFlow.getTargetFlowElement();
            // 网关数据不为空
            if (StringUtils.isNotBlank(sequenceFlow.getConditionExpression())) {
                // 获取网关判断条件
            	String expression = sequenceFlow.getConditionExpression();
            	if(expression.contains("${approved}")) {
            		return true;
            	}
            }
        }
    	return false;
    }
}
