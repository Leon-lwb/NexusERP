package com.nexus.common.cv.domain;

/** 可由 ERP 显式选择的工业缺陷模型场景；null 仅用于客户端兼容旧的通用检测。 */
public enum DefectScenario {
    METAL("defect_metal"),
    PCB("defect_pcb");

    private final String value;

    DefectScenario(String value) {
        this.value = value;
    }

    public static boolean isSupported(String scenario) {
        for (DefectScenario choice : values()) {
            if (choice.value.equals(scenario)) return true;
        }
        return false;
    }
}
