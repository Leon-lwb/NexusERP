package com.nexus.core.wm.service;

import java.util.List;
import java.util.Map;

import com.nexus.core.wm.domain.WmMaterialReceive;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.WfCallBackServiceI;

/**
 * 物料入库单Service接口
 * 
 * @author nexus
 * @date 2025-12-08
 */
public interface IWmMaterialReceiveService extends IService<WmMaterialReceive>, WfCallBackServiceI {
    /**
     * 查询物料入库单
     *
     * @param receiveId 物料入库单主键
     * @return 物料入库单
     */
    public WmMaterialReceive selectWmMaterialReceiveByReceiveId(Long receiveId);

    /**
     * 查询物料入库单列表
     *
     * @param wmMaterialReceive 物料入库单
     * @return 物料入库单集合
     */
    public List<WmMaterialReceive> selectWmMaterialReceiveList(WmMaterialReceive wmMaterialReceive);

    /**
     * 新增物料入库单
     *
     * @param wmMaterialReceive 物料入库单
     * @return 结果
     */
    public int insertWmMaterialReceive(WmMaterialReceive wmMaterialReceive);

    /**
     * 修改物料入库单
     *
     * @param wmMaterialReceive 物料入库单
     * @return 结果
     */
    public int updateWmMaterialReceive(WmMaterialReceive wmMaterialReceive);

    /**
     * 批量删除物料入库单
     *
     * @param receiveIds 需要删除的物料入库单主键集合
     * @return 结果
     */
    public int deleteWmMaterialReceiveByReceiveIds(Long[] receiveIds);

    /**
     * 删除物料入库单信息
     *
     * @param receiveId 物料入库单主键
     * @return 结果
     */
    public int deleteWmMaterialReceiveByReceiveId(Long receiveId);
    
    /**
     * 更新物料入库单并执行必要校验
     * 
     * @param wmMaterialReceive 物料入库单对象
     * @return 结果
     */
    public boolean updateWithValidation(WmMaterialReceive wmMaterialReceive);
    
    /**
     * 提交物料入库单并启动流程
     * 
     * @param receiveIdStr 物料入库单ID字符串
     * @param selectFlowId 选择的流程模板ID
     * @return 结果
     */
    public boolean submit(String receiveIdStr, String selectFlowId);
}