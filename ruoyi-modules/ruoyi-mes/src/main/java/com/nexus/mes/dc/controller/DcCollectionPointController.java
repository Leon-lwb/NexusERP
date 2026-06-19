package com.nexus.mes.dc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.dc.domain.bo.DcCollectionPointBo;
import com.nexus.mes.dc.domain.vo.DcCollectionPointVo;
import com.nexus.mes.dc.service.IDcCollectionPointService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES数据采集点 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/dc/collection-point")
public class DcCollectionPointController extends BaseController {

    private final IDcCollectionPointService dcCollectionPointService;

    @SaCheckPermission("mes:dc:point:list")
    @GetMapping("/list")
    public TableDataInfo<DcCollectionPointVo> list(DcCollectionPointBo bo, PageQuery pageQuery) {
        return dcCollectionPointService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:dc:point:query")
    @GetMapping("/{pointId}")
    public R<DcCollectionPointVo> getInfo(@PathVariable Long pointId) {
        return R.ok(dcCollectionPointService.selectById(pointId));
    }

    @SaCheckPermission("mes:dc:point:add")
    @Log(title = "采集点新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<DcCollectionPointVo> add(@RequestBody DcCollectionPointBo bo) {
        return R.ok(dcCollectionPointService.insertByBo(bo));
    }

    @SaCheckPermission("mes:dc:point:edit")
    @Log(title = "采集点编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<DcCollectionPointVo> edit(@RequestBody DcCollectionPointBo bo) {
        return R.ok(dcCollectionPointService.updateByBo(bo));
    }

    @SaCheckPermission("mes:dc:point:remove")
    @Log(title = "采集点删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(dcCollectionPointService.deleteByIds(ids));
    }
}
