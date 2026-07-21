package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmMiscRecptBo;
import com.nexus.erp.wm.domain.vo.WmMiscRecptVo;
import com.nexus.erp.wm.service.IWmMiscRecptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 杂项入库Controller
 * <p>
 * 提供杂项入库单的 RESTful API 接口，包括 CRUD 操作、数据导出及执行入库等功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/miscrecpt")
public class WmMiscRecptController extends BaseController {

    /** 杂项收货业务服务，提供杂项收货相关能力 */
    private final IWmMiscRecptService wmMiscRecptService;

    /**
     * 分页查询杂项入库列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 杂项入库分页列表
     */
    @SaCheckPermission("wm:miscrecpt:list")
    @GetMapping("/list")
    public TableDataInfo<WmMiscRecptVo> list(WmMiscRecptBo bo, PageQuery pageQuery) {
        return wmMiscRecptService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出杂项入库数据到 Excel
     * <p>权限标识：{@code wm:miscrecpt:export}</p>
     * <p>日志记录：title="杂项入库"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:miscrecpt:export")
    @Log(title = "杂项入库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMiscRecptBo bo, HttpServletResponse response) {
        List<WmMiscRecptVo> list = wmMiscRecptService.selectList(bo);
        ExcelUtil.exportExcel(list, "杂项入库数据", WmMiscRecptVo.class, response);
    }

    /**
     * 获取杂项入库详细信息
     * <p>权限标识：{@code wm:miscrecpt:query}</p>
     *
     * @param recptId 杂项入库ID
     * @return 杂项入库详细信息
     */
    @SaCheckPermission("wm:miscrecpt:query")
    @GetMapping("/{recptId}")
    public R<WmMiscRecptVo> getInfo(@PathVariable Long recptId) {
        return R.ok(wmMiscRecptService.selectById(recptId));
    }

    /**
     * 新增杂项入库
     * <p>权限标识：{@code wm:miscrecpt:add}</p>
     * <p>日志记录：title="杂项入库"，businessType=INSERT</p>
     *
     * @param bo 杂项入库业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscrecpt:add")
    @Log(title = "杂项入库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMiscRecptBo bo) {
        return toAjax(wmMiscRecptService.insertByBo(bo));
    }

    /**
     * 修改杂项入库
     * <p>权限标识：{@code wm:miscrecpt:edit}</p>
     * <p>日志记录：title="杂项入库"，businessType=UPDATE</p>
     *
     * @param bo 杂项入库业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscrecpt:edit")
    @Log(title = "杂项入库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMiscRecptBo bo) {
        return toAjax(wmMiscRecptService.updateByBo(bo));
    }

    /**
     * 删除杂项入库
     * <p>权限标识：{@code wm:miscrecpt:remove}</p>
     * <p>日志记录：title="杂项入库"，businessType=DELETE</p>
     *
     * @param recptIds 杂项入库ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscrecpt:remove")
    @Log(title = "杂项入库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recptIds}")
    public R<Void> remove(@PathVariable Long[] recptIds) {
        return toAjax(wmMiscRecptService.deleteByIds(recptIds));
    }

    /**
     * 执行杂项入库（增加库存）
     * <p>权限标识：{@code wm:miscrecpt:edit}</p>
     * <p>日志记录：title="杂项入库"，businessType=UPDATE</p>
     *
     * @param recptId 杂项入库ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscrecpt:edit")
    @Log(title = "杂项入库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{recptId}")
    public R<Void> execute(@PathVariable Long recptId) {
        wmMiscRecptService.execute(recptId);
        return R.ok();
    }
}
