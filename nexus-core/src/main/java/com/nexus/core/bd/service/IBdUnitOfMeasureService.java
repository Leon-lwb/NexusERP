package com.nexus.core.bd.service;

import java.util.List;
import com.nexus.core.bd.domain.BdUnitOfMeasure;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 计量单位Service接口
 *
 * @author nexus
 * @date 2025-09-30
 */
public interface IBdUnitOfMeasureService extends IService<BdUnitOfMeasure> {
    /**
     * 查询计量单位
     *
     * @param uomId 计量单位主键
     * @return 计量单位
     */
    public BdUnitOfMeasure selectBdUnitOfMeasureByUomId(Long uomId);

    /**
     * 查询计量单位列表
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 计量单位集合
     */
    public List<BdUnitOfMeasure> selectBdUnitOfMeasureList(BdUnitOfMeasure bdUnitOfMeasure);

    /**
     * 新增计量单位
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 结果
     */
    public int insertBdUnitOfMeasure(BdUnitOfMeasure bdUnitOfMeasure);

    /**
     * 修改计量单位
     *
     * @param bdUnitOfMeasure 计量单位
     * @return 结果
     */
    public int updateBdUnitOfMeasure(BdUnitOfMeasure bdUnitOfMeasure);

    /**
     * 批量删除计量单位
     *
     * @param uomIds 需要删除的计量单位主键集合
     * @return 结果
     */
    public int deleteBdUnitOfMeasureByUomIds(Long[] uomIds);

    /**
     * 删除计量单位信息
     *
     * @param uomId 计量单位主键
     * @return 结果
     */
    public int deleteBdUnitOfMeasureByUomId(Long uomId);
}
