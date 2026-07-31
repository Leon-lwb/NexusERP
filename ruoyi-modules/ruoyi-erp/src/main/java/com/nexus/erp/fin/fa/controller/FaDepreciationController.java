package com.nexus.erp.fin.fa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.fa.domain.bo.FaDepreciationRunBo;
import com.nexus.erp.fin.fa.domain.vo.FaDepreciationVo;
import com.nexus.erp.fin.fa.service.IFaDepreciationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 固定资产折旧 Controller
 *
 * @author nexus
 * @date 2026-08-01
 */
@Validated
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/fa/depreciation")
public class FaDepreciationController extends BaseController {

    private final IFaDepreciationService faDepreciationService;

    /** 分页查询折旧记录 */
    @SaCheckPermission("fin:fa:depreciation:list")
    @GetMapping("/list")
    public TableDataInfo<FaDepreciationVo> list(FaDepreciationRunBo bo, PageQuery pageQuery) {
        return faDepreciationService.queryPageList(bo, pageQuery);
    }

    /** 折旧计划预览(某期间应计提明细,不过账) */
    @SaCheckPermission("fin:fa:depreciation:list")
    @GetMapping("/plan")
    public R<List<FaDepreciationVo>> plan(@RequestParam Long periodId) {
        return R.ok(faDepreciationService.preview(periodId));
    }

    /** 按期间计提折旧并过账(幂等) */
    @SaCheckPermission("fin:fa:depreciation:run")
    @Log(title = "固定资产折旧计提", businessType = BusinessType.UPDATE)
    @PostMapping("/run")
    public R<Void> run(@Validated @RequestBody FaDepreciationRunBo bo) {
        faDepreciationService.runDepreciation(bo.getPeriodId());
        return R.ok();
    }
}
