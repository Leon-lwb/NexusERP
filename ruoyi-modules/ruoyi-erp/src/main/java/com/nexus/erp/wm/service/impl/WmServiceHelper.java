package com.nexus.erp.wm.service.impl;

import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * WM 模块 ServiceImpl 公共依赖聚合器。
 * <p>
 * 收敛 wm 包下 ServiceImpl 重复注入的 4 个公共依赖：
 * {@link AutoCodeUtil}、{@link IWfProcessService}、{@link WfMyBusinessServiceImpl}、
 * {@link FlowableProcInsIdHelper}。
 * 各 ServiceImpl 注入此单一 Helper 即可替代 4 行重复字段声明。
 * </p>
 *
 * @author generated
 * @since 2026-08-14
 */
@Component
@RequiredArgsConstructor
public class WmServiceHelper {

    public final AutoCodeUtil autoCodeUtil;
    public final IWfProcessService processService;
    public final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    public final FlowableProcInsIdHelper procInsIdHelper;
}