package com.nexus.mes.flash.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.MesContentFile;
import com.nexus.mes.flash.domain.bo.MesContentFileBo;
import com.nexus.mes.flash.service.IMesContentFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 刷写内容档案(配置文件) Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/content")
public class MesContentFileController {

    private final IMesContentFileService mesContentFileService;

    @SaCheckPermission("mes:flash:content:list")
    @GetMapping("/list")
    public TableDataInfo<MesContentFile> list(MesContentFileBo bo, PageQuery pageQuery) {
        return mesContentFileService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:flash:content:query")
    @GetMapping("/{contentFileId}")
    public R<MesContentFile> getInfo(@PathVariable Long contentFileId) {
        return R.ok(mesContentFileService.selectById(contentFileId));
    }

    @SaCheckPermission("mes:flash:content:add")
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody MesContentFileBo bo) {
        return toAjax(mesContentFileService.insertByBo(bo));
    }

    @SaCheckPermission("mes:flash:content:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody MesContentFileBo bo) {
        return toAjax(mesContentFileService.updateByBo(bo));
    }

    @SaCheckPermission("mes:flash:content:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesContentFileService.deleteByIds(ids));
    }

    /** 发布: DRAFT -> RELEASED */
    @SaCheckPermission("mes:flash:content:release")
    @PutMapping("/release/{contentFileId}")
    public R<Void> release(@PathVariable Long contentFileId) {
        return toAjax(mesContentFileService.release(contentFileId));
    }

    private R<Void> toAjax(boolean ok) {
        return ok ? R.ok() : R.fail("操作失败");
    }
}
