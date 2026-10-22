package com.nexus.erp.fin.fa.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.fin.fa.constant.FaConstants;
import com.nexus.erp.fin.fa.domain.FaAsset;
import com.nexus.erp.fin.fa.domain.FaAssetCategory;
import com.nexus.erp.fin.fa.domain.bo.FaAssetCategoryBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetCategoryVo;
import com.nexus.erp.fin.fa.mapper.FaAssetCategoryMapper;
import com.nexus.erp.fin.fa.mapper.FaAssetMapper;
import com.nexus.erp.fin.fa.service.IFaAssetCategoryService;
import com.nexus.erp.fin.gl.domain.GlAccount;
import com.nexus.erp.fin.gl.mapper.GlAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 固定资产类别 Service 实现
 *
 * @author nexus
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FaAssetCategoryServiceImpl implements IFaAssetCategoryService {

    private final FaAssetCategoryMapper baseMapper;
    private final FaAssetMapper assetMapper;
    private final GlAccountMapper accountMapper;

    /** 分页查询类别 */
    @Override
    public TableDataInfo<FaAssetCategoryVo> queryPageList(FaAssetCategoryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FaAssetCategory> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getCategoryName()), FaAssetCategory::getCategoryName, bo.getCategoryName());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), FaAssetCategory::getStatus, bo.getStatus());
        }
        lqw.orderByAsc(FaAssetCategory::getSortOrder);
        Page<FaAssetCategoryVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /** 查询全部启用类别(下拉用) */
    @Override
    public List<FaAssetCategoryVo> queryAll() {
        return baseMapper.selectVoList(new LambdaQueryWrapper<FaAssetCategory>()
            .eq(FaAssetCategory::getStatus, "0")
            .orderByAsc(FaAssetCategory::getSortOrder));
    }

    /** 查询类别详情 */
    @Override
    public FaAssetCategoryVo queryById(Long categoryId) {
        return baseMapper.selectVoById(categoryId);
    }

    /** 新增类别:缺省科目回填(费用 6602/累计折旧 1602/损失 6711/收益 6301) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FaAssetCategoryBo bo) {
        FaAssetCategory entity = MapstructUtils.convert(bo, FaAssetCategory.class);
        entity.setCategoryId(IdGeneratorUtil.nextLongId());
        if (entity.getUsefulLife() == null) {
            entity.setUsefulLife(120);
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("0");
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getDebitAccountId() == null) {
            entity.setDebitAccountId(findAccountIdByCode(FaConstants.ACCT_ADMIN_EXPENSE));
        }
        if (entity.getCreditAccountId() == null) {
            entity.setCreditAccountId(findAccountIdByCode(FaConstants.ACCT_FA_DEPRECIATION));
        }
        if (entity.getDisposalLossAccountId() == null) {
            entity.setDisposalLossAccountId(findAccountIdByCode(FaConstants.ACCT_NON_OPER_EXPENSE));
        }
        if (entity.getDisposalGainAccountId() == null) {
            entity.setDisposalGainAccountId(findAccountIdByCode(FaConstants.ACCT_NON_OPER_INCOME));
        }
        entity.setVersion(0);
        return baseMapper.insert(entity) > 0;
    }

    /** 修改类别 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FaAssetCategoryBo bo) {
        FaAssetCategory existing = baseMapper.selectById(bo.getCategoryId());
        if (existing == null) {
            throw new ServiceException("资产类别不存在");
        }
        FaAssetCategory entity = MapstructUtils.convert(bo, FaAssetCategory.class);
        entity.setVersion(existing.getVersion());
        return baseMapper.updateById(entity) > 0;
    }

    /** 删除类别:被卡片引用时拒绝 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return true;
        }
        for (Long categoryId : categoryIds) {
            Long used =
                    assetMapper.selectCount(
                            new LambdaQueryWrapper<FaAsset>()
                                    .eq(FaAsset::getCategoryId, categoryId));
            if (used != null && used > 0) {
                throw new ServiceException("类别已被资产卡片引用,不能删除");
            }
        }
        return baseMapper.deleteByIds(List.of(categoryIds)) > 0;
    }

    /** 按科目编码查询科目ID */
    private Long findAccountIdByCode(String code) {
        GlAccount acc = accountMapper.selectOne(Wrappers.<GlAccount>lambdaQuery()
            .eq(GlAccount::getAccountCode, code));
        if (acc == null) {
            throw new ServiceException("科目不存在: {}", code);
        }
        return acc.getAccountId();
    }
}
