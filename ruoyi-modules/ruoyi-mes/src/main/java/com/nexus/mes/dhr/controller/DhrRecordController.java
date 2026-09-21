package com.nexus.mes.dhr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.dhr.domain.bo.DhrBuildBo;
import com.nexus.mes.dhr.domain.bo.DhrRecordBo;
import com.nexus.mes.dhr.domain.bo.DhrSignBo;
import com.nexus.mes.dhr.domain.vo.DhrRecordVo;
import com.nexus.mes.dhr.domain.vo.DhrSectionVo;
import com.nexus.mes.dhr.domain.vo.DhrVerifyVo;
import com.nexus.mes.dhr.service.IDhrRecordService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DHR（Device History Record / 批记录）Controller。
 *
 * <p>GMP/IATF 合规台账：分册在提交时冻结（append-only），提交/放行/驳回必须带电子签名
 * （二级认证，不接受 LOGIN_ONLY）。</p>
 *
 * <p>二级认证凭证（{@code credential}）一律走请求体，且操作日志切面用
 * {@code excludeParamNames = {"credential"}} 排除，避免密码明文落库。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/dhr")
public class DhrRecordController extends BaseController {

    private final IDhrRecordService dhrRecordService;

    /**
     * 分页查询 DHR 批记录列表。
     *
     * @param bo        查询条件（dhrNo/workOrderNo/materialCode/lotNo/sn/status/beginTime/endTime）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission("mes:dhr:record:list")
    @GetMapping("/list")
    public TableDataInfo<DhrRecordVo> list(DhrRecordBo bo, PageQuery pageQuery) {
        return dhrRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询 DHR 详情（含分册）。
     *
     * @param dhrId DHR 主键
     * @return DHR 详情
     */
    @SaCheckPermission("mes:dhr:record:query")
    @GetMapping("/{dhrId}")
    public R<DhrRecordVo> getInfo(@PathVariable Long dhrId) {
        return R.ok(dhrRecordService.selectById(dhrId));
    }

    /**
     * 查询分册列表。
     *
     * @param dhrId DHR 主键
     * @return 分册列表
     */
    @SaCheckPermission("mes:dhr:record:query")
    @GetMapping("/{dhrId}/sections")
    public R<List<DhrSectionVo>> sections(@PathVariable Long dhrId) {
        return R.ok(dhrRecordService.selectSections(dhrId));
    }

    /**
     * 查询签名链（DHR_SUBMIT / DHR_RELEASE / DHR_REJECT 合并）。
     *
     * @param dhrId DHR 主键
     * @return 签名列表
     */
    @SaCheckPermission("mes:dhr:record:query")
    @GetMapping("/{dhrId}/signatures")
    public R<List<SignatureVo>> signatures(@PathVariable Long dhrId) {
        return R.ok(dhrRecordService.listSignatures(dhrId));
    }

    /**
     * 校验快照哈希与签名链。
     *
     * @param dhrId DHR 主键
     * @return 校验结果
     */
    @SaCheckPermission("mes:dhr:record:query")
    @GetMapping("/{dhrId}/verify")
    public R<DhrVerifyVo> verify(@PathVariable Long dhrId) {
        return R.ok(dhrRecordService.verify(dhrId));
    }

    /**
     * 构建 DHR 批记录（聚合 10 个分册 + 落快照哈希，状态 DRAFT）。
     *
     * @param bo 构建入参（workOrderId/lotNo/sn/remark）
     * @return 构建后的 DHR（含分册）
     */
    @SaCheckPermission("mes:dhr:record:add")
    @Log(title = "DHR批记录构建", businessType = BusinessType.INSERT)
    @PostMapping("/build")
    public R<DhrRecordVo> build(@Validated @RequestBody DhrBuildBo bo) {
        return R.ok("构建成功", dhrRecordService.build(bo));
    }

    /**
     * 重建分册（仅 DRAFT）。
     *
     * @param dhrId DHR 主键
     * @return 重建后的 DHR（含分册）
     */
    @SaCheckPermission("mes:dhr:record:edit")
    @Log(title = "DHR批记录重建", businessType = BusinessType.UPDATE)
    @PutMapping("/{dhrId}/rebuild")
    public R<DhrRecordVo> rebuild(@PathVariable Long dhrId) {
        return R.ok("重建成功", dhrRecordService.rebuild(dhrId));
    }

    /**
     * 删除 DHR（仅 DRAFT）。
     *
     * @param dhrId DHR 主键
     * @return 操作结果
     */
    @SaCheckPermission("mes:dhr:record:remove")
    @Log(title = "DHR批记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{dhrId}")
    public R<Void> remove(@PathVariable Long dhrId) {
        return toAjax(dhrRecordService.deleteById(dhrId));
    }

    /**
     * 提交复核（DRAFT -> SUBMITTED，必须二级认证电子签名）。
     *
     * @param dhrId DHR 主键
     * @param bo    签署入参（authMethod/credential/remark）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dhr:record:submit")
    @Log(title = "DHR批记录提交复核", businessType = BusinessType.UPDATE, excludeParamNames = {"credential"})
    @PutMapping("/{dhrId}/submit")
    public R<Void> submit(@PathVariable Long dhrId, @Validated @RequestBody DhrSignBo bo) {
        return toAjax(dhrRecordService.submit(dhrId, bo));
    }

    /**
     * 放行（SUBMITTED -> RELEASED，必须二级认证电子签名）。
     *
     * @param dhrId DHR 主键
     * @param bo    签署入参（authMethod/credential/remark）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dhr:record:release")
    @Log(title = "DHR批记录放行", businessType = BusinessType.UPDATE, excludeParamNames = {"credential"})
    @PutMapping("/{dhrId}/release")
    public R<Void> release(@PathVariable Long dhrId, @Validated @RequestBody DhrSignBo bo) {
        return toAjax(dhrRecordService.release(dhrId, bo));
    }

    /**
     * 驳回（SUBMITTED -> REJECTED，必须二级认证电子签名 + 驳回原因）。
     *
     * @param dhrId DHR 主键
     * @param bo    签署入参（authMethod/credential/reason）
     * @return 操作结果
     */
    @SaCheckPermission("mes:dhr:record:reject")
    @Log(title = "DHR批记录驳回", businessType = BusinessType.UPDATE, excludeParamNames = {"credential"})
    @PutMapping("/{dhrId}/reject")
    public R<Void> reject(@PathVariable Long dhrId, @Validated @RequestBody DhrSignBo bo) {
        return toAjax(dhrRecordService.reject(dhrId, bo));
    }

    /**
     * 归档（RELEASED -> ARCHIVED，终态）。
     *
     * @param dhrId DHR 主键
     * @return 操作结果
     */
    @SaCheckPermission("mes:dhr:record:archive")
    @Log(title = "DHR批记录归档", businessType = BusinessType.UPDATE)
    @PutMapping("/{dhrId}/archive")
    public R<Void> archive(@PathVariable Long dhrId) {
        return toAjax(dhrRecordService.archive(dhrId));
    }

    /**
     * 导出 DHR 列表（FastExcel）。
     *
     * @param bo       查询条件
     * @param response HTTP 响应
     */
    @SaCheckPermission("mes:dhr:record:export")
    @Log(title = "DHR批记录导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(DhrRecordBo bo, HttpServletResponse response) {
        List<DhrRecordVo> list = dhrRecordService.selectList(bo);
        ExcelUtil.exportExcel(list, "DHR批记录", DhrRecordVo.class, response);
    }
}
