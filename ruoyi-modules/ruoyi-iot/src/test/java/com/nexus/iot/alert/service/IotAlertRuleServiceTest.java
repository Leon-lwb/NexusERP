package com.nexus.iot.alert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.mapper.IotAlertRuleMapper;
import com.nexus.iot.alert.service.impl.IotAlertRuleServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 告警规则匹配优先级单元测试（IOT-ALERT-02）。
 * <p>
 * 覆盖"设备规则 &gt; 产品规则 &gt; 全局规则"的优先级与范围过滤：
 * 范围不匹配的规则不得参与判定，否则租户 A 的产品规则会误伤其他产品/设备。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotAlertRuleServiceTest {

    private static final String TENANT = "000000";

    private IotAlertRuleMapper baseMapper;
    private IotAlertRuleServiceImpl service;
    private MockedStatic<TenantHelper> tenantMock;

    @BeforeEach
    void setUp() {
        /** LambdaQueryWrapper 需要实体的列缓存（否则 LambdaUtils.extract 抛 “找不到 Lambda 缓存”） */
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotAlertRule.class);
        baseMapper = mock(IotAlertRuleMapper.class);
        service = new IotAlertRuleServiceImpl(baseMapper);
        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    private IotAlertRule rule(Long ruleId, Long productId, Long deviceId) {
        IotAlertRule r = new IotAlertRule();
        r.setRuleId(ruleId);
        r.setTenantId(TENANT);
        r.setProductId(productId);
        r.setDeviceId(deviceId);
        r.setPropertyCode("temperature");
        r.setOperator("GT");
        r.setThreshold(new BigDecimal("80"));
        r.setDurationSec(0);
        r.setDebounceSec(60);
        r.setEnabled(IotAlertConstants.ENABLED_YES);
        return r;
    }

    /** 具体度降序：设备级 → 产品级 → 全局。 */
    @Test
    void selectEnabledRules_priorityDeviceThenProductThenGlobal() {
        when(baseMapper.selectList(any()))
            .thenReturn(List.of(rule(1L, 0L, 0L), rule(2L, 9L, 0L), rule(3L, 0L, 1L)));

        List<IotAlertRule> matched = service.selectEnabledRules(TENANT, 1L, 9L, "temperature");

        assertEquals(3, matched.size());
        assertEquals(3L, matched.get(0).getRuleId(), "设备级规则应最优先");
        assertEquals(2L, matched.get(1).getRuleId(), "其次为产品级规则");
        assertEquals(1L, matched.get(2).getRuleId(), "最后为全局规则");
    }

    /** 范围不匹配（其他产品 / 其他设备）的规则必须被过滤掉。 */
    @Test
    void selectEnabledRules_filtersUnrelatedScopes() {
        when(baseMapper.selectList(any()))
            .thenReturn(List.of(rule(1L, 999L, 0L), rule(2L, 0L, 888L)));

        assertTrue(service.selectEnabledRules(TENANT, 1L, 9L, "temperature").isEmpty());
    }

    /** 设备/产品没有专属规则时，全局规则仍是唯一适用的库内兜底。 */
    @Test
    void selectEnabledRules_noSpecificRule_globalStillMatches() {
        when(baseMapper.selectList(any()))
            .thenReturn(List.of(rule(7001L, 0L, 0L), rule(8701L, 9L, 0L), rule(8702L, 0L, 99L)));

        List<IotAlertRule> matched = service.selectEnabledRules(TENANT, 1L, 1L, "temperature");

        assertEquals(1, matched.size());
        assertEquals(7001L, matched.get(0).getRuleId());
    }

    /** 属性编码为空时不返回任何规则（避免全表误匹配）。 */
    @Test
    void selectEnabledRules_blankProperty_returnsEmpty() {
        assertTrue(service.selectEnabledRules(TENANT, 1L, 9L, "  ").isEmpty());
    }

}
