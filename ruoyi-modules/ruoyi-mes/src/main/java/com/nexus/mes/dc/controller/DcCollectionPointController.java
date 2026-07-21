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
 * <p>属于MES 数据采集模块，提供采集点相关的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/dc/collection-point")
public class DcCollectionPointController extends BaseController {

    private final IDcCollectionPointService dcCollectionPointService;

    /**
     * 分页查询采集点列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:dc:point:list")
    @GetMapping("/list")
    public TableDataInfo<DcCollectionPointVo> list(DcCollectionPointBo bo, PageQuery pageQuery) {
        return dcCollectionPointService.selectPageList(bo, pageQuery);
    }

    /**
     * 获取采集点详情
     *
     * @param pointId 采集点ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:point:query")
    @GetMapping("/{pointId}")
    public R<DcCollectionPointVo> getInfo(@PathVariable Long pointId) {
        return R.ok(dcCollectionPointService.selectById(pointId));
    }

    /**
     * 新增采集点
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:point:add")
    @Log(title = "采集点新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<DcCollectionPointVo> add(@RequestBody DcCollectionPointBo bo) {
        return R.ok(dcCollectionPointService.insertByBo(bo));
    }

    /**
     * 更新采集点
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:point:edit")
    @Log(title = "采集点编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<DcCollectionPointVo> edit(@RequestBody DcCollectionPointBo bo) {
        return R.ok(dcCollectionPointService.updateByBo(bo));
    }

    /**
     * 删除采集点
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:dc:point:remove")
    @Log(title = "采集点删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(dcCollectionPointService.deleteByIds(ids));
    }
}
