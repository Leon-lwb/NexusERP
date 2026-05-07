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

/**
 * 客户信息Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/client")
public class BdClientController extends BaseController {

    private final IBdClientService bdClientService;

    /**
     * 查询客户列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 客户分页列表
     */
    @SaCheckPermission("bd:client:list")
    @GetMapping("/list")
    public TableDataInfo<BdClientVo> list(BdClientBo bo, PageQuery pageQuery) {
        return bdClientService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出客户数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:client:export")
    @Log(title = "客户信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdClientBo bo, HttpServletResponse response) {
        List<BdClientVo> list = bdClientService.selectList(bo);
        ExcelUtil.exportExcel(list, "客户数据", BdClientVo.class, response);
    }

    /**
     * 获取客户详细信息
     *
     * @param id 客户ID
     * @return 客户详细信息
     */
    @SaCheckPermission("bd:client:query")
    @GetMapping("/{id}")
    public R<BdClientVo> getInfo(@PathVariable Long id) {
        return R.ok(bdClientService.selectById(id));
    }

    /**
     * 新增客户
     *
     * @param bo 客户信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:client:add")
    @Log(title = "客户信息", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.insertByBo(bo));
    }

    /**
     * 修改客户
     *
     * @param bo 客户信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:client:edit")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.updateByBo(bo));
    }

    /**
     * 删除客户
     *
     * @param ids 客户ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:client:remove")
    @Log(title = "客户信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdClientService.deleteByIds(ids));
    }
}
