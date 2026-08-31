package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
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
import com.nexus.system.domain.bo.SysDictTypeBo;
import com.nexus.system.domain.vo.SysDictTypeVo;
import com.nexus.system.service.ISysDictTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 字典类型Controller
 * <p>提供字典类型（字典分类）的分页查询、Excel 导出、详情、增删改、缓存刷新及选择框列表接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/dict/type")
public class SysDictTypeController extends BaseController {

    /** 字典类型业务服务，提供分页、唯一性校验、增删改及缓存重置等能力。 */
    private final ISysDictTypeService dictTypeService;

    /**
     * 分页查询字典类型列表。
     *
     * @param dictType 字典类型查询条件（字典名称、字典类型等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 字典类型分页结果
     */
    @SaCheckPermission("system:dict:list")
    @GetMapping("/list")
    public TableDataInfo<SysDictTypeVo> list(SysDictTypeBo dictType, PageQuery pageQuery) {
        return dictTypeService.selectPageDictTypeList(dictType, pageQuery);
    }

    /**
     * 导出字典类型列表为 Excel 文件。
     *
     * @param dictType 字典类型查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "字典类型", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:dict:export")
    @PostMapping("/export")
    public void export(SysDictTypeBo dictType, HttpServletResponse response) {
        List<SysDictTypeVo> list = dictTypeService.selectDictTypeList(dictType);
        ExcelUtil.exportExcel(list, "字典类型", SysDictTypeVo.class, response);
    }

    /**
     * 根据字典类型编号查询字典类型详细信息。
     *
     * @param dictId 字典类型编号
     * @return 字典类型详情
     */
    @SaCheckPermission("system:dict:query")
    @GetMapping(value = "/{dictId}")
    public R<SysDictTypeVo> getInfo(@PathVariable Long dictId) {
        return R.ok(dictTypeService.selectDictTypeById(dictId));
    }

    /**
     * 新增字典类型，校验字典类型唯一性。
     *
     * @param dict 字典类型新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dict:add")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            return R.fail("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.insertDictType(dict);
        return R.ok();
    }

    /**
     * 修改字典类型，校验字典类型唯一性。
     *
     * @param dict 字典类型修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dict:edit")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            return R.fail("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.updateDictType(dict);
        return R.ok();
    }

    /**
     * 批量删除字典类型。
     *
     * @param dictIds 字典类型编号数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dict:remove")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dictIds}")
    public R<Void> remove(@PathVariable Long[] dictIds) {
        dictTypeService.deleteDictTypeByIds(Arrays.asList(dictIds));
        return R.ok();
    }

    /**
     * 刷新字典缓存，清除并重新加载全部字典数据。
     *
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dict:remove")
    @Log(title = "字典类型", businessType = BusinessType.CLEAN)
    @Lock4j
    @DeleteMapping("/refreshCache")
    public R<Void> refreshCache() {
        dictTypeService.resetDictCache();
        return R.ok();
    }

    /**
     * 获取全部字典类型下拉选择框列表。
     *
     * @return 全部字典类型列表
     */
    @GetMapping("/optionselect")
    public R<List<SysDictTypeVo>> optionselect() {
        List<SysDictTypeVo> dictTypes = dictTypeService.selectDictTypeAll();
        return R.ok(dictTypes);
    }
}
