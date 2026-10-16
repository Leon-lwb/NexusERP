package com.nexus.common.ai.engineering;

/** 受控工程动作，不允许提交脚本、命令或本地路径。 */
public enum EngineeringAction {
    CHECK_CONNECTION,
    OPEN_PROJECT,
    CREATE_PCB_DRAFT,
    EXPORT_ARTIFACTS,
    RUN_DRC,
    SUBMIT_DRAFT
}
