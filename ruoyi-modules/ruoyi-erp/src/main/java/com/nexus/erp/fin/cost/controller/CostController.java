package com.nexus.erp.fin.cost.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.fin.cost.poster.CostPoster;
import com.nexus.erp.fin.cost.service.CostService;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 存货成本 Controller
 *
 * @author nexus
 * @date 2026-08-01
 */
@Validated
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/cost")
public class CostController extends BaseController {

    private final CostPoster costPoster;
    private final CostService costService;
    private final WmMaterialReceiveMapper receiveMapper;

    /** 按库存流水ID过账(幂等) */
    @SaCheckPermission("fin:cost:post")
    @Log(title = "存货成本过账", businessType = BusinessType.UPDATE)
    @PostMapping("/postTx/{txId}")
    public R<Void> postTx(@PathVariable Long txId) {
        costPoster.postTx(txId);
        return R.ok();
    }

    /** 期间采购暂估(幂等):未开票收货单按暂估入账 */
    @SaCheckPermission("fin:cost:provision")
    @Log(title = "采购暂估入账", businessType = BusinessType.UPDATE)
    @PostMapping("/provision/{periodName}")
    public R<Void> provision(@PathVariable String periodName) {
        // 简化:全部未开票收货单暂估(发票联动后置)
        List<WmMaterialReceive> receives = receiveMapper.selectList(Wrappers.lambdaQuery(WmMaterialReceive.class));
        costService.provision(receives, periodName);
        return R.ok();
    }
}
