package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;

/**
 * 物料入库单明细Mapper接口
 *
 * @author nexus
 * @date 2025-12-09
 */
public interface WmMaterialReceiveDetailMapper extends BaseMapper<WmMaterialReceiveDetail> {
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
     * 删除物料入库单明细
     *
     * @param detailId 物料入库单明细主键
     * @return 结果
     */
    public int deleteWmMaterialReceiveDetailByDetailId(Long detailId);

    /**
     * 批量删除物料入库单明细
     *
     * @param detailIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmMaterialReceiveDetailByDetailIds(Long[] detailIds);
}
