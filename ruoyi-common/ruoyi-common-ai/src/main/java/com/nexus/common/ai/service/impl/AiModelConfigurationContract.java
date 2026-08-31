package com.nexus.common.ai.service.impl;

import com.nexus.common.core.utils.sql.SqlUtil;
import com.nexus.common.mybatis.enums.DataBaseType;
import com.nexus.common.mybatis.helper.DataBaseHelper;

/**
 * AI 模型配置契约
 * <p>统一收敛 ai_model 表 status（0=启用/1=停用，字典 sys_normal_disable）与
 * is_default（Y/N，字典 sys_yes_no）字面量，禁止服务层散落魔法值；
 * 能力列（input_capabilities / output_capabilities，逗号分隔 CSV）的包含判断
 * 按数据库方言生成可移植 SQL 片段（模板与 {@link DataBaseHelper#findInSet} 同源，
 * 修改时务必同步维护）。</p>
 * <p>孪生契约：ruoyi-ai 模块的 com.nexus.ai.mcp.AiToolStatusContract（public，
 * 供 MCP/初始化器等跨包消费）与本类 ACTIVE/DISABLED/DEFAULT_YES/DEFAULT_NO 同源同值，
 * 修改任一处务必同步另一处。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
final class AiModelConfigurationContract {

    /** 启用（字典 sys_normal_disable：0=正常） */
    static final String ACTIVE = "0";

    /** 停用（字典 sys_normal_disable：1=停用） */
    static final String DISABLED = "1";

    /** 默认（字典 sys_yes_no：Y=是） */
    static final String DEFAULT_YES = "Y";

    /** 非默认（字典 sys_yes_no：N=否） */
    static final String DEFAULT_NO = "N";

    private AiModelConfigurationContract() {
    }

    /**
     * 构建能力列（逗号分隔 CSV）的可移植包含判断 SQL 片段（按当前数据源方言）
     *
     * @param capability 能力值（如 TEXT，字典 ai_model_capability）
     * @param column     能力列名（如 input_capabilities）
     * @return 按当前数据库方言生成的 SQL 条件片段（用于 QueryWrapper.apply）
     */
    static String capabilityExpression(String capability, String column) {
        return capabilityExpression(capability, column, DataBaseHelper.getDataBaseType());
    }

    /**
     * 构建能力列（逗号分隔 CSV）的可移植包含判断 SQL 片段（指定方言，纯函数无静态依赖）
     * <p>模板与 {@link DataBaseHelper#findInSet} 同源：Oracle 用 instr、
     * PostgreSQL 用 strpos、SQL Server 用 charindex、其余用 find_in_set；
     * 入参先经 {@link SqlUtil#filterKeyword} 敏感词过滤防注入。</p>
     *
     * @param capability 能力值（如 TEXT，字典 ai_model_capability）
     * @param column     能力列名（如 input_capabilities）
     * @param dbType     数据库方言
     * @return 指定方言的 SQL 条件片段（用于 QueryWrapper.apply）
     */
    static String capabilityExpression(String capability, String column, DataBaseType dbType) {
        SqlUtil.filterKeyword(capability);
        SqlUtil.filterKeyword(column);
        return switch (dbType) {
            // instr(',0,100,101,' , ',100,') <> 0
            case ORACLE -> "instr(','||%s||',' , ',%s,') <> 0".formatted(column, capability);
            // (select strpos(',0,100,101,' , ',100,')) <> 0
            case POSTGRE_SQL -> "(select strpos(','||%s||',' , ',%s,')) <> 0".formatted(column, capability);
            // charindex(',100,' , ',0,100,101,') <> 0
            case SQL_SERVER -> "charindex(',%s,' , ','+%s+',') <> 0".formatted(capability, column);
            // find_in_set(100 , '0,100,101')
            default -> "find_in_set('%s' , %s) <> 0".formatted(capability, column);
        };
    }
}
