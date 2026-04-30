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
import org.dromara.erp.bd.domain.bo.BdClientBo;
import org.dromara.erp.bd.domain.vo.BdClientVo;
import org.dromara.erp.bd.service.IBdClientService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/client")
public class BdClientController extends BaseController {

    private final IBdClientService bdClientService;

    @SaCheckPermission("bd:client:list")
    @GetMapping("/list")
    public TableDataInfo<BdClientVo> list(BdClientBo bo, PageQuery pageQuery) {
        return bdClientService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("bd:client:export")
    @Log(title = "客户信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdClientBo bo, HttpServletResponse response) {
        List<BdClientVo> list = bdClientService.selectList(bo);
        ExcelUtil.exportExcel(list, "客户数据", BdClientVo.class, response);
    }

    @SaCheckPermission("bd:client:query")
    @GetMapping("/{id}")
    public R<BdClientVo> getInfo(@PathVariable Long id) {
        return R.ok(bdClientService.selectById(id));
    }

    @SaCheckPermission("bd:client:add")
    @Log(title = "客户信息", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.insertByBo(bo));
    }

    @SaCheckPermission("bd:client:edit")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.updateByBo(bo));
    }

    @SaCheckPermission("bd:client:remove")
    @Log(title = "客户信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdClientService.deleteByIds(ids));
    }
}
