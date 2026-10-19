package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
import java.util.Set;
/** 各鉴权入口传入已授权对象类型，服务实现不得扩大候选访问范围。 */
public interface CodeResolverPort { CodeResolution resolve(String tenantId,CodeResolveRequest request,Set<CodeObjectType> allowedTypes); }
