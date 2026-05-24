package org.dromara.erp.sch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.sch.domain.bo.SchPunchRuleBo;
import org.dromara.erp.sch.domain.vo.SchPunchRuleVo;
import org.dromara.erp.sch.service.ISchPunchRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打卡规则 Controller
 * <p>
 * 管理考勤打卡规则配置，包括签到/签退时间窗口、迟到/早退判定标准等。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/punchRule")
public class SchPunchRuleController extends BaseController {

    private final ISchPunchRuleService schPunchRuleService;

    /**
     * 分页查询打卡规则列表
     * <p>
     * 权限标识：{@code sch:punchRule:list}</p>
     *
     * @param bo        查询参数，包含规则名称、适用部门等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 打卡规则分页列表
     */
    @SaCheckPermission("sch:punchRule:list")
    @GetMapping("/list")
    public TableDataInfo<SchPunchRuleVo> list(SchPunchRuleBo bo, PageQuery pageQuery) {
        return schPunchRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出打卡规则数据到 Excel
     * <p>权限标识：{@code sch:punchRule:export}</p>
     * <p>日志记录：title="打卡规则"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:punchRule:export")
    @Log(title = "打卡规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchPunchRuleBo bo, HttpServletResponse response) {
        List<SchPunchRuleVo> list = schPunchRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "打卡规则数据", SchPunchRuleVo.class, response);
    }

    /**
     * 根据主键获取打卡规则详细信息
     * <p>
     * 权限标识：{@code sch:punchRule:query}</p>
     *
     * @param ruleId 打卡规则 ID
     * @return 打卡规则详细信息
     */
    @SaCheckPermission("sch:punchRule:query")
    @GetMapping("/{ruleId}")
    public R<SchPunchRuleVo> getInfo(@PathVariable Long ruleId) {
        return R.ok(schPunchRuleService.selectById(ruleId));
    }

    /**
     * 新增打卡规则
     * <p>
     * 权限标识：{@code sch:punchRule:add}</p>
     * <p>日志记录：title="打卡规则"，businessType=INSERT</p>
     *
     * @param bo 打卡规则业务对象，包含规则名称、时间窗口等配置信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:punchRule:add")
    @Log(title = "打卡规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchPunchRuleBo bo) {
        return toAjax(schPunchRuleService.insertByBo(bo));
    }

    /**
     * 修改打卡规则
     * <p>
     * 权限标识：{@code sch:punchRule:edit}</p>
     * <p>日志记录：title="打卡规则"，businessType=UPDATE</p>
     *
     * @param bo 打卡规则业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:punchRule:edit")
    @Log(title = "打卡规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchPunchRuleBo bo) {
        return toAjax(schPunchRuleService.updateByBo(bo));
    }

    /**
     * 批量删除打卡规则
     * <p>
     * 权限标识：{@code sch:punchRule:remove}</p>
     * <p>日志记录：title="打卡规则"，businessType=DELETE</p>
     *
     * @param ruleIds 要删除的打卡规则 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:punchRule:remove")
    @Log(title = "打卡规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public R<Void> remove(@PathVariable Long[] ruleIds) {
        return toAjax(schPunchRuleService.deleteByIds(ruleIds));
    }
}
