package com.nexus.erp.bd.controller;

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
import com.nexus.erp.bd.domain.bo.BdBomBo;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.domain.vo.BomComparisonVo;
import com.nexus.erp.bd.domain.vo.BomCostRollupVo;
import com.nexus.erp.bd.domain.vo.BomTreeNodeVo;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.service.IBdBomService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BOM（物料清单）Controller
 * <p>
 * 管理产品的物料清单（BOM）结构，包含 BOM 版本管理、完整性校验等功能。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/bom")
public class BdBomController extends BaseController {

    private final IBdBomService bdBomService;

    /**
     * 分页查询 BOM 列表
     * <p>权限标识：{@code bd:bom:list}</p>
     *
     * @param bo        查询参数，包含 BOM 编号、物料编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return BOM 分页列表
     */
    @SaCheckPermission("bd:bom:list")
    @GetMapping("/list")
    public TableDataInfo<BdBomVo> list(BdBomBo bo, PageQuery pageQuery) {
        return bdBomService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出 BOM 数据到 Excel
     * <p>权限标识：{@code bd:bom:export}</p>
     * <p>日志记录：title="BOM"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:bom:export")
    @Log(title = "BOM", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdBomBo bo, HttpServletResponse response) {
        List<BdBomVo> list = bdBomService.selectList(bo);
        ExcelUtil.exportExcel(list, "BOM数据", BdBomVo.class, response);
    }

    /**
     * 根据主键获取 BOM 详细信息
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param id BOM ID
     * @return BOM 详细信息
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/{id}")
    public R<BdBomVo> getInfo(@PathVariable Long id) {
        return R.ok(bdBomService.selectById(id));
    }

    /**
     * 新增 BOM
     * <p>权限标识：{@code bd:bom:add}</p>
     * <p>日志记录：title="BOM"，businessType=INSERT</p>
     *
     * @param bo BOM 业务对象，包含物料清单的组成结构信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdBomBo bo) {
        return toAjax(bdBomService.insertByBo(bo));
    }

    /**
     * 修改 BOM
     * <p>权限标识：{@code bd:bom:edit}</p>
     * <p>日志记录：title="BOM"，businessType=UPDATE</p>
     *
     * @param bo BOM 业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdBomBo bo) {
        return toAjax(bdBomService.updateByBo(bo));
    }

    /**
     * 批量删除 BOM
     * <p>权限标识：{@code bd:bom:remove}</p>
     * <p>日志记录：title="BOM"，businessType=DELETE</p>
     *
     * @param ids BOM ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:remove")
    @Log(title = "BOM", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdBomService.deleteByIds(ids));
    }

    /**
     * 激活 BOM 版本
     * <p>
     * 将指定 BOM 的某个版本标记为激活状态，激活后的版本将用于生产工单的 BOM 展开。</p>
     * <p>权限标识：{@code bd:bom:edit}</p>
     * <p>日志记录：title="BOM"，businessType=UPDATE</p>
     *
     * @param bomId BOM ID
     * @return 操作结果
     */
    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM", businessType = BusinessType.UPDATE)
    @PutMapping("/activateVersion/{bomId}")
    public R<Void> activateVersion(@PathVariable Long bomId) {
        bdBomService.activateVersion(bomId);
        return R.ok();
    }

    /**
     * 校验 BOM 完整性
     * <p>
     * 检查 BOM 结构中是否存在物料缺失、循环引用或数量异常等完整性问题。</p>
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId BOM ID
     * @return 校验结果信息
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/verify/{bomId}")
    public R<String> verify(@PathVariable Long bomId) {
        String result = bdBomService.verifyBom(bomId);
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把校验结果误塞进 msg
        return R.ok("查询成功", result);
    }

    /**
     * 获取BOM树形结构（递归展开所有层级）
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId BOM ID
     * @return BOM树节点
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/tree/{bomId}")
    public R<BomTreeNodeVo> getBomTree(@PathVariable Long bomId) {
        return R.ok(bdBomService.getBomTree(bomId));
    }

    /**
     * 获取某 BOM 的直接子 BOM 列表（仅当前版本，用于列表树形表格懒加载展开）
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId 父BOM ID
     * @return 直接子BOM列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/children/{bomId}")
    public R<List<BdBomVo>> getDirectChildren(@PathVariable Long bomId) {
        return R.ok(bdBomService.getDirectChildren(bomId));
    }

    /**
     * 获取缩进式BOM（扁平列表+层级标记）
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId BOM ID
     * @return 缩进式BOM行列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/indented/{bomId}")
    public R<List<IndentedBomLineVo>> getIndentedBom(@PathVariable Long bomId) {
        return R.ok(bdBomService.getIndentedBom(bomId));
    }

    /**
     * 获取BOM展开结果（所有叶子物料汇总）
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId BOM ID
     * @return 叶子物料汇总列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/explode/{bomId}")
    public R<List<IndentedBomLineVo>> explodeBom(@PathVariable Long bomId) {
        return R.ok(bdBomService.explodeBom(bomId));
    }

    /**
     * Where-Used反查：查询某物料被哪些BOM使用
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param materialId 物料ID
     * @return 引用该物料的BOM列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/whereUsed/{materialId}")
    public R<List<BdBomVo>> whereUsed(@PathVariable Long materialId) {
        return R.ok(bdBomService.whereUsed(materialId));
    }

    /**
     * BOM复制：从已有BOM复制创建新版本
     * <p>权限标识：{@code bd:bom:add}</p>
     * <p>日志记录：title="BOM"，businessType=INSERT</p>
     *
     * @param sourceBomId 源BOM ID
     * @param newVersion  新版本号
     * @return 新BOM的ID
     */
    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM", businessType = BusinessType.INSERT)
    @PostMapping("/copy/{sourceBomId}")
    public R<Long> copyBom(@PathVariable Long sourceBomId, @RequestParam String newVersion) {
        return R.ok(bdBomService.copyBom(sourceBomId, newVersion));
    }

    /**
     * BOM比较：对比两个版本的差异
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId1 BOM1 ID
     * @param bomId2 BOM2 ID
     * @return 比较结果
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/compare")
    public R<BomComparisonVo> compareBom(@RequestParam Long bomId1, @RequestParam Long bomId2) {
        return R.ok(bdBomService.compareBom(bomId1, bomId2));
    }

    /**
     * BOM成本卷积：汇总物料成本(叶子标准成本×累计用量) + 工序成本(工时×费率)
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bomId BOM ID
     * @return 成本卷积结果
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/costRollup/{bomId}")
    public R<BomCostRollupVo> costRollup(@PathVariable Long bomId) {
        return R.ok(bdBomService.costRollup(bomId));
    }
}
