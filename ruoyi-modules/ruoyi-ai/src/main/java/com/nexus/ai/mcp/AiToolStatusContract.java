package com.nexus.ai.mcp;

/**
 * AI 工具与资源状态契约
 * <p>统一收敛 ruoyi-ai 模块内 ai_tool 表 status（0=启用/1=停用，字典 sys_normal_disable）
 * 与 ai_prompt_template 表 is_default（Y/N，字典 sys_yes_no）字面量，禁止服务层散落魔法值。
 * 2026-08-27 迁移（script/sql/update/2026-08-27-ai-model-dictionary-provider-protocol.sql）
 * 已将 ai_tool.status 由 ENABLED/DISABLED 串归一为 0/1，本契约锁定该语义；
 * 与 common-ai 包内 AiModelConfigurationContract 同源同值（后者包级私有，跨模块不可见）。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
public final class AiToolStatusContract {

    /** 启用（字典 sys_normal_disable：0=正常） */
    public static final String ACTIVE = "0";

    /** 停用（字典 sys_normal_disable：1=停用） */
    public static final String DISABLED = "1";

    /** 默认（字典 sys_yes_no：Y=是） */
    public static final String DEFAULT_YES = "Y";

    /** 非默认（字典 sys_yes_no：N=否） */
    public static final String DEFAULT_NO = "N";

    private AiToolStatusContract() {
    }
}
