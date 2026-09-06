package com.nexus.erp.prd.controller;

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
import com.nexus.erp.prd.domain.bo.PrdSubcontractBo;
import com.nexus.erp.prd.domain.bo.PrdSubcontractDocBo;
import com.nexus.erp.prd.domain.bo.PrdSubcontractQuoteBo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractVo;
import com.nexus.erp.prd.service.IPrdSubcontractClosureService;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 委外订单 Controller
 * <p>
 * 提供委外订单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：下达、开始、完工、关闭</li>
 *   <li>PCBA 闭环：资料带出、BOM快照、报价、工程文件</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/subcontract")
public class PrdSubcontractController extends BaseController {

    private final IPrdSubcontractService subcontractService;
    private final IPrdSubcontractClosureService closureService;

    /**
     * 分页查询委外订单列表
     * <p>
     * 权限标识：{@code prd:subcontract:list}</p>
     *
     * @param bo        查询参数，包含委外单号、供应商、物料等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 委外订单分页列表
     */
    @SaCheckPermission("prd:subcontract:list")
    @GetMapping("/list")
    public TableDataInfo<PrdSubcontractVo> list(PrdSubcontractBo bo, PageQuery pageQuery) {
        return subcontractService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出委外订单数据到 Excel
     * <p>权限标识：{@code prd:subcontract:export}</p>
     * <p>日志记录：title="委外订单"，businessType=EXPORT</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:subcontract:export")
    @Log(title = "委外订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdSubcontractBo bo, HttpServletResponse response) {
        List<PrdSubcontractVo> list = subcontractService.selectList(bo);
        ExcelUtil.exportExcel(list, "委外订单数据", PrdSubcontractVo.class, response);
    }

    /**
     * 根据主键获取委外订单详细信息
     * <p>
     * 权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 委外订单详细信息
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/{subcontractId}")
    public R<PrdSubcontractVo> getInfo(@PathVariable Long subcontractId) {
        return R.ok(subcontractService.selectById(subcontractId));
    }

    /**
     * 新增委外订单
     * <p>
     * 权限标识：{@code prd:subcontract:add}</p>
     * <p>日志记录：title="委外订单"，businessType=INSERT</p>
     *
     * @param bo 委外订单业务对象，包含供应商、物料、数量、价格等信息
     * @return 新增后的委外订单详细信息
     */
    @SaCheckPermission("prd:subcontract:add")
    @Log(title = "委外订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdSubcontractVo> add(@Validated @RequestBody PrdSubcontractBo bo) {
        subcontractService.insertByBo(bo);
        return R.ok(subcontractService.selectById(bo.getSubcontractId()));
    }

    /**
     * 修改委外订单
     * <p>
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单"，businessType=UPDATE</p>
     *
     * @param bo 委外订单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdSubcontractBo bo) {
        return toAjax(subcontractService.updateByBo(bo));
    }

    /**
     * 批量删除委外订单
     * <p>
     * 权限标识：{@code prd:subcontract:remove}</p>
     * <p>日志记录：title="委外订单"，businessType=DELETE</p>
     *
     * @param subcontractIds 要删除的委外订单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:subcontract:remove")
    @Log(title = "委外订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{subcontractIds}")
    public R<Void> remove(@PathVariable Long[] subcontractIds) {
        return toAjax(subcontractService.deleteByIds(subcontractIds));
    }

    /**
     * 下达委外订单
     * <p>
     * 将委外订单状态更新为已下达，并自动生成关联生产工单。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-下达"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-下达", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{subcontractId}")
    public R<Void> release(@PathVariable Long subcontractId) {
        subcontractService.release(subcontractId);
        return R.ok();
    }

    /**
     * 开始委外订单
     * <p>
     * 将委外订单状态更新为进行中，记录实际开始时间。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-开始"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-开始", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{subcontractId}")
    public R<Void> start(@PathVariable Long subcontractId) {
        subcontractService.start(subcontractId);
        return R.ok();
    }

    /**
     * 完工委外订单
     * <p>
     * 将委外订单状态更新为已完成，记录实际结束时间。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-完工"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{subcontractId}")
    public R<Void> complete(@PathVariable Long subcontractId) {
        subcontractService.complete(subcontractId);
        return R.ok();
    }

    /**
     * 关闭委外订单
     * <p>
     * 将委外订单状态更新为已关闭。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-关闭"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{subcontractId}")
    public R<Void> close(@PathVariable Long subcontractId) {
        subcontractService.close(subcontractId);
        return R.ok();
    }

    /**
     * 委外发料
     * <p>
     * 发料视为委外开始（进入进行中状态），业务上由仓库领料流程记录实际扣料。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-发料"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-发料", businessType = BusinessType.UPDATE)
    @PutMapping("/issue/{subcontractId}")
    public R<Void> issue(@PathVariable Long subcontractId) {
        subcontractService.start(subcontractId);
        return R.ok();
    }

    /**
     * 委外收货
     * <p>
     * 收货视为委外完工（进入已完成状态）。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-收货"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-收货", businessType = BusinessType.UPDATE)
    @PutMapping("/receive/{subcontractId}")
    public R<Void> receive(@PathVariable Long subcontractId) {
        subcontractService.complete(subcontractId);
        return R.ok();
    }

    /**
     * 提交审批委外订单
     * <p>
     * 将草稿状态更新为已提交，等待审批。
     * 权限标识：{@code prd:subcontract:edit}</p>
     * <p>日志记录：title="委外订单-提交审批"，businessType=UPDATE</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-提交审批", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{subcontractId}")
    public R<Void> submit(@PathVariable Long subcontractId) {
        subcontractService.submit(subcontractId);
        return R.ok();
    }

    /**
     * 查询委外订单操作日志（状态时间线）
     * <p>
     * 基于订单的状态字段与关键时间字段生成操作时间线。
     * 权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单 ID
     * @return 操作日志列表（[{operation, operator, time}]）
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/logs/{subcontractId}")
    public R<List<Map<String, Object>>> operationLogs(@PathVariable Long subcontractId) {
        return R.ok(subcontractService.getOperationLogs(subcontractId));
    }

    // ==================== PCBA 委外闭环: 资料带出 / BOM快照 / 报价 / 工程文件 ====================

    /**
     * 选择物料带出委外资料(当前生效BOM+行/委外工序/物料级工程文件库)
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param materialId 物料ID
     * @return {material, bom, bomLines, routingSteps, routingId, docs}
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/material-info/{materialId}")
    public R<Map<String, Object>> materialInfo(@PathVariable Long materialId) {
        return R.ok(closureService.getMaterialInfo(materialId));
    }

    /**
     * 查询订单 BOM 快照行
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 快照行列表
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/bom-lines/{subcontractId}")
    public R<?> bomLines(@PathVariable Long subcontractId) {
        return R.ok(closureService.getBomLines(subcontractId));
    }

    /**
     * 生成/重建订单 BOM 快照(仅草稿)
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 快照行数
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-BOM快照", businessType = BusinessType.UPDATE)
    @PutMapping("/generate-snapshot/{subcontractId}")
    public R<Integer> generateSnapshot(@PathVariable Long subcontractId) {
        return R.ok(closureService.generateBomSnapshot(subcontractId));
    }

    /**
     * 查询订单报价记录
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param subcontractId 委外订单ID
     * @return 报价列表
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/quotes/{subcontractId}")
    public R<?> quotes(@PathVariable Long subcontractId) {
        return R.ok(closureService.listQuotes(subcontractId));
    }

    /**
     * 保存报价记录(新增/编辑)
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param bo 报价业务对象
     * @return 报价ID
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-报价", businessType = BusinessType.UPDATE)
    @PostMapping("/quote")
    public R<Long> saveQuote(@RequestBody PrdSubcontractQuoteBo bo) {
        return R.ok(closureService.saveQuote(bo));
    }

    /**
     * 删除报价记录
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param quoteIds 报价ID数组
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-报价删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/quote/{quoteIds}")
    public R<Void> deleteQuotes(@PathVariable Long[] quoteIds) {
        return toAjax(closureService.deleteQuotes(quoteIds));
    }

    /**
     * 选定供应商(报价回写订单)
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param quoteId 选定报价ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-选定供应商", businessType = BusinessType.UPDATE)
    @PutMapping("/quote/select/{quoteId}")
    public R<Void> selectQuote(@PathVariable Long quoteId) {
        closureService.selectQuote(quoteId);
        return R.ok();
    }

    /**
     * 查询工程文件(materialId 或 subcontractId 二选一; subcontractId 为 0 时查物料级库)
     * <p>权限标识：{@code prd:subcontract:query}</p>
     *
     * @param materialId    物料ID(可空)
     * @param subcontractId 委外订单ID(可空, 0=物料级)
     * @return 文件列表
     */
    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/docs")
    public R<?> docs(@RequestParam(required = false) Long materialId,
                     @RequestParam(required = false) Long subcontractId) {
        Long scId = subcontractId != null && subcontractId == 0L ? null : subcontractId;
        return R.ok(closureService.listDocs(materialId, scId));
    }

    /**
     * 保存工程文件(先经 OSS 上传取得 fileOssId)
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param bo 文件业务对象
     * @return 文件ID
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-工程文件", businessType = BusinessType.UPDATE)
    @PostMapping("/doc")
    public R<Long> saveDoc(@RequestBody PrdSubcontractDocBo bo) {
        return R.ok(closureService.saveDoc(bo));
    }

    /**
     * 删除工程文件
     * <p>权限标识：{@code prd:subcontract:edit}</p>
     *
     * @param docIds 文件ID数组
     * @return 操作结果
     */
    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-工程文件删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/doc/{docIds}")
    public R<Void> deleteDocs(@PathVariable Long[] docIds) {
        return toAjax(closureService.deleteDocs(docIds));
    }
}
