package org.dromara.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;
import org.dromara.erp.bd.service.IBdBomService;
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
        return R.ok(result);
    }
}
