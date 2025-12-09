package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 物料入库单明细Service接口
 *
 * @author nexus
 * @date 2025-12-09
 */
public interface IWmMaterialReceiveDetailService extends IService<WmMaterialReceiveDetail> {
    /**
     * 查询物料入库单明细
     *
     * @param detailId 物料入库单明细主键
     * @return 物料入库单明细
     */
    public WmMaterialReceiveDetail selectWmMaterialReceiveDetailByDetailId(Long detailId);

    /**
     * 查询物料入库单明细列表
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 物料入库单明细集合
     */
    public List<WmMaterialReceiveDetail> selectWmMaterialReceiveDetailList(WmMaterialReceiveDetail wmMaterialReceiveDetail);

    /**
     * 新增物料入库单明细
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 结果
     */
    public int insertWmMaterialReceiveDetail(WmMaterialReceiveDetail wmMaterialReceiveDetail);

    /**
     * 修改物料入库单明细
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 结果
     */
    public int updateWmMaterialReceiveDetail(WmMaterialReceiveDetail wmMaterialReceiveDetail);

    /**
     * 批量删除物料入库单明细
     *
     * @param detailIds 需要删除的物料入库单明细主键集合
     * @return 结果
     */
    public int deleteWmMaterialReceiveDetailByDetailIds(Long[] detailIds);

    /**
     * 删除物料入库单明细信息
     *
     * @param detailId 物料入库单明细主键
     * @return 结果
     */
    public int deleteWmMaterialReceiveDetailByDetailId(Long detailId);
}
