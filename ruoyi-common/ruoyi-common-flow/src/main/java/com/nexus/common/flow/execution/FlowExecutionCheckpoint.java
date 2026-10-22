package com.nexus.common.flow.execution;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.flow.domain.entity.FlowNodeRun;

import java.util.*;

/** 冻结的完整调度检查点，含循环和批次根状态。 */
public record FlowExecutionCheckpoint(
        ObjectNode state,
        Map<String, Integer> remaining,
        List<String> queue,
        Map<String, FlowNodeRun> nodeRuns,
        Set<String> succeeded,
        int executed,
        String currentNode,
        String waitingNode,
        Long wakeAt,
        Map<String, Long> waits,
        List<String> inFlightNodes) {}
