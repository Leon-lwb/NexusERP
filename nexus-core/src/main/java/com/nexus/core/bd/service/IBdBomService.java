package com.nexus.core.bd.service;

import java.util.List;
import com.nexus.core.bd.domain.BdBom;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.core.bd.domain.dto.BomDetailDTO;

/**
 * 产品BOMService接口
 *
 * @author nexus
 * @date 2025-10-18
 */
public interface IBdBomService extends IService<BdBom> {
    /**
     * 查询产品BOM
     *
     * @param bomId 产品BOM主键
     * @return 产品BOM
     */
    public BdBom selectBdBomByBomId(Long bomId);

    /**
     * 查询产品BOM列表
     *
     * @param bdBom 产品BOM
     * @return 产品BOM集合
     */
    public List<BdBom> selectBdBomList(BdBom bdBom);

    /**
     * 新增产品BOM
     *
     * @param bdBom 产品BOM
     * @return 结果
     */
    public int insertBdBom(BdBom bdBom);

    /**
     * 修改产品BOM
     *
     * @param bdBom 产品BOM
     * @return 结果
     */
    public int updateBdBom(BdBom bdBom);

    /**
     * 批量删除产品BOM
     *
     * @param bomIds 需要删除的产品BOM主键集合
     * @return 结果
     */
    public int deleteBdBomByBomIds(Long[] bomIds);

    /**
     * 删除产品BOM信息
     *
     * @param bomId 产品BOM主键
     * @return 结果
     */
    public int deleteBdBomByBomId(Long bomId);

    /**
     * 创建BOM明细
     *
     * @param bomDetailDTO BOM明细DTO
     * @return 创建结果
     */
    int insertBomDetail(BomDetailDTO bomDetailDTO);
}
