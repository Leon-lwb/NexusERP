package com.nexus.common.ai.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 实际自动生成映射器的返回标记回归，不启动服务，不把旧空版本升级为新版。 */
class AiDecisionRunContractMarkerMappingTest {
    @Test
    void actualGeneratedMapperAndJsonPreserveNewRunMarker() throws Exception {
        AiDecisionRun source = source(2);
        AiDecisionRunVo view = convert(source);
        var field = new ObjectMapper().findAndRegisterModules().valueToTree(view).get("decisionContractVersion");
        assertNotNull(field, "实际返回VO不能遗漏新采购运行版本，否则页面将永久拒绝正确新合同");
        assertTrue(field.isInt(), "运行合同版本必须保持整数，不能字符串化为标称版本");
        assertEquals(2, field.intValue(), "真实生成映射器应保留实体版本二");
    }

    @Test
    void nullableLegacyVersionIsNotInventedByMapping() throws Exception {
        var field = new ObjectMapper().findAndRegisterModules().valueToTree(convert(source(null)))
            .get("decisionContractVersion");
        assertNotNull(field, "视图应明确承载可空合同标记，不让旧空版本被隐藏后推断为新合同");
        assertTrue(field.isNull(), "旧运行的空版本必须保持空，不能默认补成二");
    }

    @Test
    void legacyExplicitVersionIsNotUpgraded() throws Exception {
        var field = new ObjectMapper().findAndRegisterModules().valueToTree(convert(source(1)))
            .get("decisionContractVersion");
        assertNotNull(field, "显式旧版本应能如实展示");
        assertEquals(1, field.intValue(), "旧版本一不能被VO映射改为新版二");
    }

    private AiDecisionRun source(Integer version) {
        AiDecisionRun entity = new AiDecisionRun();
        entity.setDecisionContractVersion(version);
        entity.setStatus("WAITING_APPROVAL");
        return entity;
    }

    private AiDecisionRunVo convert(AiDecisionRun source) throws Exception {
        // 反射只为让旧版本可真正执行红测试；调用的是当前构建生成的真实转换器，不复制转换算法。
        Class<?> type = Class.forName("com.nexus.common.ai.domain.entity.AiDecisionRunToAiDecisionRunVoMapperImpl");
        Object mapper = type.getDeclaredConstructor().newInstance();
        return (AiDecisionRunVo) type.getMethod("convert", AiDecisionRun.class).invoke(mapper, source);
    }
}
