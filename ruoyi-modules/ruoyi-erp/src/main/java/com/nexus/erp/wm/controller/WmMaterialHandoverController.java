package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialHandover;
import com.nexus.erp.wm.domain.WmMaterialHandoverLine;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.bo.WmMaterialHandoverBo;
import com.nexus.erp.wm.mapper.WmMaterialHandoverLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialHandoverMapper;
import com.nexus.erp.wm.mapper.WmStorageLocationMapper;
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
import java.util.Date;
import java.util.List;

/**
 * 上料交接单 (PC 端建单与查询; PDA 核销/签收见 /app/pda/wm/handover)
 * <p>
 * plus-ui 建单页接入前, 可经本接口/接口工具造单供试点。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/handover")
@DS("erp")
public class WmMaterialHandoverController {

    private final WmMaterialHandoverMapper handoverMapper;
    private final WmMaterialHandoverLineMapper lineMapper;
    private final WmStorageLocationMapper locationMapper;
    private final WmServiceHelper wm;

    /** 交接单列表 */
    @SaCheckPermission("wm:materialHandover:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialHandover> list(
        @RequestParam(required = false) String handoverNo,
        @RequestParam(required = false) String woNo,
        @RequestParam(required = false) String status,
        PageQuery pageQuery) {
        Page<WmMaterialHandover> page = handoverMapper.selectPage(pageQuery.build(),
            Wrappers.<WmMaterialHandover>lambdaQuery()
                .like(StringUtils.isNotBlank(handoverNo), WmMaterialHandover::getHandoverNo, handoverNo)
                .like(StringUtils.isNotBlank(woNo), WmMaterialHandover::getWoNo, woNo)
                .eq(StringUtils.isNotBlank(status), WmMaterialHandover::getStatus, status)
                .orderByDesc(WmMaterialHandover::getCreateTime));
        return new TableDataInfo<>(page.getRecords(), page.getTotal());
    }

    /** 交接单详情 (头+行, PDA 核销进度在行 actualQty 上) */
    @SaCheckPermission("wm:materialHandover:query")
    @GetMapping("/{handoverId}")
    public R<WmMaterialHandover> detail(@PathVariable Long handoverId) {
        WmMaterialHandover head = handoverMapper.selectById(handoverId);
        if (head == null) {
            return R.fail("交接单不存在");
        }
        head.setLines(lineMapper.selectList(Wrappers.<WmMaterialHandoverLine>lambdaQuery()
            .eq(WmMaterialHandoverLine::getHandoverId, handoverId)));
        return R.ok(head);
    }

    /** 新建交接单 (状态 DRAFT, 待 PDA 核销+签收) */
    @SaCheckPermission("wm:materialHandover:add")
    @Log(title = "上料交接单", businessType = BusinessType.INSERT)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@Validated @RequestBody @NotNull WmMaterialHandoverBo bo) {
        WmMaterialHandover head = new WmMaterialHandover();
        head.setHandoverNo(wm.autoCodeUtil.genSerialCode("WM_HANDOVER_CODE", null));
        head.setWoId(bo.getWoId());
        head.setWoNo(bo.getWoNo());
        head.setWarehouseId(bo.getWarehouseId());
        head.setWarehouseCode(bo.getWarehouseCode());
        head.setWarehouseName(bo.getWarehouseName());
        head.setStatus(WmMaterialHandover.STATUS_DRAFT);
        head.setHandoverTime(new Date());
        head.setRemark(bo.getRemark());
        handoverMapper.insert(head);

        List<WmMaterialHandoverLine> lines = new ArrayList<>(bo.getLines());
        for (WmMaterialHandoverLine line : lines) {
            // 线边库位必填且必须真实存在 (过账时按其归属仓库入库)
            if (StringUtils.isBlank(line.getToLocationCode()) && line.getToLocationId() == null) {
                throw new ServiceException("行[" + line.getMaterialCode() + "]线边库位不能为空");
            }
            WmStorageLocation toLoc = line.getToLocationId() != null
                ? locationMapper.selectById(line.getToLocationId())
                : locationMapper.selectOne(Wrappers.<WmStorageLocation>lambdaQuery()
                    .eq(WmStorageLocation::getLocationCode, line.getToLocationCode()));
            if (toLoc == null) {
                throw new ServiceException("线边库位不存在: " + line.getToLocationCode());
            }
            // 租户和审计身份由服务端上下文注入，不能沿用客户端实体字段。
            line.setTenantId(null);
            line.setCreateBy(null);
            line.setCreateDept(null);
            line.setCreateTime(null);
            line.setUpdateBy(null);
            line.setUpdateTime(null);
            line.setLineId(null);
            line.setHandoverId(head.getHandoverId());
            line.setToLocationId(toLoc.getLocationId());
            line.setToLocationCode(toLoc.getLocationCode());
            line.setToLocationName(toLoc.getLocationName());
            line.setActualQty(null);
            lineMapper.insert(line);
        }
        return R.ok();
    }
}
