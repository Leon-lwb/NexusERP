package com.nexus.common.ai.engineering;

/** 有界的执行阶段，与任务修订号分开管理。 */
public enum EngineeringStage {
    CHECKING,
    PREPARING,
    GENERATING,
    CHECKING_DRC,
    EXPORTING,
    UPLOADING,
    FINALIZING
}
