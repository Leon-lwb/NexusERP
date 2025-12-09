package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.service.IWmMaterialReceiveDetailService;

/**
 * 物料入库单明细Service业务层处理
 *
 * @author nexus
 * @date 2025-12-09
 */
@Service
public class WmMaterialReceiveDetailServiceImpl extends ServiceImpl<WmMaterialReceiveDetailMapper, WmMaterialReceiveDetail> implements IWmMaterialReceiveDetailService {

    @Autowired
    private WmMaterialReceiveDetailMapper wmMaterialReceiveDetailMapper;

    /**
     * 查询物料入库单明细
     *
     * @param detailId 物料入库单明细主键
     * @return 物料入库单明细
     */
    @Override
    public WmMaterialReceiveDetail selectWmMaterialReceiveDetailByDetailId(Long detailId) {
        return wmMaterialReceiveDetailMapper.selectWmMaterialReceiveDetailByDetailId(detailId);
    }

    /**
     * 查询物料入库单明细列表
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 物料入库单明细
     */
    @Override
    public List<WmMaterialReceiveDetail> selectWmMaterialReceiveDetailList(WmMaterialReceiveDetail wmMaterialReceiveDetail) {
        return wmMaterialReceiveDetailMapper.selectWmMaterialReceiveDetailList(wmMaterialReceiveDetail);
    }

    /**
     * 新增物料入库单明细
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 结果
     */
    @Override
    public int insertWmMaterialReceiveDetail(WmMaterialReceiveDetail wmMaterialReceiveDetail) {
                wmMaterialReceiveDetail.setCreateTime(DateUtils.getNowDate());
            return wmMaterialReceiveDetailMapper.insertWmMaterialReceiveDetail(wmMaterialReceiveDetail);
    }

    /**
     * 修改物料入库单明细
     *
     * @param wmMaterialReceiveDetail 物料入库单明细
     * @return 结果
     */
    @Override
    public int updateWmMaterialReceiveDetail(WmMaterialReceiveDetail wmMaterialReceiveDetail) {
                wmMaterialReceiveDetail.setUpdateTime(DateUtils.getNowDate());
        return wmMaterialReceiveDetailMapper.updateWmMaterialReceiveDetail(wmMaterialReceiveDetail);
    }

    /**
     * 批量删除物料入库单明细
     *
     * @param detailIds 需要删除的物料入库单明细主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialReceiveDetailByDetailIds(Long[] detailIds) {
        return wmMaterialReceiveDetailMapper.deleteWmMaterialReceiveDetailByDetailIds(detailIds);
    }

    /**
     * 删除物料入库单明细信息
     *
     * @param detailId 物料入库单明细主键
     * @return 结果
     */
    @Override
    public int deleteWmMaterialReceiveDetailByDetailId(Long detailId) {
        return wmMaterialReceiveDetailMapper.deleteWmMaterialReceiveDetailByDetailId(detailId);
    }
}
