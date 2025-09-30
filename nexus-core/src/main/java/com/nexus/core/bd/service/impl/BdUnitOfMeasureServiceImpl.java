package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.core.bd.domain.BdUnitOfMeasure;
import com.nexus.core.bd.service.IBdUnitOfMeasureService;

/**
 * 计量单位Service业务层处理
 *
 * @author nexus
 * @date 2025-09-30
 */
@Service
public class BdUnitOfMeasureServiceImpl extends ServiceImpl<BdUnitOfMeasureMapper, BdUnitOfMeasure> implements IBdUnitOfMeasureService {

    @Autowired
    private BdUnitOfMeasureMapper bdUnitOfMeasureMapper;

    /**
     * 查询计量单位
     *
     * @param uomId 计量单位主键
     * @return 计量单位
     */
    @Override
    public BdUnitOfMeasure selectBdUnitOfMeasureByUomId(Long uomId) {
        return bdUnitOfMeasureMapper.selectBdUnitOfMeasureByUomId(uomId);
    }

    /**
     * 查询计量单位列表
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 计量单位
     */
    @Override
    public List<BdUnitOfMeasure> selectBdUnitOfMeasureList(BdUnitOfMeasure bdUnitOfMeasure) {
        return bdUnitOfMeasureMapper.selectBdUnitOfMeasureList(bdUnitOfMeasure);
    }

    /**
     * 新增计量单位
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 结果
     */
    @Override
    public int insertBdUnitOfMeasure(BdUnitOfMeasure bdUnitOfMeasure) {
                bdUnitOfMeasure.setCreateTime(DateUtils.getNowDate());
            return bdUnitOfMeasureMapper.insertBdUnitOfMeasure(bdUnitOfMeasure);
    }

    /**
     * 修改计量单位
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 结果
     */
    @Override
    public int updateBdUnitOfMeasure(BdUnitOfMeasure bdUnitOfMeasure) {
                bdUnitOfMeasure.setUpdateTime(DateUtils.getNowDate());
        return bdUnitOfMeasureMapper.updateBdUnitOfMeasure(bdUnitOfMeasure);
    }

    /**
     * 批量删除计量单位
     *
     * @param uomIds 需要删除的计量单位主键
     * @return 结果
     */
    @Override
    public int deleteBdUnitOfMeasureByUomIds(Long[] uomIds) {
        return bdUnitOfMeasureMapper.deleteBdUnitOfMeasureByUomIds(uomIds);
    }

    /**
     * 删除计量单位信息
     *
     * @param uomId 计量单位主键
     * @return 结果
     */
    @Override
    public int deleteBdUnitOfMeasureByUomId(Long uomId) {
        return bdUnitOfMeasureMapper.deleteBdUnitOfMeasureByUomId(uomId);
    }
}
