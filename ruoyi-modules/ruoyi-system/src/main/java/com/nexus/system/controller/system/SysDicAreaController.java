package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.bo.SysDicAreaBo;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.service.ISysDicAreaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行政区划（地区字典）Controller
 * <p>提供行政区划树形列表、子级查询、省份查询、下一级查询、详情、Excel 导出及增删改等接口。
 * 属于系统管理（System）模块。</p>
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/area")
public class SysDicAreaController extends BaseController {

    /** 行政区划业务服务，提供树形查询、子级查询、省份查询、详情与增删改能力。 */
    private final ISysDicAreaService sysDicAreaService;

    /**
     * 查询行政区划树形列表。
     *
     * @param sysDicArea 行政区划查询条件（名称、层级等）
     * @return 行政区划树列表
     */
    @SaCheckPermission("system:area:list")
    @GetMapping("/list")
    public R<List<SysDicAreaVo>> list(SysDicArea sysDicArea) {
        return R.ok(sysDicAreaService.selectSysDicAreaTreeList(sysDicArea));
    }

    /**
     * 查询行政区划（含子节点）树形列表。
     *
     * @param sysDicArea 行政区划查询条件
     * @return 含子节点的行政区划树列表
     */
    @SaCheckPermission("system:area:list")
    @GetMapping("/listChildren")
    public R<List<SysDicAreaVo>> listChildren(SysDicArea sysDicArea) {
        return R.ok(sysDicAreaService.selectChildrenTreeList(sysDicArea));
    }

    /**
     * 查询省级行政区划列表。
     *
     * @return 省级行政区划列表
     */
    @SaCheckPermission("system:area:list")
    @GetMapping("/provinces")
    public R<List<SysDicAreaVo>> getProvinces() {
        return R.ok(sysDicAreaService.selectProvinces());
    }

    /**
     * 查询指定父级行政区划的下一级子节点。
     *
     * @param parentAreaId 父级行政区划 ID
     * @return 下一级行政区划列表
     */
    @SaCheckPermission("system:area:list")
    @GetMapping("/nextLevel/{parentAreaId}")
    public R<List<SysDicAreaVo>> getNextLevel(@PathVariable Long parentAreaId) {
        if (parentAreaId == null) {
            return R.fail("参数错误：parentAreaId不能为空");
        }
        return R.ok(sysDicAreaService.selectNextLevel(parentAreaId));
    }

    /**
     * 查询行政区划树形列表并排除指定节点及其子树（用于选择上级时回显）。
     *
     * @param areaId 待排除节点的行政区划代码
     * @return 排除后的行政区划树列表
     */
    @SaCheckPermission("system:area:list")
    @GetMapping("/list/exclude/{areaId}")
    public R<List<SysDicAreaVo>> listAreaExcludeChild(@PathVariable Long areaId) {
        return R.ok(sysDicAreaService.selectTreeExcludeSubtree(areaId));
    }

    /**
     * 导出行政区划列表为 Excel 文件。
     *
     * @param response   HTTP 响应，用于写入导出的 Excel 流
     * @param sysDicArea 行政区划查询条件
     */
    @SaCheckPermission("system:area:export")
    @Log(title = "行政区划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDicArea sysDicArea) {
        List<SysDicAreaVo> list = sysDicAreaService.selectSysDicAreaList(sysDicArea);
        ExcelUtil.exportExcel(list, "行政区划数据", SysDicAreaVo.class, response);
    }

    /**
     * 根据主键查询行政区划详情。
     *
     * @param pkid 行政区划主键
     * @return 行政区划详情
     */
    @SaCheckPermission("system:area:query")
    @GetMapping(value = "/{pkid}")
    public R<SysDicAreaVo> getInfo(@PathVariable("pkid") Long pkid) {
        return R.ok(sysDicAreaService.selectSysDicAreaByPkid(pkid));
    }

    /**
     * 新增行政区划。
     *
     * @param bo 行政区划新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:area:add")
    @Log(title = "行政区划", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDicAreaBo bo) {
        return toAjax(sysDicAreaService.insertSysDicArea(bo));
    }

    /**
     * 修改行政区划。
     *
     * @param bo 行政区划修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:area:edit")
    @Log(title = "行政区划", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysDicAreaBo bo) {
        return toAjax(sysDicAreaService.updateSysDicArea(bo));
    }

    /**
     * 批量删除行政区划。
     *
     * @param pkids 行政区划主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:area:remove")
    @Log(title = "行政区划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pkids}")
    public R<Void> remove(@PathVariable Long[] pkids) {
        return toAjax(sysDicAreaService.deleteSysDicAreaByPkids(pkids));
    }
}
