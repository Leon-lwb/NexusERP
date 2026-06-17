package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProFlowCardBo;
import com.nexus.mes.pro.domain.vo.ProFlowCardVo;
import com.nexus.mes.pro.service.IProFlowCardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES流转卡 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/flow-card")
public class ProFlowCardController extends BaseController {

    private final IProFlowCardService proFlowCardService;

    /**
     * 分页查询流转卡列表
     */
    @SaCheckPermission("mes:pro:flowcard:list")
    @GetMapping("/list")
    public TableDataInfo<ProFlowCardVo> list(ProFlowCardBo bo, PageQuery pageQuery) {
        return proFlowCardService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出流转卡数据到Excel
     */
    @SaCheckPermission("mes:pro:flowcard:export")
    @Log(title = "MES流转卡", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProFlowCardBo bo, HttpServletResponse response) {
        List<ProFlowCardVo> list;
        if (bo.getMesWoId() != null) {
            list = proFlowCardService.selectByWoId(bo.getMesWoId());
        } else {
            // 未指定工单时导出空列表
            list = java.util.Collections.emptyList();
        }
        ExcelUtil.exportExcel(list, "MES流转卡数据", ProFlowCardVo.class, response);
    }

    /**
     * 根据ID获取流转卡详情(含工序步骤)
     */
    @SaCheckPermission("mes:pro:flowcard:query")
    @GetMapping("/{cardId}")
    public R<ProFlowCardVo> getInfo(@PathVariable Long cardId) {
        return R.ok(proFlowCardService.selectById(cardId));
    }

    /**
     * 按工单查询流转卡列表
     */
    @SaCheckPermission("mes:pro:flowcard:list")
    @GetMapping("/by-wo/{mesWoId}")
    public R<List<ProFlowCardVo>> listByWoId(@PathVariable Long mesWoId) {
        return R.ok(proFlowCardService.selectByWoId(mesWoId));
    }

    /**
     * 创建流转卡(从MES工单)
     */
    @SaCheckPermission("mes:pro:flowcard:add")
    @Log(title = "MES流转卡", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProFlowCardVo> add(@RequestParam Long mesWoId, @RequestParam BigDecimal quantity) {
        return R.ok(proFlowCardService.createFlowCard(mesWoId, quantity));
    }

    /**
     * 下达流转卡(CREATED -> RELEASED)
     */
    @SaCheckPermission("mes:pro:flowcard:release")
    @Log(title = "流转卡下达", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{cardId}")
    public R<Void> release(@PathVariable Long cardId) {
        proFlowCardService.releaseCard(cardId);
        return R.ok();
    }

    /**
     * 开始工序
     */
    @SaCheckPermission("mes:pro:flowcard:start")
    @Log(title = "流转卡开工", businessType = BusinessType.UPDATE)
    @PutMapping("/start-step")
    public R<Void> startStep(@RequestParam Long cardId,
                             @RequestParam Long stepRecordId,
                             @RequestParam(required = false) Long operatorId) {
        proFlowCardService.startStep(cardId, stepRecordId, operatorId);
        return R.ok();
    }

    /**
     * 完成工序
     */
    @SaCheckPermission("mes:pro:flowcard:complete")
    @Log(title = "流转卡完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete-step")
    public R<Void> completeStep(@RequestParam Long cardId,
                                @RequestParam Long stepRecordId,
                                @RequestParam BigDecimal goodQty,
                                @RequestParam(required = false, defaultValue = "0") BigDecimal defectQty,
                                @RequestParam(required = false, defaultValue = "0") BigDecimal scrapQty) {
        proFlowCardService.completeStep(cardId, stepRecordId, goodQty, defectQty, scrapQty);
        return R.ok();
    }

    /**
     * 完成流转卡
     */
    @SaCheckPermission("mes:pro:flowcard:complete")
    @Log(title = "流转卡完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{cardId}")
    public R<Void> complete(@PathVariable Long cardId) {
        proFlowCardService.completeCard(cardId);
        return R.ok();
    }

    /**
     * 关闭流转卡(COMPLETED -> CLOSED)
     */
    @SaCheckPermission("mes:pro:flowcard:edit")
    @Log(title = "流转卡关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{cardId}")
    public R<Void> close(@PathVariable Long cardId) {
        proFlowCardService.closeCard(cardId);
        return R.ok();
    }
}
