package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdMaterial;

/**
 * 物料产品主Mapper接口
 *
 * @author nexus
 * @date 2025-10-04
 */
public interface BdMaterialMapper extends BaseMapper<BdMaterial> {
    /**
     * 查询物料产品主
     *
     * @param materialId 物料产品主主键
     * @return 物料产品主
     */
    public BdMaterial selectBdMaterialByMaterialId(Long materialId);

    /**
     * 查询物料产品主列表
     *
     * @param bdMaterial 物料产品主
     * @return 物料产品主集合
     */
    public List<BdMaterial> selectBdMaterialList(BdMaterial bdMaterial);

    /**
     * 新增物料产品主
     *
     * @param bdMaterial 物料产品主
     * @return 结果
     */
    public int insertBdMaterial(BdMaterial bdMaterial);

    /**
     * 修改物料产品主
     *
     * @param bdMaterial 物料产品主
     * @return 结果
     */
    public int updateBdMaterial(BdMaterial bdMaterial);

    /**
     * 删除物料产品主
     *
     * @param materialId 物料产品主主键
     * @return 结果
     */
    public int deleteBdMaterialByMaterialId(Long materialId);

    /**
     * 批量删除物料产品主
     *
     * @param materialIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdMaterialByMaterialIds(Long[] materialIds);
}
