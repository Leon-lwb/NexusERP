package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.bo.BdMaterialBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.bd.service.IBdMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料产品主Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialServiceImpl implements IBdMaterialService {

    private final BdMaterialMapper baseMapper;

    /**
     * 查询物料产品主分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 物料产品主分页列表
     */
    @Override
    public TableDataInfo<BdMaterialVo> selectPageList(BdMaterialBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterial> lqw = buildQueryWrapper(bo);
        Page<BdMaterialVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料产品主列表
     *
     * @param bo 查询参数
     * @return 物料产品主列表
     */
    @Override
    public List<BdMaterialVo> selectList(BdMaterialBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询物料产品主
     *
     * @param id 物料产品主ID
     * @return 物料产品主信息
     */
    @Override
    public BdMaterialVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialBo bo) {
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialBo bo) {
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除物料产品主
     *
     * @param ids 物料产品主ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据分类ID列表查询物料产品主
     *
     * @param categoryIds 分类ID列表
     * @return 物料产品主列表
     */
    @Override
    public List<BdMaterialVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        return baseMapper.selectByCategoryIds(categoryIds);
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<BdMaterial> buildQueryWrapper(BdMaterialBo bo) {
        LambdaQueryWrapper<BdMaterial> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), BdMaterial::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), BdMaterial::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), BdMaterial::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(bo.getMaterialType() != null, BdMaterial::getMaterialType, bo.getMaterialType());
        lqw.eq(bo.getSourceType() != null, BdMaterial::getSourceType, bo.getSourceType());
        lqw.eq(bo.getCategoryId() != null, BdMaterial::getCategoryId, bo.getCategoryId());
        lqw.eq(bo.getStatus() != null, BdMaterial::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getQualityStatus()), BdMaterial::getQualityStatus, bo.getQualityStatus());
        return lqw;
    }
}
