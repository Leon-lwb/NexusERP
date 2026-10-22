package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReturn;
import com.nexus.erp.wm.domain.WmMaterialReturnLine;
import com.nexus.erp.wm.domain.bo.WmMaterialReturnBo;
import com.nexus.erp.wm.mapper.WmMaterialReturnLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReturnMapper;
import com.nexus.erp.wm.service.impl.WmServiceHelper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产退料单 (PC 端建单与查询; PDA 核销见 /app/pda/wm/return)
 * <p>
 * 退料三类: 良品/不良品/报废。plus-ui 建单页接入前, 可经本接口/接口工具造单供试点。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/return")
@DS("erp")
public class WmMaterialReturnController {

    private final WmMaterialReturnMapper returnMapper;
    private final WmMaterialReturnLineMapper lineMapper;
    private final WmServiceHelper wm;

    /** 退料单列表 */
    @SaCheckPermission("wm:materialReturn:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReturn> list(
        @RequestParam(required = false) String returnNo,
        @RequestParam(required = false) String returnType,
        @RequestParam(required = false) String status,
        PageQuery pageQuery) {
        Page<WmMaterialReturn> page = returnMapper.selectPage(pageQuery.build(),
            Wrappers.<WmMaterialReturn>lambdaQuery()
                .like(StringUtils.isNotBlank(returnNo), WmMaterialReturn::getReturnNo, returnNo)
                .eq(StringUtils.isNotBlank(returnType), WmMaterialReturn::getReturnType, returnType)
                .eq(StringUtils.isNotBlank(status), WmMaterialReturn::getStatus, status)
                .orderByDesc(WmMaterialReturn::getCreateTime));
        return new TableDataInfo<>(page.getRecords(), page.getTotal());
    }

    /** 退料单详情 (头+行, PDA 核销进度在行 actualQty 上) */
    @SaCheckPermission("wm:materialReturn:query")
    @GetMapping("/{returnId}")
    public R<WmMaterialReturn> detail(@PathVariable Long returnId) {
        WmMaterialReturn head = returnMapper.selectById(returnId);
        if (head == null) {
            return R.fail("退料单不存在");
        }
        head.setLines(lineMapper.selectList(Wrappers.<WmMaterialReturnLine>lambdaQuery()
            .eq(WmMaterialReturnLine::getReturnId, returnId)));
        return R.ok(head);
    }

    /** 新建退料单 (状态 DRAFT, 待 PDA 核销) */
    @SaCheckPermission("wm:materialReturn:add")
    @Log(title = "生产退料单", businessType = BusinessType.INSERT)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@Validated @RequestBody @NotNull WmMaterialReturnBo bo) {
        if (!WmMaterialReturn.TYPE_GOOD.equals(bo.getReturnType())
            && !WmMaterialReturn.TYPE_DEFECT.equals(bo.getReturnType())
            && !WmMaterialReturn.TYPE_SCRAP.equals(bo.getReturnType())) {
            return R.fail("退料类型不合法");
        }
        WmMaterialReturn head = new WmMaterialReturn();
        head.setReturnNo(wm.autoCodeUtil.genSerialCode("WM_RETURN_CODE", null));
        head.setWarehouseId(bo.getWarehouseId());
        head.setWarehouseCode(bo.getWarehouseCode());
        head.setWarehouseName(bo.getWarehouseName());
        head.setReturnType(bo.getReturnType());
        head.setSourceDoc(bo.getSourceDoc());
        head.setStatus(WmMaterialReturn.STATUS_DRAFT);
        head.setReturnDate(bo.getReturnDate());
        head.setRemark(bo.getRemark());
        returnMapper.insert(head);

        List<WmMaterialReturnLine> lines = new ArrayList<>(bo.getLines());
        for (WmMaterialReturnLine line : lines) {
            // 租户和审计身份由服务端上下文注入，不能沿用客户端实体字段。
            line.setTenantId(null);
            line.setCreateBy(null);
            line.setCreateDept(null);
            line.setCreateTime(null);
            line.setUpdateBy(null);
            line.setUpdateTime(null);
            line.setLineId(null);
            line.setReturnId(head.getReturnId());
            line.setActualQty(null);
            lineMapper.insert(line);
        }
        return R.ok();
    }
}
