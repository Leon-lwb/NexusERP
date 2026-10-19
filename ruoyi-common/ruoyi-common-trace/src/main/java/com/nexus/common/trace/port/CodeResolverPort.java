package com.nexus.common.trace.port;

import com.nexus.common.trace.*;
import java.util.Set;

/**
 * 各鉴权入口传入已授权对象类型，服务实现不得扩大候选访问范围。
 */
public interface CodeResolverPort {
    /**
     * 在调用方允许的对象类型内只读解析原码，歧义结果保留全部有权候选，不执行过账。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param request 本次业务的原始请求，具体校验要求见方法说明。
     * @param allowedTypes 权限与业务场景求交集后允许返回的对象类型。
     * @return 明确的解析状态及当前调用方有权查看的候选。
     */
    CodeResolution resolve(
            String tenantId, CodeResolveRequest request, Set<CodeObjectType> allowedTypes);
}
