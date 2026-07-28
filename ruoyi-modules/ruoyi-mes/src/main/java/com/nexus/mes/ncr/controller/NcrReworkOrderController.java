package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.ncr.domain.bo.NcrReworkOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrReworkOrderVo;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES NCR返工单 Controller
 * <p>属于 MES NCR 不合格品管理模块，提供 NCR 返工单关联 的查询接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/ncr/rework")
public class NcrReworkOrderController extends BaseController {

    private final INcrReworkOrderService ncrReworkOrderService;

    /**
     * 分页查询返工单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 返工单分页数据
     */
    @SaCheckPermission("mes:ncr:rework:list")
    @GetMapping("/list")
    public TableDataInfo<NcrReworkOrderVo> list(NcrReworkOrderBo bo, PageQuery pageQuery) {
        return ncrReworkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取返工单详情
     *
     * @param reworkId 返工记录ID
     * @return 返工单详情
     */
    @SaCheckPermission("mes:ncr:rework:query")
    @GetMapping("/{reworkId}")
    public R<NcrReworkOrderVo> getInfo(@PathVariable Long reworkId) {
        return R.ok(ncrReworkOrderService.selectById(reworkId));
    }
}