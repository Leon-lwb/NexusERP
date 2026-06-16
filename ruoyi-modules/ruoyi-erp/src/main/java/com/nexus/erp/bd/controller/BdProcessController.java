package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdProcessBo;
import com.nexus.erp.bd.domain.vo.BdProcessVo;
import com.nexus.erp.bd.service.IBdProcessService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工序定义Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/process")
public class BdProcessController extends BaseController {

    private final IBdProcessService bdProcessService;

    /**
     * 分页查询工序列表
     */
    @SaCheckPermission("bd:process:list")
    @GetMapping("/list")
    public TableDataInfo<BdProcessVo> list(BdProcessBo bo, PageQuery pageQuery) {
        return bdProcessService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工序数据到 Excel
     */
    @SaCheckPermission("bd:process:export")
    @Log(title = "工序定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdProcessBo bo, HttpServletResponse response) {
        List<BdProcessVo> list = bdProcessService.selectList(bo);
        ExcelUtil.exportExcel(list, "工序数据", BdProcessVo.class, response);
    }

    /**
     * 根据主键获取工序详细信息
     */
    @SaCheckPermission("bd:process:query")
    @GetMapping("/{id}")
    public R<BdProcessVo> getInfo(@PathVariable Long id) {
        return R.ok(bdProcessService.selectById(id));
    }

    /**
     * 新增工序
     */
    @SaCheckPermission("bd:process:add")
    @Log(title = "工序定义", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdProcessBo bo) {
        return toAjax(bdProcessService.insertByBo(bo));
    }

    /**
     * 修改工序
     */
    @SaCheckPermission("bd:process:edit")
    @Log(title = "工序定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdProcessBo bo) {
        return toAjax(bdProcessService.updateByBo(bo));
    }

    /**
     * 批量删除工序
     */
    @SaCheckPermission("bd:process:remove")
    @Log(title = "工序定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdProcessService.deleteByIds(ids));
    }
}
