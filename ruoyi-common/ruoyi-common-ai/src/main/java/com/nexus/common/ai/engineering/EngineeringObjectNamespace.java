package com.nexus.common.ai.engineering;

/** 工程私有对象命名空间，通用 OSS 查询和下载接口必须排除此范围。 */
public final class EngineeringObjectNamespace {
    private EngineeringObjectNamespace() {}

    /** 判断内部对象键是否位于保留工程前缀。 */
    public static boolean isEngineering(String key) {
        return key != null && key.startsWith("engineering/");
    }

    /** 根据服务端身份和编号生成对象键，所有输入均来自可信上下文。 */
    public static String key(EngineeringActor actor, Long task, Long artifact) {
        if (actor == null
                || actor.tenantId() == null
                || !actor.tenantId().matches("[A-Za-z0-9_-]{1,20}")
                || actor.userId() == null
                || actor.userId() <= 0
                || task == null
                || task <= 0
                || artifact == null
                || artifact <= 0) throw new IllegalArgumentException("工程对象身份无效");
        return "engineering/"
                + actor.tenantId()
                + "/"
                + actor.userId()
                + "/"
                + task
                + "/"
                + artifact;
    }
}
