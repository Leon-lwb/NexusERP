package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.web.core.BaseController;
import jakarta.servlet.http.HttpServletResponse;
import com.nexus.mes.pro.domain.bo.ProSnBo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.service.IProSnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MES SN序列号 Controller
 * <p>属于 MES 生产管理模块，提供 SN序列号追溯 相关 REST 接口（分页查询、增删改及业务动作）。</p>
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
     * 导出SN列表(委外SN发外协厂镭雕导入用, 支持按委外单号过滤)
     */
    @SaCheckPermission("mes:pro:sn:export")
    @Log(title = "MES SN序列号", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProSnBo bo, HttpServletResponse response) {
        List<ProSnVo> list = proSnService.selectList(bo);
        ExcelUtil.exportExcel(list, "SN序列号数据", ProSnVo.class, response);
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
    public R<List<ProSnVo>> generate(@RequestParam(required=false) String commandId, @RequestParam Long cardId,
                                     @RequestParam Integer quantity,
                                     @RequestParam(required = false) String initialStatus,
                                     @RequestParam(required = false) Long lotId) {
        return R.ok(proSnService.generateSnForCard(commandId==null?"MES:CARD:"+cardId:commandId,cardId, quantity, initialStatus, lotId));
    }

    /**
     * 手动创建单个SN(可指定parentSnId用于组装追溯)
     */
    @SaCheckPermission("mes:pro:sn:add")
    @Log(title = "SN手动创建", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProSnVo> add(@RequestParam(required=false) String commandId, @RequestParam(required = false) Long mesWoId,
                          @RequestParam(required = false) Long cardId,
                          @RequestParam Long materialId,
                          @RequestParam(required=false) String snCode,
                          @RequestParam(required = false) Long parentSnId,
                          @RequestParam(required = false) String remark,
                          @RequestParam(required = false) Long lotId) {
        return R.ok(commandId==null?proSnService.createSn(mesWoId,cardId,materialId,snCode,parentSnId,remark,lotId):proSnService.createSn(commandId,mesWoId,cardId,materialId,snCode,parentSnId,remark,lotId));
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
