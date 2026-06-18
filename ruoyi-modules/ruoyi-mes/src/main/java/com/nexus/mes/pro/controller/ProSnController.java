package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProSnBo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.service.IProSnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MES SN序列号 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/sn")
public class ProSnController extends BaseController {

    private final IProSnService proSnService;

    /**
     * 分页查询SN列表
     */
    @SaCheckPermission("mes:pro:sn:list")
    @GetMapping("/list")
    public TableDataInfo<ProSnVo> list(ProSnBo bo, PageQuery pageQuery) {
        return proSnService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取SN详情
     */
    @SaCheckPermission("mes:pro:sn:query")
    @GetMapping("/{snId}")
    public R<ProSnVo> getInfo(@PathVariable Long snId) {
        return R.ok(proSnService.selectById(snId));
    }

    /**
     * 根据SN编码查询
     */
    @SaCheckPermission("mes:pro:sn:query")
    @GetMapping("/by-code/{snCode}")
    public R<ProSnVo> getByCode(@PathVariable String snCode) {
        return R.ok(proSnService.selectByCode(snCode));
    }

    /**
     * 追溯树(前向+后向)
     */
    @SaCheckPermission("mes:pro:sn:query")
    @GetMapping("/trace/{snId}")
    public R<Map<String, Object>> trace(@PathVariable Long snId) {
        return R.ok(proSnService.trace(snId));
    }

    /**
     * 从流转卡批量生成SN
     */
    @SaCheckPermission("mes:pro:sn:add")
    @Log(title = "SN批量生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public R<List<ProSnVo>> generate(@RequestParam Long cardId,
                                     @RequestParam Integer quantity,
                                     @RequestParam(required = false) String initialStatus) {
        return R.ok(proSnService.generateSnForCard(cardId, quantity, initialStatus));
    }

    /**
     * 手动创建单个SN(可指定parentSnId用于组装追溯)
     */
    @SaCheckPermission("mes:pro:sn:add")
    @Log(title = "SN手动创建", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProSnVo> add(@RequestParam(required = false) Long mesWoId,
                          @RequestParam(required = false) Long cardId,
                          @RequestParam Long materialId,
                          @RequestParam String snCode,
                          @RequestParam(required = false) Long parentSnId,
                          @RequestParam(required = false) String remark) {
        return R.ok(proSnService.createSn(mesWoId, cardId, materialId, snCode, parentSnId, remark));
    }

    /**
     * SN完工(IN_PRODUCTION -> COMPLETED)
     */
    @SaCheckPermission("mes:pro:sn:complete")
    @Log(title = "SN完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{snId}")
    public R<Void> complete(@PathVariable Long snId) {
        proSnService.completeSn(snId);
        return R.ok();
    }

    /**
     * SN报废(IN_PRODUCTION/COMPLETED -> SCRAPPED)
     */
    @SaCheckPermission("mes:pro:sn:scrap")
    @Log(title = "SN报废", businessType = BusinessType.UPDATE)
    @PutMapping("/scrap/{snId}")
    public R<Void> scrap(@PathVariable Long snId,
                         @RequestParam(required = false) String remark) {
        proSnService.scrapSn(snId, remark);
        return R.ok();
    }

    /**
     * SN发货(COMPLETED -> SHIPPED)
     */
    @SaCheckPermission("mes:pro:sn:ship")
    @Log(title = "SN发货", businessType = BusinessType.UPDATE)
    @PutMapping("/ship/{snId}")
    public R<Void> ship(@PathVariable Long snId) {
        proSnService.shipSn(snId);
        return R.ok();
    }
}
