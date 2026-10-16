package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EngineeringTaskStateTest {
    @Test
    void transitionsAndTerminalStatesAreClosed() throws Exception {
        Class<?> state = Class.forName("com.nexus.common.ai.engineering.EngineeringTaskState");
        var transition = state.getMethod("canTransitionTo", state);
        var value = state.getMethod("valueOf", String.class);
        String[][] allowed = {
            {"CREATED", "WAITING_DEVICE"},
            {"CREATED", "FAILED"},
            {"CREATED", "CANCELED"},
            {"WAITING_DEVICE", "RUNNING"},
            {"WAITING_DEVICE", "CANCELED"},
            {"WAITING_DEVICE", "EXPIRED"},
            {"RUNNING", "SUCCEEDED"},
            {"RUNNING", "PARTIAL_SUCCESS"},
            {"RUNNING", "FAILED"},
            {"RUNNING", "CANCELED"}
        };
        for (String[] edge : allowed)
            assertEquals(
                    true,
                    transition.invoke(value.invoke(null, edge[0]), value.invoke(null, edge[1])));
        for (String terminal :
                new String[] {"SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELED", "EXPIRED"}) {
            for (Object target : state.getEnumConstants())
                assertEquals(false, transition.invoke(value.invoke(null, terminal), target));
        }
        assertEquals(
                false,
                transition.invoke(
                        value.invoke(null, "RUNNING"), value.invoke(null, "WAITING_DEVICE")));
    }
}
