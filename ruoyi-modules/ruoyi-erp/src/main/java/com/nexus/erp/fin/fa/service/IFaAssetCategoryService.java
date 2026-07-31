package com.nexus.erp.fin.fa.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.fa.domain.bo.FaAssetCategoryBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetCategoryVo;

import java.util.List;

/**
 * 固定资产类别 Service
 *
 * @author nexus
 * @date 2026-08-01
 */
public interface IFaAssetCategoryService {

    /**
     * 分页查询类别
     */
    TableDataInfo<FaAssetCategoryVo> queryPageList(FaAssetCategoryBo bo, PageQuery pageQuery);

    /**
     * 查询全部启用类别(下拉用)
     */
    List<FaAssetCategoryVo> queryAll();

    /**
     * 查询类别详情
     */
    FaAssetCategoryVo queryById(Long categoryId);

    /**
     * 新增类别(缺省科目回填:费用 6602/累计折旧 1602/损失 6711/收益 6301)
     */
    Boolean insertByBo(FaAssetCategoryBo bo);

    /**
     * 修改类别
     */
    Boolean updateByBo(FaAssetCategoryBo bo);

    /**
     * 删除类别(被卡片引用时拒绝)
     */
    Boolean deleteByIds(Long[] categoryIds);
}
