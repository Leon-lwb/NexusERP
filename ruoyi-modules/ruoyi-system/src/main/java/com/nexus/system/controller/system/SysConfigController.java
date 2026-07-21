package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysConfigBo;
import com.nexus.system.domain.vo.SysConfigVo;
import com.nexus.system.service.ISysConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 参数配置（系统参数）Controller
 * <p>提供系统参数的分页查询、Excel 导出、键值查询、增删改、按 key 更新及缓存刷新接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/config")
public class SysConfigController extends BaseController {

    /** 参数配置业务服务，提供分页、唯一性校验、增删改及缓存重置等能力。 */
    private final ISysConfigService configService;

    /**
     * 分页查询参数配置列表。
     *
     * @param config    参数查询条件（参数名称、键名、类型等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 参数配置分页结果
     */
    @SaCheckPermission("system:config:list")
    @GetMapping("/list")
    public TableDataInfo<SysConfigVo> list(SysConfigBo config, PageQuery pageQuery) {
        return configService.selectPageConfigList(config, pageQuery);
    }

    /**
     * 导出参数配置列表为 Excel 文件。
     *
     * @param config   参数查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:config:export")
    @PostMapping("/export")
    public void export(SysConfigBo config, HttpServletResponse response) {
        List<SysConfigVo> list = configService.selectConfigList(config);
        ExcelUtil.exportExcel(list, "参数数据", SysConfigVo.class, response);
    }

    /**
     * 根据参数编号获取参数配置详细信息。
     *
     * @param configId 参数配置主键
     * @return 参数配置详情
     */
    @SaCheckPermission("system:config:query")
    @GetMapping(value = "/{configId}")
    public R<SysConfigVo> getInfo(@PathVariable Long configId) {
        return R.ok(configService.selectConfigById(configId));
    }

    /**
     * 根据参数键名查询参数值。
     *
     * @param configKey 参数键名
     * @return 对应参数的值
     */
    @GetMapping(value = "/configKey/{configKey}")
    public R<String> getConfigKey(@PathVariable String configKey) {
        return R.ok("操作成功", configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置，校验参数键名唯一性。
     *
     * @param config 参数配置新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:config:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            return R.fail("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.insertConfig(config);
        return R.ok();
    }

    /**
     * 修改参数配置，校验参数键名唯一性。
     *
     * @param config 参数配置修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            return R.fail("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.updateConfig(config);
        return R.ok();
    }

    /**
     * 根据参数键名修改参数配置（不校验键名唯一性）。
     *
     * @param config 含参数键名与值的修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/updateByKey")
    public R<Void> updateByKey(@RequestBody SysConfigBo config) {
        configService.updateConfig(config);
        return R.ok();
    }

    /**
     * 批量删除参数配置。
     *
     * @param configIds 参数配置主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public R<Void> remove(@PathVariable Long[] configIds) {
        configService.deleteConfigByIds(Arrays.asList(configIds));
        return R.ok();
    }

    /**
     * 刷新参数缓存，清除并重新加载全部系统参数。
     *
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public R<Void> refreshCache() {
        configService.resetConfigCache();
        return R.ok();
    }
}
