package com.nexus.mes.trace;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.CompletionManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/mes/pro/completion-manifest") @RequiredArgsConstructor
public class MesCompletionManifestController {
    private final MesCompletionCancellationService cancellations;
    private final MesCompletionManifestService manifests;
    public record Cancel(String commandId,int revision,String reason) { }
    public record Refreeze(String commandId,int cancelledRevision,String reason) { }
    @PostMapping("/{manifestId}/request-cancel") @SaCheckPermission("mes:pro:work:cancel")
    public R<String> requestCancel(@PathVariable Long manifestId,@RequestBody Cancel body) {
        return R.ok(cancellations.request(body.commandId(),manifestId,body.revision(),body.reason(),LoginHelper.getUserId()));
    }
    @PostMapping("/work-order/{woId}/refreeze") @SaCheckPermission("mes:pro:work:edit")
    public R<CompletionManifest> refreeze(@PathVariable Long woId,@RequestBody Refreeze body) {
        return R.ok(manifests.refreeze(body.commandId(),woId,body.cancelledRevision(),body.reason(),LoginHelper.getUserId()));
    }
}
