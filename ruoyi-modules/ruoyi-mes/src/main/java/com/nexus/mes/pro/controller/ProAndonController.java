package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProAndonBo;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.service.IProAndonService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MES安灯 Controller
 * <p>属于 MES 生产管理模块，提供 安灯告警 相关 REST 接口（分页查询、增删改及业务动作）。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/andon")
public class ProAndonController extends BaseController {

    private final IProAndonService proAndonService;

    /**
     * 分页查询安灯列表
     */
    @SaCheckPermission("mes:pro:andon:list")
    @GetMapping("/list")
    public TableDataInfo<ProAndonVo> list(ProAndonBo bo, PageQuery pageQuery) {
        return proAndonService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取安灯详情
     */
    @SaCheckPermission("mes:pro:andon:query")
    @GetMapping("/{andonId}")
    public R<ProAndonVo> getInfo(@PathVariable Long andonId) {
        return R.ok(proAndonService.selectById(andonId));
    }

    /**
     * 看板聚合统计(按状态/类型分组)
     */
    @SaCheckPermission("mes:pro:andon:list")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(proAndonService.dashboardStats());
    }

    /**
     * 发起安灯呼叫
     */
    @SaCheckPermission("mes:pro:andon:call")
    @Log(title = "安灯呼叫", businessType = BusinessType.INSERT)
    @PostMapping("/call")
    public R<ProAndonVo> call(@RequestParam(required = false) Long mesWoId,
                              @RequestParam(required = false) Long cardId,
                              @RequestParam(required = false) Long stepRecordId,
                              @RequestParam String callType,
                              @RequestParam(required = false) String callReason) {
        return R.ok(proAndonService.callAndon(mesWoId, cardId, stepRecordId, callType, callReason));
    }

    /**
     * 响应安灯(CALLED -> RESPONDED)
     */
    @SaCheckPermission("mes:pro:andon:respond")
    @Log(title = "安灯响应", businessType = BusinessType.UPDATE)
    @PutMapping("/respond/{andonId}")
    public R<Void> respond(@PathVariable Long andonId) {
        proAndonService.respondAndon(andonId);
        return R.ok();
    }

    /**
     * 解决安灯(RESPONDED -> RESOLVED)
     */
    @SaCheckPermission("mes:pro:andon:resolve")
    @Log(title = "安灯解决", businessType = BusinessType.UPDATE)
    @PutMapping("/resolve/{andonId}")
    public R<Void> resolve(@PathVariable Long andonId,
                           @RequestParam(required = false) String remark) {
        proAndonService.resolveAndon(andonId, remark);
        return R.ok();
    }

    /**
     * 取消安灯(CALLED -> CANCELLED)
     */
    @SaCheckPermission("mes:pro:andon:cancel")
    @Log(title = "安灯取消", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{andonId}")
    public R<Void> cancel(@PathVariable Long andonId) {
        proAndonService.cancelAndon(andonId);
        return R.ok();
    }
}
