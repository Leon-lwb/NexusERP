package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

/** 仅接入固定业务目录；不改变原controller签名、权限注解和业务URL。 */
@Aspect @Component @Order(Ordered.LOWEST_PRECEDENCE-20) @RequiredArgsConstructor
public class WmSerialBusinessAspect {
    private final WmSerialBusinessAdapter adapter;
    private final ObjectMapper json;
    @Around("@within(org.springframework.web.bind.annotation.RestController) && (execution(public * com.nexus.erp.wm.controller..*(..)) || execution(public * com.nexus.erp.so.controller..*(..)) || execution(public * com.nexus.erp.pu.controller..*(..)) || execution(public * com.nexus.erp.prd.controller..*(..)))")
    public Object controller(ProceedingJoinPoint join) throws Throwable {
        var attributes=RequestContextHolder.getRequestAttributes();if(!(attributes instanceof ServletRequestAttributes servlet))return join.proceed();
        HttpServletRequest request=servlet.getRequest();String uri=request.getRequestURI().substring(request.getContextPath().length());
        var route=SerialBusinessRoute.ofPath(uri);
        if(route==null||!("POST".equals(request.getMethod())||"PUT".equals(request.getMethod()))||uri.endsWith("/export"))return join.proceed();
        JsonNode body=(JsonNode)request.getAttribute(WmSerialPayloadFilter.BODY);
        if(body==null) {
            if(request.getContentLengthLong()>0)throw new ServiceException("SN业务请求缺少原请求体适配，请检查过滤器配置");
            body=json.createObjectNode();
        }
        if(!body.isObject())throw new ServiceException("原业务请求体必须为对象");
        return adapter.invoke(route,uri,body,join.getSignature().getName(),join::proceed);
    }
    @Around("execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper+.insert(..)) || execution(* com.nexus.common.mybatis.core.mapper.BaseMapperPlus+.insertBatch(..))")
    public Object inserted(ProceedingJoinPoint join) throws Throwable {
        Object result=join.proceed();if(join.getArgs().length>0)adapter.inserted(join.getArgs()[0]);return result;
    }
    @Around("execution(public * com.nexus.erp.wm.service.WmSerialInventoryService.post(..)) || execution(public * com.nexus.erp.wm.service.WmSerialInventoryService.postSource(..)) || execution(public * com.nexus.erp.wm.service.WmSerialInventoryService.postReceiveDocument(..)) || execution(public * com.nexus.erp.wm.service.WmSerialInventoryService.postCertifiedReceive(..))")
    public Object posting(ProceedingJoinPoint join) throws Throwable {
        Object[] args=join.getArgs();
        if(args[0] instanceof WmSerialPostingRequest r)adapter.beforePosting(r.sourceType(),r.sourceId(),r.lineKey());
        else if(args[0] instanceof String type)adapter.beforePosting(type,(Long)args[1],args[2]==null?0L:(Long)args[2]);
        else adapter.beforeReceive((Long)args[0]);
        return join.proceed();
    }
}
