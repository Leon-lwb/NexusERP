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
import org.dromara.erp.bd.domain.bo.BdWorkshopBo;
import org.dromara.erp.bd.domain.vo.BdWorkshopVo;
import org.dromara.erp.bd.service.IBdWorkshopService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车间信息Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workshop")
public class BdWorkshopController extends BaseController {

    private final IBdWorkshopService bdWorkshopService;

    /**
     * 查询车间列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 车间分页列表
     */
    @SaCheckPermission("bd:workshop:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkshopVo> list(BdWorkshopBo bo, PageQuery pageQuery) {
        return bdWorkshopService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出车间数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:workshop:export")
    @Log(title = "车间", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkshopBo bo, HttpServletResponse response) {
        List<BdWorkshopVo> list = bdWorkshopService.selectList(bo);
        ExcelUtil.exportExcel(list, "车间数据", BdWorkshopVo.class, response);
    }

    /**
     * 获取车间详细信息
     *
     * @param id 车间ID
     * @return 车间详细信息
     */
    @SaCheckPermission("bd:workshop:query")
    @GetMapping("/{id}")
    public R<BdWorkshopVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkshopService.selectById(id));
    }

    /**
     * 新增车间
     *
     * @param bo 车间信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:workshop:add")
    @Log(title = "车间", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.insertByBo(bo));
    }

    /**
     * 修改车间
     *
     * @param bo 车间信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:workshop:edit")
    @Log(title = "车间", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.updateByBo(bo));
    }

    /**
     * 删除车间
     *
     * @param ids 车间ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:workshop:remove")
    @Log(title = "车间", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkshopService.deleteByIds(ids));
    }
}
