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
import org.dromara.erp.bd.domain.bo.BdWorkstationBo;
import org.dromara.erp.bd.domain.vo.BdWorkstationVo;
import org.dromara.erp.bd.service.IBdWorkstationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作中心Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workstation")
public class BdWorkstationController extends BaseController {

    private final IBdWorkstationService bdWorkstationService;

    /**
     * 查询工作中心列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 工作中心分页列表
     */
    @SaCheckPermission("bd:workstation:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkstationVo> list(BdWorkstationBo bo, PageQuery pageQuery) {
        return bdWorkstationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工作中心数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:workstation:export")
    @Log(title = "工作站", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkstationBo bo, HttpServletResponse response) {
        List<BdWorkstationVo> list = bdWorkstationService.selectList(bo);
        ExcelUtil.exportExcel(list, "工作站数据", BdWorkstationVo.class, response);
    }

    /**
     * 获取工作中心详细信息
     *
     * @param id 工作中心ID
     * @return 工作中心详细信息
     */
    @SaCheckPermission("bd:workstation:query")
    @GetMapping("/{id}")
    public R<BdWorkstationVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkstationService.selectById(id));
    }

    /**
     * 新增工作中心
     *
     * @param bo 工作中心信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:workstation:add")
    @Log(title = "工作站", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.insertByBo(bo));
    }

    /**
     * 修改工作中心
     *
     * @param bo 工作中心信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:workstation:edit")
    @Log(title = "工作站", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.updateByBo(bo));
    }

    /**
     * 删除工作中心
     *
     * @param ids 工作中心ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:workstation:remove")
    @Log(title = "工作站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkstationService.deleteByIds(ids));
    }
}
