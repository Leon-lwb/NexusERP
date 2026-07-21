package com.nexus.erp.bd.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.bd.domain.BdCategoryAttribute;
import com.nexus.erp.bd.domain.bo.BdCategoryAttributeBo;
import com.nexus.erp.bd.domain.vo.BdCategoryAttributeVo;
import com.nexus.erp.bd.mapper.BdCategoryAttributeMapper;
import com.nexus.erp.bd.service.IBdCategoryAttributeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.Arrays;
import java.util.List;

/**
 * 物料分类属性模板Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdCategoryAttributeServiceImpl implements IBdCategoryAttributeService {

    private final BdCategoryAttributeMapper baseMapper;
    /**
     * 查询指定类别下的类别属性列表
     *
     * @param categoryId 类别ID
     * @return 结果列表
     */

    @Override
    public List<BdCategoryAttributeVo> selectListByCategory(Long categoryId) {
        LambdaQueryWrapper<BdCategoryAttribute> lqw = Wrappers.lambdaQuery();
        lqw.eq(categoryId != null, BdCategoryAttribute::getCategoryId, categoryId);
        lqw.orderByAsc(BdCategoryAttribute::getSortOrder);
        return baseMapper.selectVoList(lqw);
    }
    /**
     * 根据主键查询类别属性详情
     *
     * @param attrId 参数 attrId
     * @return 结果
     */

    @Override
    public BdCategoryAttributeVo selectById(Long attrId) {
        return baseMapper.selectVoById(attrId);
    }
    /**
     * 新增类别属性
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdCategoryAttributeBo bo) {
        BdCategoryAttribute entity = MapstructUtils.convert(bo, BdCategoryAttribute.class);
        validate(bo);
        return baseMapper.insert(entity);
    }
    /**
     * 修改类别属性
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdCategoryAttributeBo bo) {
        BdCategoryAttribute entity = MapstructUtils.convert(bo, BdCategoryAttribute.class);
        validate(bo);
        return baseMapper.updateById(entity);
    }
    /**
     * 批量删除类别属性
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(Arrays.asList(ids));
    }

    @Override
    public boolean checkAttrCodeUnique(BdCategoryAttributeBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BdCategoryAttribute>()
            .eq(BdCategoryAttribute::getAttrCode, bo.getAttrCode())
            .eq(BdCategoryAttribute::getCategoryId, bo.getCategoryId())
            .ne(ObjectUtil.isNotNull(bo.getAttrId()), BdCategoryAttribute::getAttrId, bo.getAttrId()));
        return !exist;
    }

    /**
     * 校验：分类存在性 / 编码在分类内唯一 / ENUM 必填枚举值
     */
    private void validate(BdCategoryAttributeBo bo) {
        if (bo.getCategoryId() == null) {
            throw new ServiceException("所属分类不能为空");
        }
        if (!checkAttrCodeUnique(bo)) {
            throw new ServiceException("属性编码'" + bo.getAttrCode() + "'在该分类下已存在");
        }
        if ("ENUM".equals(bo.getDataType()) && StringUtils.isBlank(bo.getEnumValues())) {
            throw new ServiceException("枚举类型属性必须填写枚举可选项");
        }
    }
}
