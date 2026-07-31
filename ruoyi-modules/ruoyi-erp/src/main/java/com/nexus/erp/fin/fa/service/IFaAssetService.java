package com.nexus.erp.fin.fa.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.fa.domain.bo.FaAssetBo;
import com.nexus.erp.fin.fa.domain.vo.FaAssetVo;

import java.math.BigDecimal;

/**
 * 固定资产卡片 Service
 *
 * @author nexus
 * @date 2026-08-01
 */
public interface IFaAssetService {

    /**
     * 分页查询资产卡片
     */
    TableDataInfo<FaAssetVo> queryPageList(FaAssetBo bo, PageQuery pageQuery);

    /**
     * 查询资产详情
     */
    FaAssetVo queryById(Long assetId);

    /**
     * 新增卡片(自动编号 FA-YYYY-NNNN,默认 IN_USE,净值=原值)
     */
    Boolean insertByBo(FaAssetBo bo);

    /**
     * 修改卡片(已计提或已处置不可改;按类别带出折旧政策并重算净值)
     */
    Boolean updateByBo(FaAssetBo bo);

    /**
     * 删除卡片(已计提/已处置禁止)
     */
    Boolean deleteByIds(Long[] assetIds);

    /**
     * 原值调整(仅未计提或未处置;调整后重算净值,直线法按剩余月数重摊)
     */
    Boolean adjustValue(Long assetId, BigDecimal newOriginalValue);

    /**
     * 转固(CWIP->IN_USE,设启用日期为当前日)
     */
    Boolean activate(Long assetId);
}
