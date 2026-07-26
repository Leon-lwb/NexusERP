package com.nexus.mes.sop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopVersionVo;
import com.nexus.mes.sop.service.ISopVersionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SOP版本 Controller
 * <p>版本历史查询 + 删除(CURRENT 版本守卫)。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/sop/version")
public class SopVersionController extends BaseController {

    private final ISopVersionService sopVersionService;

    /**
     * 分页查询版本列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/list")
    public TableDataInfo<SopVersionVo> list(SopVersionBo bo, PageQuery pageQuery) {
        return sopVersionService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询SOP的版本列表(版本历史, 按 published_time desc)
     *
     * @param sopId SOP文档ID
     * @return 版本列表
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/by-sop/{sopId}")
    public R<List<SopVersionVo>> bySop(@PathVariable Long sopId) {
        return R.ok(sopVersionService.selectBySop(sopId));
    }

    /**
     * 根据ID获取版本详情
     *
     * @param versionId 版本ID
     * @return 版本详情
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/{versionId}")
    public R<SopVersionVo> getInfo(@PathVariable Long versionId) {
        return R.ok(sopVersionService.selectById(versionId));
    }

    /**
     * 批量删除版本(守卫:CURRENT 版本不可删)
     *
     * @param ids 版本ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:sop:document:remove")
    @Log(title = "SOP版本删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(sopVersionService.deleteByIds(ids));
    }
}
