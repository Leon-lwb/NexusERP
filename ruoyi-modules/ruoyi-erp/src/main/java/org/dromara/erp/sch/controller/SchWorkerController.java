package org.dromara.erp.sch.controller;

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
import org.dromara.erp.sch.domain.bo.SchWorkerBo;
import org.dromara.erp.sch.domain.vo.SchWorkerVo;
import org.dromara.erp.sch.service.ISchWorkerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工人档案Controller
 *
 * @author nexus
 * @date 2025-12-19
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/worker")
public class SchWorkerController extends BaseController {

    private final ISchWorkerService schWorkerService;

    @SaCheckPermission("sch:worker:list")
    @GetMapping("/list")
    public TableDataInfo<SchWorkerVo> list(SchWorkerBo bo, PageQuery pageQuery) {
        return schWorkerService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("sch:worker:export")
    @Log(title = "生产工人档案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchWorkerBo bo, HttpServletResponse response) {
        List<SchWorkerVo> list = schWorkerService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工人档案数据", SchWorkerVo.class, response);
    }

    @SaCheckPermission("sch:worker:query")
    @GetMapping("/{workerId}")
    public R<SchWorkerVo> getInfo(@PathVariable Long workerId) {
        return R.ok(schWorkerService.selectById(workerId));
    }

    @SaCheckPermission("sch:worker:add")
    @Log(title = "生产工人档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchWorkerBo bo) {
        return toAjax(schWorkerService.insertByBo(bo));
    }

    @SaCheckPermission("sch:worker:edit")
    @Log(title = "生产工人档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchWorkerBo bo) {
        return toAjax(schWorkerService.updateByBo(bo));
    }

    @SaCheckPermission("sch:worker:remove")
    @Log(title = "生产工人档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workerIds}")
    public R<Void> remove(@PathVariable Long[] workerIds) {
        return toAjax(schWorkerService.deleteByIds(workerIds));
    }
}
