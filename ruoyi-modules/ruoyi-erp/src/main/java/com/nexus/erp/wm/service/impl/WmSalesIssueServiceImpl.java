package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.OqcAutoCreateMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.domain.bo.WmSalesIssueLineBo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueVo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueLineVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
import com.nexus.erp.wm.service.IWmSalesIssueService;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 销售出库Service业务层处理
 *
 * @author nexus
 * @date 2026-04-03
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmSalesIssueService")
public class WmSalesIssueServiceImpl implements IWmSalesIssueService {

    /** 基础数据访问映射器（Mapper） */
    private final WmSalesIssueMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmSalesIssueLineMapper lineMapper;
    /** 物料库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper wmMaterialStockMapper;
    /** 库存流水数据访问映射器（Mapper） */
    private final WmMaterialStockTxMapper stockTxMapper;
    /** 销售发料业务服务，提供销售发料相关能力 */
    private final IWfProcessService processService;
    /** 销售发料业务服务，提供销售发料相关能力 */
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    /** 销售发料业务服务，提供销售发料相关能力 */
    private final RuntimeService runtimeService;
    /** 销售发料业务服务，提供销售发料相关能力 */
    private final HistoryService historyService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;
    /** 销售发料业务服务，提供销售发料相关能力 */
    private final ISoFulfillmentWritebackService fulfillmentWritebackService;
    /** 可靠消息生产者：销售出库审批通过后发 OQC 自动创建消息 */
    private final ReliableMessageProducer messageProducer;
    /** 物料主数据 Mapper：查 inspection_required 门控 OQC 自动创建 */
    private final BdMaterialMapper bdMaterialMapper;

    /**
     * 分页查询销售发料列表
     *
     * @param bo 销售发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmSalesIssue> lqw = buildQueryWrapper(bo);
        Page<WmSalesIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售发料列表
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmSalesIssueVo> selectList(WmSalesIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询销售发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmSalesIssueVo selectById(Long id) {
        WmSalesIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<WmSalesIssueLine> lineList = lineMapper.selectList(
                new LambdaQueryWrapper<WmSalesIssueLine>()
                    .eq(WmSalesIssueLine::getIssueId, id));
            vo.setWmSalesIssueLineList(MapstructUtils.convert(lineList, WmSalesIssueLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售发料
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(autoCodeUtil.genSerialCode("WM_SALES_ISSUE_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return rows;
    }

    /**
     * 修改销售发料
     *
     * @param bo 销售发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSalesIssueBo bo) {
        WmSalesIssue entity = MapstructUtils.convert(bo, WmSalesIssue.class);
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
        insertLines(entity.getIssueId(), bo.getWmSalesIssueLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除销售发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        lineMapper.delete(new LambdaQueryWrapper<WmSalesIssueLine>()
            .in(WmSalesIssueLine::getIssueId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 销售发料处理（confirmSalesIssue）
     *
     * @param issueId 发料单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(salesIssue.getStatus()) && !STATUS_APPROVED.equals(salesIssue.getStatus())) {
            throw new RuntimeException("只有草稿或已批准的出库单才能确认");
        }
        salesIssue.setStatus(STATUS_CONFIRMED);
        return baseMapper.updateById(salesIssue);
    }

    /**
     * 销售发料处理（completeSalesIssue）
     *
     * @param issueId 发料单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int completeSalesIssue(Long issueId) {
        WmSalesIssue salesIssue = baseMapper.selectById(issueId);
        if (salesIssue == null) {
            throw new ServiceException("销售出库单不存在");
        }
        if (!STATUS_CONFIRMED.equals(salesIssue.getStatus())) {
            throw new ServiceException("只有已确认的出库单才能完成");
        }

        // 幂等检查：已生成过出库事务记录则跳过
        Long txCount = stockTxMapper.selectCount(Wrappers.<WmMaterialStockTx>lambdaQuery()
            .eq(WmMaterialStockTx::getSourceDocType, "WM_SALES_ISSUE")
            .eq(WmMaterialStockTx::getSourceDocId, issueId));
        if (txCount != null && txCount > 0) {
            log.warn("销售出库单[{}]已执行过，跳过重复处理", issueId);
            return 1;
        }

        List<WmSalesIssueLine> lineList = lineMapper.selectList(
            new LambdaQueryWrapper<WmSalesIssueLine>()
                .eq(WmSalesIssueLine::getIssueId, issueId));
        if (lineList == null || lineList.isEmpty()) {
            throw new ServiceException("出库单明细为空");
        }

        Date now = new Date();

        // 扣减库存：按批次效期、收货时间、库存ID排序，跨库存记录 FIFO 扣减
        for (WmSalesIssueLine line : lineList) {
            BigDecimal issueQty = line.getQuantityIssue() != null ? line.getQuantityIssue() : BigDecimal.ZERO;
            if (issueQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LambdaQueryWrapper<WmMaterialStock> stockLqw = Wrappers.lambdaQuery();
            stockLqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
            stockLqw.eq(WmMaterialStock::getWarehouseId, salesIssue.getWarehouseId());
            stockLqw.eq(line.getLocationId() != null, WmMaterialStock::getLocationId, line.getLocationId());
            stockLqw.eq(line.getBatchId() != null, WmMaterialStock::getBatchId, line.getBatchId());
            stockLqw.eq(WmMaterialStock::getFrozenFlag, 0L);
            stockLqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
            stockLqw.orderByAsc(WmMaterialStock::getExpireDate);
            stockLqw.orderByAsc(WmMaterialStock::getRecptDate);
            stockLqw.orderByAsc(WmMaterialStock::getStockId);

            List<WmMaterialStock> stockList = wmMaterialStockMapper.selectList(stockLqw);
            if (stockList == null || stockList.isEmpty()) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】库存不存在");
            }

            BigDecimal remaining = issueQty;
            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (WmMaterialStock stock : stockList) {
                totalAvailable = totalAvailable.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO);
            }
            if (totalAvailable.compareTo(issueQty) < 0) {
                throw new ServiceException("物料【" + line.getMaterialName() + "】库存不足，当前库存："
                    + totalAvailable + "，出库数量：" + issueQty);
            }

            for (WmMaterialStock stock : stockList) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal currentQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;
                if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal consumeQty = currentQty.min(remaining);
                // 原子扣减库存（乐观锁 + 不允许负库存）
                int affected = wmMaterialStockMapper.deductStock(stock.getStockId(), consumeQty, stock.getVersion());
                if (affected == 0) {
                    throw new ServiceException("库存并发冲突，请重试: 物料【" + line.getMaterialName() + "】");
                }

                // 生成库存事务记录
                WmMaterialStockTx tx = new WmMaterialStockTx();
                tx.setTxType("SALES_OUT");
                tx.setMaterialId(stock.getMaterialId());
                tx.setMaterialCode(stock.getMaterialCode());
                tx.setMaterialName(stock.getMaterialName());
                tx.setMaterialSpec(stock.getMaterialSpec());
                tx.setBaseUomId(stock.getBaseUomId());
                tx.setBaseUomName(stock.getBaseUomName());
                tx.setBatchId(stock.getBatchId());
                tx.setBatchCode(stock.getBatchCode());
                tx.setWarehouseId(stock.getWarehouseId());
                tx.setWarehouseCode(stock.getWarehouseCode());
                tx.setWarehouseName(stock.getWarehouseName());
                tx.setAreaId(stock.getAreaId());
                tx.setAreaCode(stock.getAreaCode());
                tx.setAreaName(stock.getAreaName());
                tx.setLocationId(stock.getLocationId());
                tx.setLocationCode(stock.getLocationCode());
                tx.setLocationName(stock.getLocationName());
                tx.setMaterialStockId(stock.getStockId());
                tx.setTxFlag(-1);
                tx.setTxQuantity(consumeQty);
                tx.setBeforeQuantity(currentQty);
                tx.setAfterQuantity(currentQty.subtract(consumeQty));
                tx.setTxDate(now);
                tx.setSourceDocType("WM_SALES_ISSUE");
                tx.setSourceDocId(issueId);
                tx.setSourceDocCode(salesIssue.getIssueCode());
                tx.setSourceDocLineId(line.getLineId());
                tx.setRemark("销售出库扣减库存，单号：" + salesIssue.getIssueCode());
                stockTxMapper.insert(tx);

                remaining = remaining.subtract(consumeQty);
            }

            // 回写 SO 行已发货/已履行数量（销售出库即对该行的履行交付）
            if (line.getSourceDocLineId() != null && issueQty.compareTo(BigDecimal.ZERO) > 0) {
                fulfillmentWritebackService.addShippedQty(line.getSourceDocLineId(), issueQty);
            }
        }

        salesIssue.setStatus(STATUS_COMPLETED);
        return baseMapper.updateById(salesIssue);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 销售发料处理（submit）
     *
     * @param issueIdStr 参数 issueIdStr
     * @param selectFlowId selectFlow ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String issueIdStr, String selectFlowId) {
        Long issueId = Long.parseLong(issueIdStr);
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) {
            throw new RuntimeException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的出库单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", issueIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("issueId", entity.getIssueId());
        variables.put("issueCode", entity.getIssueCode());
        variables.put("clientName", entity.getClientName());
        R<Void> result = processService.startProcessByDataId(issueIdStr, selectFlowId, "wmSalesIssueService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取销售发料
     *
     * @param issueIdStr 参数 issueIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String issueIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(issueIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(issueIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(issueIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    /**
     * 销售发料处理（afterFlowHandle）
     *
     * @param business 参数 business
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long issueId = Long.parseLong(business.getDataId());
        WmSalesIssue entity = baseMapper.selectById(issueId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVED);
            // 审批通过后，事务提交后按需检物料发 OqcAutoCreateMessage 给 MES 自动创建 OQC
            registerOqcCreateMessages(entity);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(entity);
    }

    /**
     * 注册 afterCommit 回调，事务提交后按"需检物料"逐条发 OqcAutoCreateMessage 给 MES 自动创建 OQC。
     * <p>
     * 触发点：销售出库审批通过(afterFlowHandle pass)。
     * 门控：出库行物料 inspection_required=1 才发；同 soId+materialId 去重(消费端亦幂等)。
     * soId 取自销售出库主表；物料/数量取自 wm_sales_issue_line。显式传 tenantId 避免消费端退化。
     *
     * @param entity 销售出库单
     */
    private void registerOqcCreateMessages(WmSalesIssue entity) {
        try {
            if (entity.getSoId() == null) {
                log.warn("销售出库 {} 无关联销售订单，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }
            List<WmSalesIssueLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<WmSalesIssueLine>()
                    .eq(WmSalesIssueLine::getIssueId, entity.getIssueId()));
            if (lines == null || lines.isEmpty()) {
                log.warn("销售出库 {} 无明细行，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }
            // 批量查物料 inspection_required 门控
            Set<Long> materialIds = new HashSet<>();
            for (WmSalesIssueLine l : lines) {
                if (l.getMaterialId() != null) {
                    materialIds.add(l.getMaterialId());
                }
            }
            if (materialIds.isEmpty()) {
                return;
            }
            List<BdMaterial> materials = bdMaterialMapper.selectList(
                new LambdaQueryWrapper<BdMaterial>().in(BdMaterial::getMaterialId, materialIds));
            Set<Long> needOqcMaterialIds = new HashSet<>();
            for (BdMaterial m : materials) {
                if (Integer.valueOf(1).equals(m.getInspectionRequired())) {
                    needOqcMaterialIds.add(m.getMaterialId());
                }
            }
            if (needOqcMaterialIds.isEmpty()) {
                log.info("销售出库 {} 所有出库物料均免检，跳过 OQC 自动创建", entity.getIssueId());
                return;
            }

            // 显式设置 tenantId，避免消费端退化为 000000
            long tenantIdVal = 0L;
            String tenantIdStr = TenantHelper.getTenantId();
            if (tenantIdStr != null) {
                try {
                    tenantIdVal = Long.parseLong(tenantIdStr);
                } catch (NumberFormatException ignore) {
                    tenantIdVal = 0L;
                }
            }
            final long tenantId = tenantIdVal;

            // 逐个需检物料构建消息（同物料去重，一物料一条 -> MES 建一条 OQC）
            List<OqcAutoCreateMessage> messages = new ArrayList<>();
            Set<Long> dispatched = new HashSet<>();
            for (WmSalesIssueLine l : lines) {
                if (l.getMaterialId() == null || !needOqcMaterialIds.contains(l.getMaterialId())) {
                    continue;
                }
                if (!dispatched.add(l.getMaterialId())) {
                    continue;
                }
                OqcAutoCreateMessage msg = new OqcAutoCreateMessage();
                msg.setSoId(entity.getSoId());
                msg.setMaterialId(l.getMaterialId());
                msg.setMaterialCode(l.getMaterialCode());
                msg.setMaterialName(l.getMaterialName());
                msg.setQuantity(l.getQuantityIssue());
                msg.setSourceModule("ERP");
                msg.setTenantId(tenantId);
                messages.add(msg);
            }
            if (messages.isEmpty()) {
                return;
            }
            final List<OqcAutoCreateMessage> toSend = messages;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (OqcAutoCreateMessage msg : toSend) {
                        try {
                            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.MES_OQC_CREATE, msg);
                            log.info("销售出库审批通过 OQC MQ 已发送: soId={}, materialId={}", entity.getSoId(), msg.getMaterialId());
                        } catch (Exception e) {
                            log.error("销售出库审批通过 OQC MQ 发送失败: soId={}, materialId={}", entity.getSoId(), msg.getMaterialId(), e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("注册销售出库 OQC MQ 失败: issueId={}", entity.getIssueId(), e);
        }
    }

    /**
     * 获取销售发料详情
     *
     * @param dataId data ID
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 销售发料处理（flowValuesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 销售发料处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void insertLines(Long issueId, List<WmSalesIssueLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmSalesIssueLine> lineList = MapstructUtils.convert(lineBoList, WmSalesIssueLine.class);
            for (WmSalesIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
            }
            lineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmSalesIssue> buildQueryWrapper(WmSalesIssueBo bo) {
        LambdaQueryWrapper<WmSalesIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmSalesIssue::getIssueCode, bo.getIssueCode());
        lqw.like(StringUtils.isNotBlank(bo.getIssueName()), WmSalesIssue::getIssueName, bo.getIssueName());
        lqw.eq(bo.getSoId() != null, WmSalesIssue::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), WmSalesIssue::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, WmSalesIssue::getClientId, bo.getClientId());
        lqw.eq(StringUtils.isNotBlank(bo.getClientCode()), WmSalesIssue::getClientCode, bo.getClientCode());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), WmSalesIssue::getClientName, bo.getClientName());
        lqw.eq(bo.getWarehouseId() != null, WmSalesIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmSalesIssue::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmSalesIssue::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getIssueDate() != null, WmSalesIssue::getIssueDate, bo.getIssueDate());
        lqw.eq(bo.getTotalQuantity() != null, WmSalesIssue::getTotalQuantity, bo.getTotalQuantity());
        lqw.eq(bo.getTotalAmount() != null, WmSalesIssue::getTotalAmount, bo.getTotalAmount());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSalesIssue::getStatus, bo.getStatus());
        return lqw;
    }

}
