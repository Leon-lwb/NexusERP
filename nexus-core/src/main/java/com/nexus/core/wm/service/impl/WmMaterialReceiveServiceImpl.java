package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
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
}