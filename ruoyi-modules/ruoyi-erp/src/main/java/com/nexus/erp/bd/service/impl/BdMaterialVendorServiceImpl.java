package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVendorVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdMaterialVendorMapper;
import com.nexus.erp.bd.service.IBdMaterialVendorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料-供应商关系Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialVendorServiceImpl implements IBdMaterialVendorService {

    private final BdMaterialVendorMapper baseMapper;
    private final BdMaterialMapper materialMapper;

    /**
     * 分页查询物料-供应商关系列表
     */
    @Override
    public TableDataInfo<BdMaterialVendorVo> selectPageList(BdMaterialVendorBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterialVendor> lqw = buildQueryWrapper(bo);
        Page<BdMaterialVendorVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 批量填充物料编码/名称
        fillMaterialInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料-供应商关系列表（不分页）
     */
    @Override
    public List<BdMaterialVendorVo> selectList(BdMaterialVendorBo bo) {
        List<BdMaterialVendorVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        // 批量填充物料编码/名称
        fillMaterialInfo(list);
        return list;
    }

    /**
     * 根据ID查询物料-供应商关系
     */
    @Override
    public BdMaterialVendorVo selectById(Long id) {
        BdMaterialVendorVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillMaterialInfo(Collections.singletonList(vo));
        }
        return vo;
    }

    /**
     * 新增物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialVendorBo bo) {
        // 1. 查重: 同一物料+供应商只能有一条关系
        Long existCount = baseMapper.selectCount(Wrappers.<BdMaterialVendor>lambdaQuery()
            .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
            .eq(BdMaterialVendor::getVendorId, bo.getVendorId()));
        if (existCount != null && existCount > 0) {
            throw new ServiceException("该物料与供应商的关系已存在");
        }
        // 2. isDefault 唯一性: 同一物料只能有一个默认供应商
        if (Long.valueOf(1L).equals(bo.getIsDefault())) {
            Long defaultCount = baseMapper.selectCount(Wrappers.<BdMaterialVendor>lambdaQuery()
                .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
                .eq(BdMaterialVendor::getIsDefault, 1L));
            if (defaultCount != null && defaultCount > 0) {
                throw new ServiceException("该物料已存在默认供应商, 请先取消原默认");
            }
        }
        BdMaterialVendor entity = MapstructUtils.convert(bo, BdMaterialVendor.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialVendorBo bo) {
        // isDefault 唯一性: 切换为默认时, 将该物料的其他默认供应商置为非默认(排除自身)
        if (Long.valueOf(1L).equals(bo.getIsDefault())
            && bo.getRelationId() != null
            && bo.getMaterialId() != null) {
            baseMapper.update(null, Wrappers.<BdMaterialVendor>lambdaUpdate()
                .set(BdMaterialVendor::getIsDefault, 0L)
                .eq(BdMaterialVendor::getMaterialId, bo.getMaterialId())
                .ne(BdMaterialVendor::getRelationId, bo.getRelationId()));
        }
        BdMaterialVendor entity = MapstructUtils.convert(bo, BdMaterialVendor.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除物料-供应商关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<BdMaterialVendor> buildQueryWrapper(BdMaterialVendorBo bo) {
        LambdaQueryWrapper<BdMaterialVendor> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getMaterialId() != null, BdMaterialVendor::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getVendorId() != null, BdMaterialVendor::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorCode()), BdMaterialVendor::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), BdMaterialVendor::getVendorName, bo.getVendorName());
        lqw.eq(bo.getStatus() != null, BdMaterialVendor::getStatus, bo.getStatus());
        lqw.orderByAsc(BdMaterialVendor::getPriority);
        return lqw;
    }

    /**
     * 批量填充物料编码/名称
     * <p>
     * 收集所有关系的 materialId，一次性查询对应的物料信息，
     * 通过 Map 映射填充到每条记录中，避免 N+1 查询问题。
     * </p>
     *
     * @param list 物料-供应商关系Vo列表
     */
    private void fillMaterialInfo(List<BdMaterialVendorVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> materialIds = list.stream()
            .map(BdMaterialVendorVo::getMaterialId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (materialIds.isEmpty()) {
            return;
        }
        List<BdMaterialVo> materials = materialMapper.selectVoByIds(materialIds);
        Map<Long, BdMaterialVo> materialMap = materials.stream()
            .collect(Collectors.toMap(BdMaterialVo::getMaterialId, m -> m));
        for (BdMaterialVendorVo vo : list) {
            BdMaterialVo material = materialMap.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
        }
    }
}
