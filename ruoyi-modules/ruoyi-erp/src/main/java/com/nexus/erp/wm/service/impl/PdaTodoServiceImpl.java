package com.nexus.erp.wm.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoSummaryVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmStockTakingPlanMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import com.nexus.erp.wm.service.IPdaTodoService;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmStockTakingPlanService;
import com.nexus.erp.wm.service.IWmTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PDA 任务中心待办聚合实现
 * <p>
 * 计数口径 = 各流程"可作业"状态集 (与 Pda 作业接口的状态校验一致):
 * 收货 已审核(核销/上架)+已上架(待过账); 发料 已创建+部分领料;
 * 移库 已审核; 盘点 草稿(待生成)+已生成(待盘点)。
 * 可见性按既有权限点过滤 (角色未配置则不展示该流程卡片)。
 */
@DS("erp")
@Service
@RequiredArgsConstructor
public class PdaTodoServiceImpl implements IPdaTodoService {

    /** 发料可作业状态 (与 PdaIssueServiceImpl 校验口径一致) */
    private static final String ISSUE_CREATED = "CREATED";
    private static final String ISSUE_PARTIAL = "PARTIAL";

    private final WmMaterialReceiveMapper receiveMapper;
    private final WmProductionIssueMapper issueMapper;
    private final WmTransferMapper transferMapper;
    private final WmStockTakingPlanMapper stocktakingMapper;

    @Override
    public PdaTodoSummaryVo summary() {
        List<PdaTodoSummaryVo.TodoItem> items = new ArrayList<>(4);

        if (StpUtil.hasPermission("wm:receive:list")) {
            Long count = receiveMapper.selectCount(Wrappers.<WmMaterialReceive>lambdaQuery()
                .in(WmMaterialReceive::getStatus,
                    IWmMaterialReceiveCrudService.STATUS_APPROVED,
                    IWmMaterialReceiveCrudService.STATUS_SHELVED));
            items.add(item("receive", "收货", count, "/pages/wm/receive/index"));
        }
        if (StpUtil.hasPermission("wm:productionissue:list")) {
            Long count = issueMapper.selectCount(Wrappers.<WmProductionIssue>lambdaQuery()
                .in(WmProductionIssue::getStatus, ISSUE_CREATED, ISSUE_PARTIAL));
            items.add(item("issue", "发料", count, "/pages/wm/issue/index"));
        }
        if (StpUtil.hasPermission("wm:transfer:list")) {
            Long count = transferMapper.selectCount(Wrappers.<WmTransfer>lambdaQuery()
                .eq(WmTransfer::getStatus, IWmTransferService.STATUS_APPROVED));
            items.add(item("transfer", "移库", count, "/pages/wm/transfer/index"));
        }
        if (StpUtil.hasPermission("wm:stocktaking:list")) {
            Long count = stocktakingMapper.selectCount(Wrappers.<WmStockTakingPlan>lambdaQuery()
                .in(WmStockTakingPlan::getStatus,
                    IWmStockTakingPlanService.STATUS_DRAFT,
                    IWmStockTakingPlanService.STATUS_GENERATED));
            items.add(item("stocktaking", "盘点", count, "/pages/wm/stocktaking/index"));
        }

        PdaTodoSummaryVo vo = new PdaTodoSummaryVo();
        vo.setItems(items);
        return vo;
    }

    private PdaTodoSummaryVo.TodoItem item(String type, String title, Long count, String path) {
        PdaTodoSummaryVo.TodoItem item = new PdaTodoSummaryVo.TodoItem();
        item.setType(type);
        item.setTitle(title);
        item.setCount(count == null ? 0L : count);
        item.setPath(path);
        return item;
    }
}
