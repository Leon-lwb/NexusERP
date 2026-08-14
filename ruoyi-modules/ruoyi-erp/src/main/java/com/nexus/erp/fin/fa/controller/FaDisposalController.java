package com.nexus.erp.fin.fa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.fa.domain.bo.FaDisposalBo;
import com.nexus.erp.fin.fa.domain.vo.FaDisposalVo;
import com.nexus.erp.fin.fa.service.IFaDisposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 资产处置 Controller
 *
 * @author nexus
 * @date 2026-08-01
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/fa/disposal")
public class FaDisposalController extends BaseController {

    private final IFaDisposalService faDisposalService;

    /** 分页查询处置记录 */
    @SaCheckPermission("fin:fa:disposal:list")
    @GetMapping("/list")
    public TableDataInfo<FaDisposalVo> list(FaDisposalBo bo, PageQuery pageQuery) {
        return faDisposalService.queryPageList(bo, pageQuery);
    }

    /** 处置资产并过账 */
    @SaCheckPermission("fin:fa:disposal:post")
    @Log(title = "资产处置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FaDisposalBo bo) {
        return toAjax(faDisposalService.dispose(bo));
    }
}
