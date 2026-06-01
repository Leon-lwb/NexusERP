package com.nexus.erp.so.controller;

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
import com.nexus.erp.so.domain.bo.SoPriceListBo;
import com.nexus.erp.so.domain.vo.SoPriceListVo;
import com.nexus.erp.so.service.ISoPriceListService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售价格表 Controller
 * <p>
 * 提供销售价格表的RESTful API接口，包括：
 * <ul>
 *   <li>CRUD操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/price-list")
public class SoPriceListController extends BaseController {

    private final ISoPriceListService soPriceListService;

    /**
     * 分页查询销售价格表列表
     * 权限标识：{@code so:priceList:list}
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售价格表分页列表
     */
    @SaCheckPermission("so:priceList:list")
    @GetMapping("/list")
    public TableDataInfo<SoPriceListVo> list(SoPriceListBo bo, PageQuery pageQuery) {
        return soPriceListService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售价格表数据到Excel
     * 权限标识：{@code so:priceList:export}
     *
     * @param bo       查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("so:priceList:export")
    @Log(title = "销售价格表", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoPriceListBo bo, HttpServletResponse response) {
        List<SoPriceListVo> list = soPriceListService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售价格表数据", SoPriceListVo.class, response);
    }

    /**
     * 根据主键获取销售价格表详细信息
     * 权限标识：{@code so:priceList:query}
     *
     * @param id 销售价格表ID
     * @return 销售价格表详细信息
     */
    @SaCheckPermission("so:priceList:query")
    @GetMapping("/{id}")
    public R<SoPriceListVo> getInfo(@PathVariable Long id) {
        return R.ok(soPriceListService.selectById(id));
    }

    /**
     * 新增销售价格表
     * 权限标识：{@code so:priceList:add}
     *
     * @param bo 销售价格表业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:priceList:add")
    @Log(title = "销售价格表", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoPriceListBo bo) {
        return toAjax(soPriceListService.insertByBo(bo));
    }

    /**
     * 修改销售价格表
     * 权限标识：{@code so:priceList:edit}
     *
     * @param bo 销售价格表业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:priceList:edit")
    @Log(title = "销售价格表", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoPriceListBo bo) {
        return toAjax(soPriceListService.updateByBo(bo));
    }

    /**
     * 批量删除销售价格表
     * 权限标识：{@code so:priceList:remove}
     *
     * @param ids 销售价格表ID数组
     * @return 操作结果
     */
    @SaCheckPermission("so:priceList:remove")
    @Log(title = "销售价格表", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soPriceListService.deleteByIds(ids));
    }
}
