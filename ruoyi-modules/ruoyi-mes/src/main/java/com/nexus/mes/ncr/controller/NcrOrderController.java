package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.ncr.domain.bo.NcrOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;
import com.nexus.mes.ncr.service.INcrOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES NCR不合格品单 Controller
 * <p>属于 MES NCR 不合格品管理模块，提供 NCR 单的增删改查与评审、处置操作接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/ncr/order")
public class NcrOrderController extends BaseController {

    private final INcrOrderService ncrOrderService;

    /**
     * 分页查询NCR单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return NCR单分页数据
     */
    @SaCheckPermission("mes:ncr:order:list")
    @GetMapping("/list")
    public TableDataInfo<NcrOrderVo> list(NcrOrderBo bo, PageQuery pageQuery) {
        return ncrOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取NCR单详情
     *
     * @param ncrId NCR单ID
     * @return NCR单详情
     */
    @SaCheckPermission("mes:ncr:order:query")
    @GetMapping("/{ncrId}")
    public R<NcrOrderVo> getInfo(@PathVariable Long ncrId) {
        return R.ok(ncrOrderService.selectById(ncrId));
    }

    /**
     * 新增NCR单(手工创建,来源 CUSTOMER)
     *
     * @param bo NCR单业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:add")
    @Log(title = "NCR单新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody @Validated NcrOrderBo bo) {
        return toAjax(ncrOrderService.insertByBo(bo));
    }

    /**
     * 修改NCR单
     *
     * @param bo NCR单业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:edit")
    @Log(title = "NCR单修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody @Validated NcrOrderBo bo) {
        return toAjax(ncrOrderService.updateByBo(bo));
    }

    /**
     * 批量删除NCR单
     *
     * @param ids NCR单ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:remove")
    @Log(title = "NCR单删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(ncrOrderService.deleteByIds(ids));
    }

    /**
     * 评审NCR单(OPEN -> REVIEW)
     *
     * @param ncrId NCR单ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:review")
    @Log(title = "NCR评审", businessType = BusinessType.UPDATE)
    @PutMapping("/review/{ncrId}")
    public R<Void> review(@PathVariable Long ncrId) {
        return toAjax(ncrOrderService.review(ncrId));
    }

    /**
     * 处置NCR单(OPEN/REVIEW -> DISPOSITION)
     *
     * @param ncrId       NCR单ID
     * @param disposition 处置方式(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)
     * @param remark      处置备注(可空)
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:dispose")
    @Log(title = "NCR处置", businessType = BusinessType.UPDATE)
    @PutMapping("/dispose/{ncrId}")
    public R<Void> dispose(@PathVariable Long ncrId,
                           @RequestParam String disposition,
                           @RequestParam(required = false) String remark) {
        return toAjax(ncrOrderService.dispose(ncrId, disposition, remark));
    }

    /**
     * 签署并处置NCR单(OPEN/REVIEW -> DISPOSITION, 并按处置方式分支流转)
     * <p>与 {@link #dispose(Long, String, String)} 的区别：本接口在同一请求内先落电子签名
     * (biz_type=NCR_DISPOSITION), 再执行处置, 二者由服务层一个事务方法收口, 不会出现
     * 「签了没处置」或「处置了没绑定签名」；{@code authMethod=LOGIN_ONLY} 会被拒绝
     * (处置是授权, 必须二级认证)。未签署的 dispose 接口保持可用(P1 决策: 签署尚未强制)。</p>
     *
     * @param ncrId       NCR单ID
     * @param disposition 处置方式(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)
     * @param remark      处置备注(可空)
     * @param authMethod  认证方式(LOGIN_ONLY 将被拒绝)
     * @param credential  认证凭证
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:order:dispose")
    @Log(title = "NCR处置签署", businessType = BusinessType.UPDATE)
    @PutMapping("/dispose/{ncrId}/sign")
    public R<Void> disposeWithSignature(@PathVariable Long ncrId,
                                        @RequestParam String disposition,
                                        @RequestParam(required = false) String remark,
                                        @RequestParam String authMethod,
                                        @RequestParam String credential) {
        return toAjax(ncrOrderService.disposeWithSignature(ncrId, disposition, remark, authMethod, credential));
    }
}