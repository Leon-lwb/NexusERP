package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * AI 工具 Mapper 接口
 *
 * @author NexusERP
 */
public interface AiToolMapper extends BaseMapperPlus<AiTool, Void> {
    /** 设备认证尚未建立租户上下文时，仅按显式租户核对工程工具；不依赖浏览器令牌。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM ai_tool WHERE tenant_id=#{tenantId} AND tool_name='easyeda_engineering' AND tool_type='BUILTIN' AND status='0' AND del_flag='0'")
    long engineeringEnabled(@org.apache.ibatis.annotations.Param("tenantId") String tenantId);
}
