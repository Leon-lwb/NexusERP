package com.nexus.mes.trace;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.CompletionManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 完工清单查询、取消与修订入口，以业务权限、版本和原命令约束操作。
 */
@RestController
@RequestMapping("/mes/pro/completion-manifest")
@RequiredArgsConstructor
public class MesCompletionManifestController {
    private final MesCompletionCancellationService cancellations;
    private final MesCompletionManifestService manifests;

    /**
     * 引用原清单及修订的取消请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param revision 冻结清单修订号，必须与游标及事件一致。
     * @param reason 与原任务或原事实关联的明确操作原因。
     */
    public record Cancel(String commandId, int revision, String reason) {}

    /**
     * 已完成取消握手后的重新冻结请求。
     *
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param cancelledRevision 已经通过取消握手释放的旧清单修订。
     * @param reason 与原任务或原事实关联的明确操作原因。
     */
    public record Refreeze(String commandId, int cancelledRevision, String reason) {}

    @PostMapping("/{manifestId}/request-cancel")
    @SaCheckPermission("mes:pro:work:cancel")
    public R<String> requestCancel(@PathVariable Long manifestId, @RequestBody Cancel body) {
        return R.ok(
                cancellations.request(
                        body.commandId(),
                        manifestId,
                        body.revision(),
                        body.reason(),
                        LoginHelper.getUserId()));
    }

    @PostMapping("/work-order/{woId}/refreeze")
    @SaCheckPermission("mes:pro:work:edit")
    public R<CompletionManifest> refreeze(@PathVariable Long woId, @RequestBody Refreeze body) {
        return R.ok(
                manifests.refreeze(
                        body.commandId(),
                        woId,
                        body.cancelledRevision(),
                        body.reason(),
                        LoginHelper.getUserId()));
    }
}
