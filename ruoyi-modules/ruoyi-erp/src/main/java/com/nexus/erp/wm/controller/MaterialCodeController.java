package com.nexus.erp.wm.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.CodeResolverPort;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.nexus.erp.wm.trace.TraceCodeAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * PC、App与PDA统一只读标识入口，写入仍由原业务单据重新校验。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/erp/wm/code", "/app/pda/code"})
public class MaterialCodeController {
    private final CodeResolverPort resolver;
    private final ErpTraceContext context;

    @PostMapping("/resolve")
    public R<CodeResolution> resolve(@RequestBody CodeResolveRequest request) {
        var allowed = TraceCodeAccess.allowed();
        return R.ok(resolver.resolve(context.tenant(), request, allowed));
    }
}
