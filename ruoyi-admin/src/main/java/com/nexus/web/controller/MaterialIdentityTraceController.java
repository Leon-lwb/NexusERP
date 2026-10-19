package com.nexus.web.controller;

import com.nexus.common.core.domain.R;
import com.nexus.web.service.MaterialIdentityTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping({"/erp/wm/trace","/app/pda/trace"})
public class MaterialIdentityTraceController {
    private final MaterialIdentityTraceService trace;
    @GetMapping("/identity/{identityId}")
    public R<Map<String,Object>> identity(@PathVariable Long identityId,@RequestParam(required=false) String module,
        @RequestParam(required=false) String section,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="500") int limit){
        return R.ok(trace.identity(identityId,module,section,cursor,limit));
    }
    @GetMapping("/batch/{batchId}")
    public R<Map<String,Object>> batch(@PathVariable Long batchId,@RequestParam(required=false) String module,
        @RequestParam(required=false) String section,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="500") int limit){
        return R.ok(trace.batch(batchId,module,section,cursor,limit));
    }
    @GetMapping("/recall")
    public R<Map<String,Object>> recall(@RequestParam Long batchId,@RequestParam(defaultValue="0") long afterIdentity,
        @RequestParam(defaultValue="500") int limit,@RequestParam(defaultValue="10") int depth,@RequestParam(required=false) String scopeCursor,@RequestParam(required=false) String batchCursor){return R.ok(trace.recall(batchId,afterIdentity,limit,depth,scopeCursor,batchCursor));}
    @GetMapping("/where-used/{identityId}")
    public R<Map<String,Object>> whereUsed(@PathVariable Long identityId,@RequestParam(defaultValue="0") long afterIdentity,
        @RequestParam(defaultValue="500") int limit,@RequestParam(defaultValue="10") int depth,@RequestParam(required=false) String scopeCursor,@RequestParam(required=false) String batchCursor){return R.ok(trace.whereUsed(identityId,afterIdentity,limit,depth,scopeCursor,batchCursor));}
}
