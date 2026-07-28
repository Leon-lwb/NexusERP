package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.ncr.domain.bo.NcrDefectBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectVo;
import com.nexus.mes.ncr.service.INcrDefectService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES NCR缺陷明细 Controller
 * <p>属于 MES NCR 不合格品管理模块，提供 NCR 缺陷明细 的查询和补录接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/ncr/defect")
public class NcrDefectController extends BaseController {

    private final INcrDefectService ncrDefectService;

    /**
     * 查询缺陷明细列表(按 NCR 单ID)
     *
     * @param ncrId NCR单ID
     * @return 缺陷明细列表
     */
    @SaCheckPermission("mes:ncr:defect:list")
    @GetMapping("/list")
    public R<List<NcrDefectVo>> list(@RequestParam Long ncrId) {
        return R.ok(ncrDefectService.selectByNcrId(ncrId));
    }

    /**
     * 新增缺陷明细(补录)
     *
     * @param bo 缺陷明细业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:defect:add")
    @Log(title = "缺陷明细新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody @Validated NcrDefectBo bo) {
        return toAjax(ncrDefectService.insertByBo(bo));
    }
}