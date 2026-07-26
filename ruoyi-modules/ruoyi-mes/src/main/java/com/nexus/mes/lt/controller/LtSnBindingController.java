package com.nexus.mes.lt.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.domain.vo.LtSnBindingVo;
import com.nexus.mes.lt.service.ILtSnBindingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES SN批次绑定 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/lt/binding")
public class LtSnBindingController extends BaseController {

    private final ILtSnBindingService bindingService;

    /**
     * 分页查询SN批次绑定列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return SN批次绑定分页数据
     */
    @SaCheckPermission("mes:lt:binding:list")
    @GetMapping("/list")
    public TableDataInfo<LtSnBindingVo> list(LtSnBindingBo bo, PageQuery pageQuery) {
        return bindingService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取绑定详情
     *
     * @param bindingId 绑定ID
     * @return 绑定详情
     */
    @SaCheckPermission("mes:lt:binding:query")
    @GetMapping("/{bindingId}")
    public R<LtSnBindingVo> getInfo(@PathVariable Long bindingId) {
        return R.ok(bindingService.selectById(bindingId));
    }

    /**
     * 新增SN-批次绑定
     *
     * @param bo SN批次绑定业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:lt:binding:add")
    @Log(title = "SN批次绑定", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody LtSnBindingBo bo) {
        return toAjax(bindingService.insertByBo(bo));
    }

    /**
     * 批量删除绑定
     *
     * @param ids 绑定ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:lt:binding:remove")
    @Log(title = "SN批次绑定删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bindingService.deleteByIds(ids));
    }

    /**
     * 按SN ID查询其批次绑定(追溯用)
     *
     * @param snId SN ID
     * @return 绑定列表
     */
    @SaCheckPermission("mes:lt:binding:list")
    @GetMapping("/by-sn/{snId}")
    public R<List<LtSnBindingVo>> listBySn(@PathVariable Long snId) {
        return R.ok(bindingService.selectBySnId(snId));
    }

    /**
     * 按批次ID查询所有SN绑定(追溯用)
     *
     * @param lotId 批次ID
     * @return 绑定列表
     */
    @SaCheckPermission("mes:lt:binding:list")
    @GetMapping("/by-lot/{lotId}")
    public R<List<LtSnBindingVo>> listByLot(@PathVariable Long lotId) {
        return R.ok(bindingService.selectByLotId(lotId));
    }
}
