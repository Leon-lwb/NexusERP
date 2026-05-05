package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.core.domain.ActStatus;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMaterialReceive;
import org.dromara.erp.wm.domain.WmMaterialReceiveDetail;
import org.dromara.erp.wm.domain.WmMaterialReceiveLine;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveBo;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveVo;
import org.dromara.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import org.dromara.erp.wm.mapper.WmMaterialReceiveLineMapper;
import org.dromara.erp.wm.mapper.WmMaterialReceiveMapper;
import org.dromara.erp.wm.service.IWmMaterialReceiveService;
import org.dromara.workflow.domain.WfMyBusiness;
import org.dromara.workflow.service.IWfProcessService;
import org.dromara.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料入库单Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service("wmMaterialReceiveService")
public class WmMaterialReceiveServiceImpl implements IWmMaterialReceiveService {

    private final WmMaterialReceiveMapper baseMapper;
    private final WmMaterialReceiveLineMapper receiveLineMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialReceiveVo selectById(Long id) {
        WmMaterialReceiveVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            // Load lines
            List<WmMaterialReceiveLine> lineList = receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>()
                    .eq(WmMaterialReceiveLine::getReceiveId, id));
            List<WmMaterialReceiveLineVo> lineVoList = MapstructUtils.convert(lineList, WmMaterialReceiveLineVo.class);

            // Load details for each line
            for (WmMaterialReceiveLineVo lineVo : lineVoList) {
                List<WmMaterialReceiveDetail> detailList = receiveDetailMapper.selectList(
                    new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                        .eq(WmMaterialReceiveDetail::getLineId, lineVo.getLineId()));
                lineVo.setWmMaterialReceiveLineDetailList(
                    MapstructUtils.convert(detailList, WmMaterialReceiveDetailVo.class));
            }

            vo.setWmMaterialReceiveLineList(lineVoList);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        int rows = baseMapper.insert(entity);
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        // Delete existing lines and details
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        // Re-insert
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long receiveId : ids) {
            deleteLinesAndDetailsByReceiveId(receiveId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWithValidation(WmMaterialReceiveBo bo) {
        // Validate receiveCode is not blank
        if (StringUtils.isBlank(bo.getReceiveCode())) {
            throw new RuntimeException("编号不能为空！");
        }

        // Check receiveCode uniqueness
        LambdaQueryWrapper<WmMaterialReceive> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WmMaterialReceive::getReceiveCode, bo.getReceiveCode());
        List<WmMaterialReceive> existingReceives = baseMapper.selectList(queryWrapper);

        if (!existingReceives.isEmpty()) {
            WmMaterialReceive existingReceive = existingReceives.get(0);
            Long receiveId = bo.getReceiveId() == null ? -1L : bo.getReceiveId();
            if (!existingReceive.getReceiveId().equals(receiveId)) {
                throw new RuntimeException("编号已存在！");
            }
        }

        // If status is APPROVING, check lines are not empty
        if ("APPROVING".equals(bo.getStatus())) {
            if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
                throw new RuntimeException("入库单行不能为空！");
            }
        }

        // If status is APPROVED, validate details for each line
        if ("APPROVED".equals(bo.getStatus())) {
            validateDetailsForApproval(bo);
        }

        // Update entity
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String receiveIdStr, String selectFlowId) {
        Long receiveId = Long.parseLong(receiveIdStr);
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            throw new RuntimeException("入库单不存在");
        }

        // 如果前端没传 selectFlowId，从数据库已保存记录中读取
        if (StringUtils.isBlank(selectFlowId)) {
            if (receive.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(receive.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        // 保存 selectFlowId 并更新状态
        receive.setSelectFlowId(Long.parseLong(selectFlowId));
        receive.setStatus("APPROVING");
        baseMapper.updateById(receive);

        // 发起 Flowable 流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", receiveIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("receiveId", receive.getReceiveId());
        variables.put("receiveCode", receive.getReceiveCode());
        variables.put("receiveName", receive.getReceiveName());
        variables.put("vendorId", receive.getVendorId());
        variables.put("vendorName", receive.getVendorName());
        variables.put("receiveDate", receive.getReceiveDate());
        processService.startProcessByDataId(receiveIdStr, selectFlowId, "wmMaterialReceiveService", variables);

        return true;
    }

    @Override
    public String getProcInsId(String receiveIdStr) {
        // 第一级：查 wf_my_business 表
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(receiveIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        // 第二级：查运行中的流程实例
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        // 第三级：查历史流程实例
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        Long receiveId = Long.parseLong(business.getDataId());
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            return;
        }
        String actStatus = business.getActStatus();
        // 根据流程状态同步到单据状态
        if (ActStatus.doing.equals(actStatus)) {
            receive.setStatus("APPROVING");
        } else if (ActStatus.pass.equals(actStatus)) {
            receive.setStatus("APPROVED");
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            receive.setStatus("REJECTED");
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            receive.setStatus("PREPARE");
        }
        baseMapper.updateById(receive);
    }

    @Override
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * Validate details for approval status
     */
    private void validateDetailsForApproval(WmMaterialReceiveBo bo) {
        StringBuilder errorMsg = new StringBuilder();
        boolean isValid = true;

        if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
            throw new RuntimeException("入库单行不能为空！");
        }

        for (WmMaterialReceiveLineBo line : bo.getWmMaterialReceiveLineList()) {
            if (line.getWmMaterialReceiveLineDetailList() == null
                || line.getWmMaterialReceiveLineDetailList().isEmpty()) {
                isValid = false;
                errorMsg.append("物料[").append(line.getMaterialName())
                    .append("]未分配对应的明细行！");
            } else {
                for (WmMaterialReceiveDetailBo detail : line.getWmMaterialReceiveLineDetailList()) {
                    if (detail.getQuantity().compareTo(line.getQuantityReceived()) != 0) {
                        isValid = false;
                        errorMsg.append("物料[").append(line.getMaterialName())
                            .append("]明细数量与入库数量不一致！");
                    }
                }
            }
        }

        if (!isValid) {
            throw new RuntimeException(errorMsg.toString());
        }
    }

    /**
     * Insert lines and their details
     */
    private void insertLinesAndDetails(Long receiveId, List<WmMaterialReceiveLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmMaterialReceiveLine> lineList = MapstructUtils.convert(lineBoList, WmMaterialReceiveLine.class);
            for (WmMaterialReceiveLine line : lineList) {
                line.setReceiveId(receiveId);
            }
            receiveLineMapper.insertBatch(lineList);

            // Insert details
            for (int i = 0; i < lineBoList.size(); i++) {
                WmMaterialReceiveLineBo lineBo = lineBoList.get(i);
                if (lineBo.getWmMaterialReceiveLineDetailList() != null
                    && !lineBo.getWmMaterialReceiveLineDetailList().isEmpty()) {
                    List<WmMaterialReceiveDetail> detailList = MapstructUtils.convert(
                        lineBo.getWmMaterialReceiveLineDetailList(), WmMaterialReceiveDetail.class);
                    for (WmMaterialReceiveDetail detail : detailList) {
                        detail.setReceiveId(receiveId);
                        detail.setLineId(lineList.get(i).getLineId());
                    }
                    receiveDetailMapper.insertBatch(detailList);
                }
            }
        }
    }

    /**
     * Delete lines and details by receive ID
     */
    private void deleteLinesAndDetailsByReceiveId(Long receiveId) {
        // Delete details for all lines under this receive
        List<WmMaterialReceiveLine> lines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        for (WmMaterialReceiveLine line : lines) {
            receiveDetailMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                .eq(WmMaterialReceiveDetail::getLineId, line.getLineId()));
        }
        // Delete lines
        receiveLineMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveLine>()
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
    }

    private LambdaQueryWrapper<WmMaterialReceive> buildQueryWrapper(WmMaterialReceiveBo bo) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReceiveCode()), WmMaterialReceive::getReceiveCode, bo.getReceiveCode());
        lqw.like(StringUtils.isNotBlank(bo.getReceiveName()), WmMaterialReceive::getReceiveName, bo.getReceiveName());
        lqw.eq(bo.getIqcId() != null, WmMaterialReceive::getIqcId, bo.getIqcId());
        lqw.eq(StringUtils.isNotBlank(bo.getIqcCode()), WmMaterialReceive::getIqcCode, bo.getIqcCode());
        lqw.eq(bo.getNoticeId() != null, WmMaterialReceive::getNoticeId, bo.getNoticeId());
        lqw.eq(StringUtils.isNotBlank(bo.getNoticeCode()), WmMaterialReceive::getNoticeCode, bo.getNoticeCode());
        lqw.eq(bo.getPurId() != null, WmMaterialReceive::getPurId, bo.getPurId());
        lqw.eq(StringUtils.isNotBlank(bo.getPurCode()), WmMaterialReceive::getPurCode, bo.getPurCode());
        lqw.eq(bo.getVendorId() != null, WmMaterialReceive::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmMaterialReceive::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmMaterialReceive::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmMaterialReceive::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialReceive::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialReceive::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialReceive::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialReceive::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialReceive::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialReceive::getAreaName, bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialReceive::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialReceive::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialReceive::getLocationName, bo.getLocationName());
        lqw.eq(bo.getReceiveDate() != null, WmMaterialReceive::getReceiveDate, bo.getReceiveDate());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMaterialReceive::getStatus, bo.getStatus());
        return lqw;
    }
}
