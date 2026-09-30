package com.nexus.erp.prd.service;

import com.nexus.erp.prd.controller.PrdCapacityCalendarController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PrdCapacityCalendarControllerContractTest {
    @Test void unsafeManualOccupyAndReleaseAreNotHttpRoutes() {
        Set<String> forbidden = Set.of("/occupy", "/release");
        assertTrue(Arrays.stream(PrdCapacityCalendarController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(PutMapping.class))
            .filter(annotation -> annotation != null)
            .flatMap(annotation -> Arrays.stream(annotation.value()))
            .noneMatch(forbidden::contains));
    }
}
