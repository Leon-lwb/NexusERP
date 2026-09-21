package com.nexus.iot.alert.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.alert.domain.bo.IotAlertRuleBo;
import com.nexus.iot.alert.domain.vo.IotAlertRuleVo;
import com.nexus.iot.alert.service.IIotAlertRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IoT告警规则 Controller（IOT-ALERT-02）。
 * <p>
 * 权限码：{@code iot:alertRule:list/query/add/edit/remove/changeStatus}，
 * 种子见 {@code script/sql/update/2026-09-20-iot-alert.sql} 第 5 节（menu_id 6040~6045）。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/alertRule")
public class IotAlertRuleController extends BaseController {

    private final IIotAlertRuleService alertRuleService;

    /**
     * 分页查询告警规则列表
     */
    @SaCheckPermission("iot:alertRule:list")
    @GetMapping("/list")
    public TableDataInfo<IotAlertRuleVo> list(IotAlertRuleBo bo, PageQuery pageQuery) {
        return alertRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询全部启用规则（供下拉/前端本地匹配展示）
     */
    @SaCheckPermission("iot:alertRule:list")
    @GetMapping("/optionList")
    public R<List<IotAlertRuleVo>> optionList(IotAlertRuleBo bo) {
        return R.ok(alertRuleService.selectList(bo));
    }

    /**
     * 根据主键获取规则详情
     */
    @SaCheckPermission("iot:alertRule:query")
    @GetMapping("/{ruleId}")
    public R<IotAlertRuleVo> getInfo(@PathVariable Long ruleId) {
        return R.ok(alertRuleService.selectById(ruleId));
    }

    /**
     * 新增告警规则
     */
    @SaCheckPermission("iot:alertRule:add")
    @Log(title = "IoT告警规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotAlertRuleBo bo) {
        return toAjax(alertRuleService.insertByBo(bo));
    }

    /**
     * 修改告警规则
     */
    @SaCheckPermission("iot:alertRule:edit")
    @Log(title = "IoT告警规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotAlertRuleBo bo) {
        return toAjax(alertRuleService.updateByBo(bo));
    }

    /**
     * 启用/停用告警规则（1启用 0停用）
     */
    @SaCheckPermission("iot:alertRule:changeStatus")
    @Log(title = "IoT告警规则", businessType = BusinessType.UPDATE)
    @PutMapping("/changeEnabled")
    public R<Void> changeEnabled(@RequestParam Long ruleId, @RequestParam String enabled) {
        return toAjax(alertRuleService.changeEnabled(ruleId, enabled));
    }

    /**
     * 批量删除告警规则
     */
    @SaCheckPermission("iot:alertRule:remove")
    @Log(title = "IoT告警规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(alertRuleService.deleteByIds(ids));
    }

}
