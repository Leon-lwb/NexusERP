package com.nexus.erp.fin.cost.poster;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产入库成本方向回归测试，仅调用分类函数，不访问过账依赖或运行态。
 */
@Tag("dev")
class CostPosterProductionInDirectionTest {

    private final CostPoster poster = new CostPoster(null, null, null, null, null, null, null);

    @Test
    void productionInIsInbound() throws ReflectiveOperationException {
        assertTrue(classify("isIn", "PRODUCTION_IN"), "生产入库必须识别为入库，不能进入出库分支");
    }

    @Test
    void productionInIsNotSkipped() throws ReflectiveOperationException {
        assertFalse(classify("isSkip", "PRODUCTION_IN"), "生产入库不能作为内部调拨流水跳过");
    }

    @ParameterizedTest
    @CsvSource({
        "RECEIVE_IN, true, false",
        "MISC_IN, true, false",
        "TARGET_IN, true, false",
        "PRODUCTION_ISSUE, false, false",
        "SALES_OUT, false, false",
        "MISC_OUT, false, false",
        "VIRTUAL_OUT, false, true",
        "TRANSFER_IN, false, true",
        "TRANSFER_OUT, false, true"
    })
    void existingDirectionsRemainUnchanged(String txType, boolean inbound, boolean skipped)
        throws ReflectiveOperationException {
        assertEquals(inbound, classify("isIn", txType), txType + "的原有入出库方向不能改变");
        assertEquals(skipped, classify("isSkip", txType), txType + "的原有跳过规则不能改变");
    }

    private boolean classify(String methodName, String txType) throws ReflectiveOperationException {
        Method method = CostPoster.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(poster, txType);
    }
}
