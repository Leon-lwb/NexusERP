package com.nexus.mes.flash.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.domain.bo.MesResourcePoolBo;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 刷写资源池 Controller
 * <p>MAC/SN 资源池管理 + 资源项导入 + 分配(MAC刷写时 Provider 调 allocate)</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/pool")
public class MesResourcePoolController {

    private final IMesResourcePoolService mesResourcePoolService;

    @SaCheckPermission("mes:flash:pool:list")
    @GetMapping("/list")
    public TableDataInfo<MesResourcePool> list(MesResourcePoolBo bo, PageQuery pageQuery) {
        return mesResourcePoolService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:flash:pool:query")
    @GetMapping("/{poolId}")
    public R<MesResourcePool> getInfo(@PathVariable Long poolId) {
        return R.ok(mesResourcePoolService.selectById(poolId));
    }

    @SaCheckPermission("mes:flash:pool:add")
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody MesResourcePoolBo bo) {
        return toAjax(mesResourcePoolService.insertByBo(bo));
    }

    @SaCheckPermission("mes:flash:pool:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody MesResourcePoolBo bo) {
        return toAjax(mesResourcePoolService.updateByBo(bo));
    }

    @SaCheckPermission("mes:flash:pool:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesResourcePoolService.deleteByIds(ids));
    }

    /** 批量导入资源项(body=换行/逗号分隔的文本) */
    @SaCheckPermission("mes:flash:pool:import")
    @PostMapping("/import/{poolId}")
    public R<Integer> importItems(@PathVariable Long poolId, @RequestBody String text) {
        return R.ok(mesResourcePoolService.importItems(poolId, text));
    }

    /** 手动分配(测试/补分配用; 刷写时由 MacPoolFlashProvider 内部调) */
    @SaCheckPermission("mes:flash:pool:edit")
    @PostMapping("/allocate")
    public R<MesResourceItem> allocate(@RequestParam Long poolId, @RequestParam Long snId,
                                       @RequestParam(required = false) String snCode) {
        return R.ok(mesResourcePoolService.allocate(poolId, snId, snCode));
    }

    private R<Void> toAjax(boolean ok) {
        return ok ? R.ok() : R.fail("操作失败");
    }
}
