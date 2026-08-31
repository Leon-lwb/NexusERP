package com.nexus.common.ai.service.impl;

import com.nexus.common.mybatis.enums.DataBaseType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 模型配置契约单元测试
 * <p>锁定 ai_model 状态/默认值的系统字典语义（status 0启用/1停用、is_default Y/N），
 * 以及能力列（逗号分隔 CSV）查询必须按数据库方言生成可移植的 FIND_IN_SET 表达式。
 * 方言断言使用各方言精确片段，防止实现被硬编码为单一方言。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelConfigurationContractTest {

    @Test
    void activeAndDefaultCodesMustUseSystemDictionaryValues() {
        assertEquals("0", AiModelConfigurationContract.ACTIVE);
        assertEquals("1", AiModelConfigurationContract.DISABLED);
        assertEquals("Y", AiModelConfigurationContract.DEFAULT_YES);
        assertEquals("N", AiModelConfigurationContract.DEFAULT_NO);
    }

    @Test
    void capabilityFilterMustUseDatabasePortableFindInSetExpression() {
        assertEquals("find_in_set('TEXT' , input_capabilities) <> 0",
            AiModelConfigurationContract.capabilityExpression("TEXT", "input_capabilities", DataBaseType.MY_SQL));
        assertEquals("find_in_set('VECTOR' , output_capabilities) <> 0",
            AiModelConfigurationContract.capabilityExpression("VECTOR", "output_capabilities", DataBaseType.MY_SQL));
    }

    @Test
    void capabilityFilterMustSwitchFragmentPerDialect() {
        assertEquals("instr(','||input_capabilities||',' , ',TEXT,') <> 0",
            AiModelConfigurationContract.capabilityExpression("TEXT", "input_capabilities", DataBaseType.ORACLE));
        assertEquals("(select strpos(','||input_capabilities||',' , ',TEXT,')) <> 0",
            AiModelConfigurationContract.capabilityExpression("TEXT", "input_capabilities", DataBaseType.POSTGRE_SQL));
        assertEquals("charindex(',TEXT,' , ','+input_capabilities+',') <> 0",
            AiModelConfigurationContract.capabilityExpression("TEXT", "input_capabilities", DataBaseType.SQL_SERVER));
    }
}
