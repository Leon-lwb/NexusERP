package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.trace.ErpSerialIdentityService;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 正式实物登记与外部别名管理；生成身份不产生库存。 */
@RestController @RequestMapping("/erp/wm/sn") @RequiredArgsConstructor
public class SerialIdentityController {
    private final ErpSerialIdentityService identities;
    private final ErpTraceContext context;
    @SaCheckPermission("wm:sn:add") @PostMapping("/reserve")
    public R<SerialReservation> reserve(@RequestBody SerialReservationCommand command) {
        return R.ok(identities.reserveManual(context.tenant(),command));
    }
    /** 有审计地单向退休未使用手工身份；不删除、不回收号码或代替库存业务。 */
    @SaCheckPermission({"wm:sn:add","wm:sn:remove"})
    @Log(title="未使用手工SN退休",businessType=BusinessType.UPDATE)
    @PostMapping("/{identityId}/retire")
    public R<ErpSerialIdentityService.ManualRetirement> retire(@PathVariable Long identityId,@RequestBody RetireManualCommand command) {
        if(command==null)throw new ServiceException("退休请求不能为空");
        return R.ok(identities.retireUnusedManual(context.tenant(),identityId,command.commandId(),command.expectedVersion(),command.reason()));
    }
    /** 调用方持久化的退休命令与原身份版本；原因进入不可变审计指纹。 */
    public record RetireManualCommand(String commandId,Long expectedVersion,String reason) { }
    @SaCheckPermission("wm:sn:edit") @PostMapping("/{identityId}/aliases")
    public R<Void> alias(@PathVariable Long identityId,@RequestBody AliasCommand command) {
        identities.addAlias(context.tenant(),identityId,command.commandId(),command.sourceType(),command.sourceId(),command.code());
        return R.ok();
    }
    /** 别名保留原文与来源主体，不能直接改写正式SN。 */
    public record AliasCommand(String commandId,String sourceType,Long sourceId,String code) { }
}
