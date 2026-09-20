package com.nexus.iot.forward.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.forward.domain.IotForwardLog;
import com.nexus.iot.forward.domain.IotForwardRule;
import com.nexus.iot.forward.domain.bo.IotForwardRuleBo;
import com.nexus.iot.forward.service.IIotForwardRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT数据转发规则 Controller（§4#6）。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/forwardRule")
public class IotForwardRuleController extends BaseController {

    private final IIotForwardRuleService forwardRuleService;

    /**
     * 查询转发规则列表
     */
    @SaCheckPermission("iot:forwardRule:list")
    @GetMapping("/list")
    public R<List<IotForwardRule>> list(@RequestParam(required = false) String ruleName,
                                        @RequestParam(required = false) String enabled) {
        return R.ok(forwardRuleService.selectList(ruleName, enabled));
    }

    /**
     * 查询规则详情
     */
    @SaCheckPermission("iot:forwardRule:query")
    @GetMapping("/{ruleId}")
    public R<IotForwardRule> getInfo(@PathVariable Long ruleId) {
        return R.ok(forwardRuleService.selectById(ruleId));
    }

    /**
     * 新增转发规则（URL 保存时即做 SSRF 校验）
     */
    @SaCheckPermission("iot:forwardRule:add")
    @Log(title = "IoT数据转发规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotForwardRuleBo bo) {
        return toAjax(forwardRuleService.insertByBo(bo));
    }

    /**
     * 修改转发规则
     */
    @SaCheckPermission("iot:forwardRule:edit")
    @Log(title = "IoT数据转发规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotForwardRuleBo bo) {
        return toAjax(forwardRuleService.updateByBo(bo));
    }

    /**
     * 批量删除转发规则（投递留痕保留）
     */
    @SaCheckPermission("iot:forwardRule:remove")
    @Log(title = "IoT数据转发规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(forwardRuleService.deleteByIds(ids));
    }

    /**
     * 分页查询投递留痕（含失败原因与重试次数）
     */
    @SaCheckPermission("iot:forwardRule:log")
    @GetMapping("/log/list")
    public TableDataInfo<IotForwardLog> logList(@RequestParam(required = false) Long ruleId,
                                                @RequestParam(required = false) Long deviceId,
                                                @RequestParam(required = false) String status,
                                                PageQuery pageQuery) {
        return forwardRuleService.selectLogPage(ruleId, deviceId, status, pageQuery);
    }
}
