package com.nexus.core.wm.service.impl;

import java.util.List;
import java.util.ArrayList;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.utils.DateUtils;
import com.nexus.common.utils.StringUtils;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
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
@Service
public class WmMaterialReceiveServiceImpl extends ServiceImpl<WmMaterialReceiveMapper, WmMaterialReceive> implements IWmMaterialReceiveService {

    @Autowired
    private WmMaterialReceiveMapper wmMaterialReceiveMapper;

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
}