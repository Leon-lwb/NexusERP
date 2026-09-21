package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqSpareReturnBo;
import com.nexus.mes.eq.domain.vo.EqSpareReturnVo;
import com.nexus.mes.eq.service.IEqSpareReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES备件退库单 Controller（MES-EQ-04 收口）
 * <p>
 * 契约（前端已按此并行实现）：
 * <pre>
 * GET    /mes/eq/spare-return/list                分页  mes:eq:spare:return:list
 * GET    /mes/eq/spare-return/{returnId}          详情(含明细行)  mes:eq:spare:return:query
 * POST   /mes/eq/spare-return                     新增草稿(含明细)  mes:eq:spare:return:add
 * PUT    /mes/eq/spare-return                     修改(仅 DRAFT)  mes:eq:spare:return:edit
 * DELETE /mes/eq/spare-return/{ids}               删除(仅 DRAFT)  mes:eq:spare:return:remove
 * PUT    /mes/eq/spare-return/{returnId}/confirm  确认退库  mes:eq:spare:return:confirm
 * </pre>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/spare-return")
public class EqSpareReturnController extends BaseController {

    private final IEqSpareReturnService eqSpareReturnService;

    /**
     * 分页查询退库单
     *
     * @param bo        查询条件(returnNo/status/returnType/beginTime/endTime)
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:spare:return:list")
    @GetMapping("/list")
    public TableDataInfo<EqSpareReturnVo> list(EqSpareReturnBo bo, PageQuery pageQuery) {
        return eqSpareReturnService.selectPageList(bo, pageQuery);
    }

    /**
     * 退库单详情(含明细行)
     *
     * @param returnId 退库单ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:return:query")
    @GetMapping("/{returnId}")
    public R<EqSpareReturnVo> getInfo(@PathVariable Long returnId) {
        return R.ok(eqSpareReturnService.selectById(returnId));
    }

    /**
     * 新增退库单草稿(含明细)
     *
     * @param bo 业务对象
     * @return 退库单ID
     */
    @SaCheckPermission("mes:eq:spare:return:add")
    @Log(title = "备件退库单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Validated @RequestBody EqSpareReturnBo bo) {
        return R.ok(eqSpareReturnService.insertByBo(bo));
    }

    /**
     * 修改退库单(仅 DRAFT)
     *
     * @param bo 业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:return:edit")
    @Log(title = "备件退库单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody EqSpareReturnBo bo) {
        return toAjax(eqSpareReturnService.updateByBo(bo));
    }

    /**
     * 删除退库单(仅 DRAFT)
     *
     * @param ids 退库单ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:return:remove")
    @Log(title = "备件退库单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqSpareReturnService.deleteByIds(ids));
    }

    /**
     * 确认退库(逐行 TXN_RETURN 过账到 ERP 入库方向)
     *
     * @param returnId 退库单ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:return:confirm")
    @Log(title = "备件退库单确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{returnId}/confirm")
    public R<Boolean> confirm(@PathVariable Long returnId) {
        return R.ok("退库单已确认", eqSpareReturnService.confirm(returnId));
    }
}
