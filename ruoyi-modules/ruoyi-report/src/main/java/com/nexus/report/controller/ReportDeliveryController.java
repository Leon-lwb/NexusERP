package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.subscription.ReportDeliveryService;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 接收人的私有交付中心，生成者权限不能替代领取人权限。 */
@RestController
@RequestMapping("/report/deliveries")
public final class ReportDeliveryController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前deliveries状态或配置。 */
    private final ReportDeliveryService deliveries;

    /**
     * 注入正常登录主体及重新鉴权的领取服务。
     *
     * @param actors 当前契约的actors配置或状态
     * @param deliveries 当前契约的deliveries配置或状态
     */
    public ReportDeliveryController(ReportRequestActor actors, ReportDeliveryService deliveries) {
        this.actors = actors;
        this.deliveries = deliveries;
    }

    /**
     * 只读取当前用户交付，不向普通用户开放全租户任务对账。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @GetMapping
    public R<List<ReportDeliveryService.Delivery>> list() {
        return R.ok(deliveries.list(actors.current()));
    }

    /**
     * 领取返回正常导出任务ID，下载依然经过通用实时权限代理。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:runtime:export")
    @PostMapping("/{id}/claim")
    public R<String> claim(@PathVariable String id) {
        return R.ok("操作成功", deliveries.claim(actors.current(), id));
    }
}
