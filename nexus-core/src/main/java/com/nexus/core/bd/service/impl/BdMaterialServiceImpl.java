package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdMaterialMapper;
import com.nexus.core.bd.domain.BdMaterial;
import com.nexus.core.bd.service.IBdMaterialService;

/**
 * 物料产品主Service业务层处理
 *
 * @author nexus
 * @date 2025-10-04
 */
@Service
public class BdMaterialServiceImpl extends ServiceImpl<BdMaterialMapper, BdMaterial> implements IBdMaterialService {

    @Autowired
    private BdMaterialMapper bdMaterialMapper;

    /**
     * 查询物料产品主
     *
     * @param materialId 物料产品主主键
     * @return 物料产品主
     */
    @Override
    public BdMaterial selectBdMaterialByMaterialId(Long materialId) {
        return bdMaterialMapper.selectBdMaterialByMaterialId(materialId);
    }

    /**
     * 查询物料产品主列表
     *
     * @param bdMaterial 物料产品主
     * @return 物料产品主
     */
    @Override
    public List<BdMaterial> selectBdMaterialList(BdMaterial bdMaterial) {
        return bdMaterialMapper.selectBdMaterialList(bdMaterial);
    }

    /**
     * 新增物料产品主
     *
     * @param bdMaterial 物料产品主
     * @return 结果
     */
    @Override
    public int insertBdMaterial(BdMaterial bdMaterial) {
                bdMaterial.setCreateTime(DateUtils.getNowDate());
            return bdMaterialMapper.insertBdMaterial(bdMaterial);
    }

    /**
     * 修改物料产品主
     *
     * @param bdMaterial 物料产品主
     * @return 结果
     */
    @Override
    public int updateBdMaterial(BdMaterial bdMaterial) {
                bdMaterial.setUpdateTime(DateUtils.getNowDate());
        return bdMaterialMapper.updateBdMaterial(bdMaterial);
    }

    /**
     * 批量删除物料产品主
     *
     * @param materialIds 需要删除的物料产品主主键
     * @return 结果
     */
    @Override
    public int deleteBdMaterialByMaterialIds(Long[] materialIds) {
        return bdMaterialMapper.deleteBdMaterialByMaterialIds(materialIds);
    }

    /**
     * 删除物料产品主信息
     *
     * @param materialId 物料产品主主键
     * @return 结果
     */
    @Override
    public int deleteBdMaterialByMaterialId(Long materialId) {
        return bdMaterialMapper.deleteBdMaterialByMaterialId(materialId);
    }
}
