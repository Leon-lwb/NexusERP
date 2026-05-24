package org.dromara.erp.bd.controller;

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
import org.dromara.erp.bd.domain.bo.BdSerialNumberRuleBo;
import org.dromara.erp.bd.domain.vo.BdSerialNumberRuleVo;
import org.dromara.erp.bd.service.IBdSerialNumberRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品序列号/条码规则 Controller
 * <p>
 * 管理产品序列号/条码/批次号的编码规则，含校验位（MOD10/MOD11）。
 * 单据编码规则请使用 {@code /system/autocode} 路径下的 SysAutoCodeController。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/serial-number-rule")
public class BdSerialNumberRuleController extends BaseController {

    private final IBdSerialNumberRuleService serialNumberRuleService;

    /**
     * 分页查询产品序列号/条码规则列表
     * <p>
     * 根据查询条件分页获取序列号/条码规则数据，支持多条件筛选。</p>
     * <p>权限标识：{@code bd:serialNumberRule:list}</p>
     *
     * @param bo        查询参数，包含规则名称、规则编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 序列号规则分页列表
     */
    @SaCheckPermission("bd:serialNumberRule:list")
    @GetMapping("/list")
    public TableDataInfo<BdSerialNumberRuleVo> list(BdSerialNumberRuleBo bo, PageQuery pageQuery) {
        return serialNumberRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出序列号规则数据到 Excel
     * <p>
     * 根据查询条件导出序列号规则列表为 Excel 文件。</p>
     * <p>权限标识：{@code bd:serialNumberRule:export}</p>
     * <p>日志记录：title="序列号规则"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:serialNumberRule:export")
    @Log(title = "序列号规则", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdSerialNumberRuleBo bo, HttpServletResponse response) {
        List<BdSerialNumberRuleVo> list = serialNumberRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "序列号规则", BdSerialNumberRuleVo.class, response);
    }

    /**
     * 根据主键获取序列号规则详细信息
     * <p>
     * 权限标识：{@code bd:serialNumberRule:query}</p>
     *
     * @param id 序列号规则 ID
     * @return 序列号规则详细信息
     */
    @SaCheckPermission("bd:serialNumberRule:query")
    @GetMapping("/{id}")
    public R<BdSerialNumberRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(serialNumberRuleService.selectById(id));
    }

    /**
     * 新增序列号规则
     * <p>
     * 权限标识：{@code bd:serialNumberRule:add}</p>
     * <p>日志记录：title="序列号规则"，businessType=INSERT</p>
     *
     * @param bo 序列号规则业务对象，包含规则编码、规则名称、编码格式等配置信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:serialNumberRule:add")
    @Log(title = "序列号规则", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdSerialNumberRuleBo bo) {
        return toAjax(serialNumberRuleService.insertByBo(bo));
    }

    /**
     * 修改序列号规则
     * <p>
     * 权限标识：{@code bd:serialNumberRule:edit}</p>
     * <p>日志记录：title="序列号规则"，businessType=UPDATE</p>
     *
     * @param bo 序列号规则业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:serialNumberRule:edit")
    @Log(title = "序列号规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdSerialNumberRuleBo bo) {
        return toAjax(serialNumberRuleService.updateByBo(bo));
    }

    /**
     * 批量删除序列号规则
     * <p>
     * 权限标识：{@code bd:serialNumberRule:remove}</p>
     * <p>日志记录：title="序列号规则"，businessType=DELETE</p>
     *
     * @param ids 要删除的序列号规则 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:serialNumberRule:remove")
    @Log(title = "序列号规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(serialNumberRuleService.deleteByIds(ids));
    }

    /**
     * 根据规则编码生成序列号/条码
     * <p>
     * 根据指定的规则编码，调用编码规则引擎生成下一个序列号或条码值。</p>
     * <p>权限标识：{@code bd:serialNumberRule:generate}</p>
     *
     * @param ruleCode 规则编码，对应 {@code bd_serial_number_rule} 表中的 {@code rule_code} 字段
     * @return 生成的序列号/条码字符串
     */
    @SaCheckPermission("bd:serialNumberRule:generate")
    @PostMapping("/generate/{ruleCode}")
    public R<String> generate(@PathVariable String ruleCode) {
        return R.ok(serialNumberRuleService.generateSerialNumber(ruleCode));
    }
}
