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
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单 Controller
 * <p>
 * 提供物料入库单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>执行入库操作</li>
 *   <li>流程审批集成</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/receive")
public class WmMaterialReceiveController extends BaseController {

    private final IWmMaterialReceiveService wmMaterialReceiveService;

    /**
     * 分页查询物料入库单列表
     * <p>
     * 权限标识：{@code wm:receive:list}</p>
     *
     * @param bo        查询参数，包含入库单号、供应商、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料入库单分页列表
     */
    @SaCheckPermission("wm:receive:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveVo> list(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单数据到 Excel
     * <p>权限标识：{@code wm:receive:export}</p>
     * <p>日志记录：title="物料入库单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:receive:export")
    @Log(title = "物料入库单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveVo> list = wmMaterialReceiveService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单数据", WmMaterialReceiveVo.class, response);
    }

    /**
     * 根据主键获取物料入库单详细信息
     * <p>
     * 权限标识：{@code wm:receive:query}</p>
     *
     * @param id 物料入库单 ID
     * @return 物料入库单详细信息
     */
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveService.selectById(id));
    }

    /**
     * 新增物料入库单
     * <p>
     * 权限标识：{@code wm:receive:add}</p>
     * <p>日志记录：title="物料入库单"，businessType=INSERT</p>
     *
     * @param bo 物料入库单业务对象，包含入库物料、数量等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:receive:add")
    @Log(title = "物料入库单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveBo bo) {
        return toAjax(wmMaterialReceiveService.insertByBo(bo));
    }

    /**
     * 修改物料入库单
     * <p>权限标识：{@code wm:receive:edit}</p>
     * <p>日志记录：title="物料入库单"，businessType=UPDATE</p>
     *
     * @param bo 物料入库单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     * @throws RuntimeException 校验失败时抛出运行时异常
     */
    @SaCheckPermission("wm:receive:edit")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveBo bo) {
        try {
            return toAjax(wmMaterialReceiveService.updateWithValidation(bo));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 批量删除物料入库单
     * <p>
     * 权限标识：{@code wm:receive:remove}</p>
     * <p>日志记录：title="物料入库单"，businessType=DELETE</p>
     *
     * @param ids 要删除的物料入库单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:receive:remove")
    @Log(title = "物料入库单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveService.deleteByIds(ids));
    }

    /**
     * 执行物料入库
     * <p>权限标识：{@code wm:receive:execute}</p>
     * <p>日志记录：title="物料入库单"，businessType=UPDATE</p>
     *
     * @param receiveId 入库单 ID
     * @return 操作结果
     * @throws RuntimeException 入库执行失败时抛出运行时异常
     */
    @SaCheckPermission("wm:receive:execute")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/execute/{receiveId}")
    public R<Void> executeReceive(@PathVariable Long receiveId) {
        try {
            wmMaterialReceiveService.executeReceive(receiveId);
            return R.ok("物料入库执行成功");
        } catch (RuntimeException e) {
            return R.fail("物料入库执行失败：" + e.getMessage());
        }
    }

    /**
     * 提交物料入库单并启动流程
     * <p>权限标识：{@code wm:receive:submit}</p>
     * <p>日志记录：title="物料入库单"，businessType=UPDATE</p>
     *
     * @param receiveId    入库单 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     * @throws RuntimeException 提交失败时抛出运行时异常
     */
    @SaCheckPermission("wm:receive:submit")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/{receiveId}/submit")
    public R<Void> submit(@PathVariable("receiveId") String receiveId,
                          @RequestParam(value = "selectFlowId", required = false) String selectFlowId) {
        try {
            return toAjax(wmMaterialReceiveService.submit(receiveId, selectFlowId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取物料入库单流程实例 ID
     * <p>
     * 权限标识：{@code wm:receive:query}</p>
     *
     * @param receiveId 入库单 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/{receiveId}/proc")
    public R<String> getProcInsId(@PathVariable("receiveId") String receiveId) {
        return R.ok("查询成功", wmMaterialReceiveService.getProcInsId(receiveId));
    }
}
