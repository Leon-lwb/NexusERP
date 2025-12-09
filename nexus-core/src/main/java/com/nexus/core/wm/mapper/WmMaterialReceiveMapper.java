package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmMaterialReceive;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;

/**
 * 物料入库单Mapper接口
 *
 * @author nexus
 * @date 2025-12-08
 */
public interface WmMaterialReceiveMapper extends BaseMapper<WmMaterialReceive> {
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
     * 删除物料入库单
     *
     * @param receiveId 物料入库单主键
     * @return 结果
     */
    public int deleteWmMaterialReceiveByReceiveId(Long receiveId);

    /**
     * 批量删除物料入库单
     *
     * @param receiveIds 需要删除的数据主键数组
     * @return 结果
     */
    public int deleteWmMaterialReceiveByReceiveIds(Long[] receiveIds);

    /**
     * 批量删除物料入库单行
     * 
     * @param receiveIds 需要删除的数据主键数组
     * @return 结果
     */
    public int deleteWmMaterialReceiveLineByReceiveIds(Long[] receiveIds);
    
    /**
     * 批量新增物料入库单行信息
     * 
     * @param wmMaterialReceiveLineList 物料入库单行列表
     * @return 结果
     */
    public int batchWmMaterialReceiveLine(List<WmMaterialReceiveLine> wmMaterialReceiveLineList);

    /**
     * 删除物料入库单行信息
     * 
     * @param receiveId 物料入库单ID
     * @return 结果
     */
    public int deleteWmMaterialReceiveLineByReceiveId(Long receiveId);
    
    /**
     * 查询物料入库单行列表
     * 
     * @param receive_id 物料入库单ID
     * @return 物料入库单行列表
     */
    public List<WmMaterialReceiveLine> selectWmMaterialReceiveLineList(Long receive_id);
}