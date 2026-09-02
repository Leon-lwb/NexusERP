package com.nexus.ai.service.facade;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据查询关键词路由规则单元测试
 * <p>验证收紧后的 Agent 模式路由：强意图词单独命中即路由，
 * 泛化业务词需与意图词组合命中，普通知识问答不再误入 Agent 模式。</p>
 *
 * @author nexus-erp
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiServiceFacadeDataQueryRouteTest {

    @Test
    void 强意图词单独命中应路由Agent() {
        assertTrue(AiServiceFacade.containsDataQueryKeywords("统计本月采购订单金额"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("查询库存周转情况"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("帮我生成销售报表"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("这张BOM包含哪些物料"));
    }

    @Test
    void 排产缺料专属词单独命中应路由Agent() {
        // 智能排产/缺料风险 Agent 场景，即使无"查询/统计"等意图词也应路由
        assertTrue(AiServiceFacade.containsDataQueryKeywords("分析最新排产方案 APS202609010001"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("下周哪些订单会拖期"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("瓶颈资源是哪个"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("有没有缺料风险"));
    }

    @Test
    void 业务词加意图词组合命中应路由Agent() {
        assertTrue(AiServiceFacade.containsDataQueryKeywords("本月订单金额多少"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("各仓库库存数量对比"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("分析一下供应商交货趋势"));
        assertTrue(AiServiceFacade.containsDataQueryKeywords("客户退货数据看一下"));
    }

    @Test
    void 泛化业务词单独出现不应路由Agent() {
        // 之前误路由的典型场景：知识问答中含"订单""库存""客户"等词
        assertFalse(AiServiceFacade.containsDataQueryKeywords("什么是安全库存"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords("如何创建采购订单"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords("客户关系管理的理念是什么"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords("销售漏斗的原理"));
    }

    @Test
    void 意图词单独出现不应路由Agent() {
        // 泛化意图词（分析/总/数据等）单独出现不代表数据查询意图
        assertFalse(AiServiceFacade.containsDataQueryKeywords("分析一下SWOT模型的用法"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords("总结一下OKR的实施要点"));
    }

    @Test
    void 普通闲聊不应路由Agent() {
        assertFalse(AiServiceFacade.containsDataQueryKeywords("你好"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords("写一首关于春天的诗"));
        assertFalse(AiServiceFacade.containsDataQueryKeywords(""));
        assertFalse(AiServiceFacade.containsDataQueryKeywords(null));
    }
}
