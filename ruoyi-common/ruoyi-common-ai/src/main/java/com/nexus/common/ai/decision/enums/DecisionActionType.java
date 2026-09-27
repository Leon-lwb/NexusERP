package com.nexus.common.ai.decision.enums;

public enum DecisionActionType {
    SEND_EXPEDITE_NOTICE("sendExpediteNotice", "ai:decision:execute", false),
    CREATE_PURCHASE_REQUISITION_DRAFT("purchaseRequisitionDraft", "pu:requisition:add", true),
    CREATE_SCHEDULE_PLAN_DRAFT("schedulePlanDraft", "prd:schedule:run", true),
    SEND_MANAGER_NOTIFICATION("managerNotification", "ai:decision:execute", false);

    private final String executorCode;
    private final String permissionCode;
    private final boolean approvalRequired;

    DecisionActionType(String executorCode, String permissionCode, boolean approvalRequired) {
        this.executorCode = executorCode;
        this.permissionCode = permissionCode;
        this.approvalRequired = approvalRequired;
    }

    public String executorCode() {
        return executorCode;
    }

    public String permissionCode() {
        return permissionCode;
    }

    public boolean approvalRequired() {
        return approvalRequired;
    }
}
