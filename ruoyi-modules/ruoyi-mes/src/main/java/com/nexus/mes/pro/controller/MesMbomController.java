package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.MesMbomBo;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import com.nexus.mes.pro.service.IMesMbomService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES制造BOM Controller
 * <p>
 * 管理MES侧制造BOM(MBOM): 每个ERP BOM对应1个MBOM,
 * MBOM行承载子件->工序->工位->作业内容的制造关联。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/mbom")
public class MesMbomController extends BaseController {

    private final IMesMbomService mesMbomService;

    /** 分页查询MBOM列表 */
    @SaCheckPermission("mes:pro:mbom:list")
    @GetMapping("/list")
    /**
     * 分页查询列表
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<MesMbomVo> list(MesMbomBo bo, PageQuery pageQuery) {
        return mesMbomService.selectPageList(bo, pageQuery);
    }

    /** 根据ID获取MBOM详情(含明细行) */
    @SaCheckPermission("mes:pro:mbom:query")
    @GetMapping("/{mbomId}")
    /**
     * 获取
     * @param mbomId MBOMID
     * @return 操作结果
     */
    public R<MesMbomVo> getInfo(@PathVariable Long mbomId) {
        return R.ok(mesMbomService.selectById(mbomId));
    }

    /** 根据ERP BOM ID获取MBOM(1:1, 用于工单详情展示) */
    @SaCheckPermission("mes:pro:mbom:query")
    @GetMapping("/byBom/{bomId}")
    /**
     * 获取
     * @param bomId BOMID
     * @return 操作结果
     */
    public R<MesMbomVo> getByBomId(@PathVariable Long bomId) {
        return R.ok(mesMbomService.selectByBomId(bomId));
    }

    /** 新增MBOM */
    @SaCheckPermission("mes:pro:mbom:add")
    @PostMapping
    /**
     * 新增
     * @param bo bo
     * @return 操作结果
     */
    public R<Void> add(@Validated @RequestBody MesMbomBo bo) {
        return toAjax(mesMbomService.insertByBo(bo));
    }

    /** 修改MBOM */
    @SaCheckPermission("mes:pro:mbom:edit")
    @PutMapping
    /**
     * 编辑
     * @param bo bo
     * @return 操作结果
     */
    public R<Void> edit(@Validated @RequestBody MesMbomBo bo) {
        return toAjax(mesMbomService.updateByBo(bo));
    }

    /** 删除MBOM */
    @SaCheckPermission("mes:pro:mbom:remove")
    @DeleteMapping("/{ids}")
    /**
     * 删除
     * @param ids 批量操作ID集合
     * @return 操作结果
     */
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesMbomService.deleteByIds(ids));
    }

    /** 激活MBOM (DRAFT -> ACTIVE) */
    @SaCheckPermission("mes:pro:mbom:edit")
    @PutMapping("/activate/{mbomId}")
    /**
     * activate 操作
     * @param mbomId MBOMID
     * @return 操作结果
     */
    public R<Void> activate(@PathVariable Long mbomId) {
        mesMbomService.activate(mbomId);
        return R.ok();
    }

    /** 校验MBOM完整性 */
    @SaCheckPermission("mes:pro:mbom:query")
    @GetMapping("/verify/{mbomId}")
    /**
     * verify 操作
     * @param mbomId MBOMID
     * @return 操作结果
     */
    public R<String> verify(@PathVariable Long mbomId) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把校验结果误塞进 msg
        return R.ok("查询成功", mesMbomService.verify(mbomId));
    }
}
