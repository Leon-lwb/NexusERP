package com.nexus.core.wm.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.core.domain.ActStatus;
import com.nexus.common.core.domain.R;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.utils.DateUtils;
import com.nexus.common.utils.StringUtils;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfMyBusinessService;
import com.nexus.workflow.service.IWfProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.core.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.core.wm.domain.WmMaterialReceive;
import com.nexus.core.wm.service.IWmMaterialReceiveService;

/**
 * 物料入库单Service业务层处理
 * 
 * @author nexus
 * @date 2025-12-08
 */
@Service("wmMaterialReceiveService")
public class WmMaterialReceiveServiceImpl extends ServiceImpl<WmMaterialReceiveMapper, WmMaterialReceive> implements IWmMaterialReceiveService {

    @Autowired
    private WmMaterialReceiveMapper wmMaterialReceiveMapper;
    
    @Autowired
    private IWfProcessService wfProcessService;
    
    @Autowired
    private IWfMyBusinessService wfMyBusinessService;

    /**
     * 查询物料入库单
     *
     * @param receiveId 物料入库单主键
     * @return 物料入库单
     */
    @Override
    public WmMaterialReceive selectWmMaterialReceiveByReceiveId(Long receiveId) {
        return wmMaterialReceiveMapper.selectWmMaterialReceiveByReceiveId(receiveId);
    }

    /**
     * 查询物料入库单列表
     *
     * @param wmMaterialReceive 物料入库单
     * @return 物料入库单
     */
    @Override
    public List<WmMaterialReceive> selectWmMaterialReceiveList(WmMaterialReceive wmMaterialReceive) {
        return wmMaterialReceiveMapper.selectWmMaterialReceiveList(wmMaterialReceive);
    }

    /**
     * 新增物料入库单
     *
     * @param wmMaterialReceive 物料入库单
     * @return 结果
     */
    @Transactional
    @Override
    public int insertWmMaterialReceive(WmMaterialReceive wmMaterialReceive) {
        wmMaterialReceive.setCreateTime(DateUtils.getNowDate());
        int rows = wmMaterialReceiveMapper.insertWmMaterialReceive(wmMaterialReceive);
        insertWmMaterialReceiveLine(wmMaterialReceive);
        return rows;
    }

    /**
     * 修改物料入库单
     *
     * @param wmMaterialReceive 物料入库单
     * @return 结果
     */
    @Transactional
    @Override
    public int updateWmMaterialReceive(WmMaterialReceive wmMaterialReceive) {
        wmMaterialReceive.setUpdateTime(DateUtils.getNowDate());
        wmMaterialReceiveMapper.deleteWmMaterialReceiveLineByReceiveId(wmMaterialReceive.getReceiveId());
        insertWmMaterialReceiveLine(wmMaterialReceive);
        return wmMaterialReceiveMapper.updateWmMaterialReceive(wmMaterialReceive);
    }

    /**
     * 批量删除物料入库单
     *
     * @param receiveIds 需要删除的物料入库单主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteWmMaterialReceiveByReceiveIds(Long[] receiveIds) {
        wmMaterialReceiveMapper.deleteWmMaterialReceiveLineByReceiveIds(receiveIds);
        return wmMaterialReceiveMapper.deleteWmMaterialReceiveByReceiveIds(receiveIds);
    }

    /**
     * 删除物料入库单信息
     *
     * @param receiveId 物料入库单主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteWmMaterialReceiveByReceiveId(Long receiveId) {
        wmMaterialReceiveMapper.deleteWmMaterialReceiveLineByReceiveId(receiveId);
        return wmMaterialReceiveMapper.deleteWmMaterialReceiveByReceiveId(receiveId);
    }

    /**
     * 新增物料入库单行信息
     *
     * @param wmMaterialReceive 物料入库单对象
     */
    public void insertWmMaterialReceiveLine(WmMaterialReceive wmMaterialReceive) {
        List<WmMaterialReceiveLine> wmMaterialReceiveLineList = wmMaterialReceive.getWmMaterialReceiveLineList();
        Long receiveId = wmMaterialReceive.getReceiveId();
        if (StringUtils.isNotNull(wmMaterialReceiveLineList)) {
            List<WmMaterialReceiveLine> list = new ArrayList<WmMaterialReceiveLine>();
            for (WmMaterialReceiveLine wmMaterialReceiveLine : wmMaterialReceiveLineList) {
                wmMaterialReceiveLine.setReceiveId(receiveId);
                list.add(wmMaterialReceiveLine);
            }
            if (list.size() > 0) {
                wmMaterialReceiveMapper.batchWmMaterialReceiveLine(list);
            }
        }
    }
    
    /**
     * 更新物料入库单并执行必要校验
     * 
     * @param wmMaterialReceive 物料入库单对象
     * @return 结果
     */
    @Override
    public boolean updateWithValidation(WmMaterialReceive wmMaterialReceive) {
        // 校验编号是否为空
        if (StringUtils.isBlank(wmMaterialReceive.getReceiveCode())) {
            throw new ServiceException("编号不能为空！");
        }
        
        // 检查入库单编号是否已经存在
        LambdaQueryWrapper<WmMaterialReceive> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WmMaterialReceive::getReceiveCode, wmMaterialReceive.getReceiveCode());
        List<WmMaterialReceive> existingReceives = this.list(queryWrapper);
        
        if (!existingReceives.isEmpty()) {
            WmMaterialReceive existingReceive = existingReceives.get(0);
            Long receiveId = wmMaterialReceive.getReceiveId() == null ? -1L : wmMaterialReceive.getReceiveId();
            if (!existingReceive.getReceiveId().equals(receiveId)) {
                throw new ServiceException("编号已存在！");
            }
        }
        
        // 如果审核到待上架状态，则检查是否有对应的入库单行
        if (NexusConstants.ORDER_STATUS_APPROVING.equals(wmMaterialReceive.getStatus())) {
            // 检查是否存在入库单行表
            if (wmMaterialReceive.getWmMaterialReceiveLineList() == null || wmMaterialReceive.getWmMaterialReceiveLineList().isEmpty()) {
                throw new ServiceException("入库单行不能为空！");
            }
        }
        
        // 如果是提交到待入库状态，则检查各个行表是否都已分配了对应的明细行，数量必须一致。
        if (NexusConstants.ORDER_STATUS_APPROVED.equals(wmMaterialReceive.getStatus())) {
            validateDetailsForApproval(wmMaterialReceive);
        }
        
        // 处理子表更新
        wmMaterialReceive.setUpdateTime(DateUtils.getNowDate());
        wmMaterialReceiveMapper.deleteWmMaterialReceiveLineByReceiveId(wmMaterialReceive.getReceiveId());
        insertWmMaterialReceiveLine(wmMaterialReceive);
        
        return this.updateById(wmMaterialReceive);
    }
    
    /**
     * 校验待入库状态下的明细信息
     * 
     * @param wmMaterialReceive 物料入库单对象
     */
    private void validateDetailsForApproval(WmMaterialReceive wmMaterialReceive) {
        boolean isValid = true;
        StringBuilder errorMsg = new StringBuilder();
        
        if (wmMaterialReceive.getWmMaterialReceiveLineList() == null || 
            wmMaterialReceive.getWmMaterialReceiveLineList().isEmpty()) {
            isValid = false;
            errorMsg.append("入库单行不能为空！");
        } else {
            for (WmMaterialReceiveLine line : wmMaterialReceive.getWmMaterialReceiveLineList()) {
                // 检查行表是否已分配对应的明细行
                if (line.getWmMaterialReceiveLineDetailList() == null || 
                    line.getWmMaterialReceiveLineDetailList().isEmpty()) {
                    isValid = false;
                    errorMsg.append("物料[").append(line.getMaterialName())
                           .append("]未分配对应的明细行！");
                } else {
                    // 检查行表数量必须一致
                    for (WmMaterialReceiveDetail detail : line.getWmMaterialReceiveLineDetailList()) {
                        if (detail.getQuantity().compareTo(line.getQuantityReceived()) != 0) {
                            isValid = false;
                            errorMsg.append("物料[").append(line.getMaterialName())
                                   .append("]明细数量与入库数量不一致！");
                        }
                    }
                }
            }
        }
        
        if (!isValid) {
            throw new ServiceException(errorMsg.toString());
        }
    }
    
    /**
     * 提交物料入库单并启动流程
     * 
     * @param receiveIdStr 物料入库单ID字符串
     * @param selectFlowId 选择的流程模板ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String receiveIdStr, String selectFlowId) {
        Long receiveId = Long.parseLong(receiveIdStr);
        WmMaterialReceive receive = this.getById(receiveId);
        if (receive == null) {
            throw new ServiceException("入库单不存在");
        }

        if (selectFlowId == null || selectFlowId.isBlank()) {
            if (receive.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(receive.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        receive.setStatus(NexusConstants.ORDER_STATUS_APPROVING);
        this.updateById(receive);

        Map<String, Object> vars = new HashMap<>();
        vars.put("dataId", receiveIdStr);
        vars.put("requireTaskOnStart", true);
        vars.put("receiveId", receive.getReceiveId());
        vars.put("receiveCode", receive.getReceiveCode());
        vars.put("receiveName", receive.getReceiveName());
        vars.put("vendorId", receive.getVendorId());
        vars.put("vendorName", receive.getVendorName());
        vars.put("receiveDate", receive.getReceiveDate());

        R<Void> result = wfProcessService.startProcessByDataId(receiveIdStr, selectFlowId, "wmMaterialReceiveService", vars);
        if (R.isSuccess(result)) {
            return true;
        }

        throw new ServiceException(result.getMsg());
    }

    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        if (business == null) return;

        String dataId = business.getDataId();
        String actStatus = business.getActStatus();
        if (dataId == null || dataId.isEmpty()) return;

        Long receiveId;
        try {
            receiveId = Long.parseLong(dataId);
        } catch (Exception ignore) {
            return;
        }

        WmMaterialReceive receive = this.getById(receiveId);
        if (receive == null) return;

        if (ActStatus.doing.equals(actStatus)) {
            receive.setStatus(NexusConstants.ORDER_STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            receive.setStatus(NexusConstants.ORDER_STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            receive.setStatus("REJECTED");
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            receive.setStatus(NexusConstants.ORDER_STATUS_PREPARE);
        }
        
        this.updateById(receive);
    }

    @Override
    public Object getBusinessDataById(String dataId) {
        if (dataId == null || dataId.isEmpty()) return null;
        try {
            Long receiveId = Long.parseLong(dataId);
            return this.selectWmMaterialReceiveByReceiveId(receiveId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return new HashMap<>();
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return new ArrayList<>();
    }
}