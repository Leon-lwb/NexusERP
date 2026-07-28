package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.ncr.domain.bo.NcrDefectCodeBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectCodeVo;
import com.nexus.mes.ncr.service.INcrDefectCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES NCR缺陷代码 Controller
 * <p>属于 MES NCR 不合格品管理模块，提供 缺陷代码(树形) 的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/ncr/defect-code")
public class NcrDefectCodeController extends BaseController {

    private final INcrDefectCodeService ncrDefectCodeService;

    /**
     * 查询缺陷代码列表(树形)
     *
     * @param bo 查询条件
     * @return 缺陷代码树形列表
     */
    @SaCheckPermission("mes:ncr:defectcode:list")
    @GetMapping("/list")
    public R<List<NcrDefectCodeVo>> list(NcrDefectCodeBo bo) {
        return R.ok(ncrDefectCodeService.selectList(bo));
    }

    /**
     * 查询缺陷代码树(全部,不分页)
     *
     * @return 缺陷代码树形列表
     */
    @SaCheckPermission("mes:ncr:defectcode:list")
    @GetMapping("/tree")
    public R<List<NcrDefectCodeVo>> tree() {
        return R.ok(ncrDefectCodeService.selectList(new NcrDefectCodeBo()));
    }

    /**
     * 根据ID获取缺陷代码
     *
     * @param codeId 代码ID
     * @return 缺陷代码详情
     */
    @SaCheckPermission("mes:ncr:defectcode:query")
    @GetMapping("/{codeId}")
    public R<NcrDefectCodeVo> getInfo(@PathVariable Long codeId) {
        return R.ok(ncrDefectCodeService.selectById(codeId));
    }

    /**
     * 新增缺陷代码
     *
     * @param bo 缺陷代码业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:defectcode:add")
    @Log(title = "缺陷代码新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody @Validated NcrDefectCodeBo bo) {
        return toAjax(ncrDefectCodeService.insertByBo(bo));
    }

    /**
     * 修改缺陷代码
     *
     * @param bo 缺陷代码业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:defectcode:edit")
    @Log(title = "缺陷代码修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody @Validated NcrDefectCodeBo bo) {
        return toAjax(ncrDefectCodeService.updateByBo(bo));
    }

    /**
     * 删除缺陷代码
     *
     * @param ids 代码ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:defectcode:remove")
    @Log(title = "缺陷代码删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(ncrDefectCodeService.deleteByIds(ids));
    }
}