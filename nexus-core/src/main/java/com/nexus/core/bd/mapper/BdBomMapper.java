package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdBom;
import com.nexus.core.bd.domain.dto.BomDetailDTO;
import com.nexus.core.bd.domain.vo.BomDetailVO;

/**
 * 产品BOMMapper接口
 *
 * @author nexus
 * @date 2025-10-18
 */
public interface BdBomMapper extends BaseMapper<BdBom> {
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
     * 删除产品BOM
     *
     * @param bomId 产品BOM主键
     * @return 结果
     */
    public int deleteBdBomByBomId(Long bomId);

    /**
     * 批量删除产品BOM
     *
     * @param bomIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdBomByBomIds(Long[] bomIds);

    /**
     * 创建BOM明细
     *
     * @param bomDetailDTO BOM明细DTO
     * @return 创建结果
     */
    int insertBomDetail(BomDetailDTO bomDetailDTO);
    
    /**
     * 修改BOM明细
     *
     * @param bomDetailDTO BOM明细DTO
     * @return 修改结果
     */
    int updateBomDetail(BomDetailDTO bomDetailDTO);
    
    /**
     * 根据明细ID查询BOM明细
     *
     * @param detailId 明细ID
     * @return BOM明细
     */
    String selectBomDetailByDetailId(Long detailId);
    
    /**
     * 根据BOM ID查询BOM明细列表
     *
     * @param bomId BOM ID
     * @return BOM明细列表
     */
    List<BomDetailVO> selectBomDetailList(Long bomId);

}