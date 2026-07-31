package com.nexus.erp.fin.fa.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.fa.domain.FaDepreciation;
import com.nexus.erp.fin.fa.domain.bo.FaDepreciationRunBo;
import com.nexus.erp.fin.fa.domain.vo.FaDepreciationVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 固定资产折旧 Service
 *
 * @author nexus
 * @date 2026-08-01
 */
public interface IFaDepreciationService {

    /**
     * 分页查询折旧记录
     */
    TableDataInfo<FaDepreciationVo> queryPageList(FaDepreciationRunBo bo, PageQuery pageQuery);

    /**
     * 折旧计划预览(某期间应计提明细,不过账)
     *
     * @return 折旧记录列表(PLANNED,未落库,含资产编号/名称)
     */
    List<FaDepreciationVo> preview(Long periodId);

    /**
     * 按期间计提并过账(幂等:同期间重跑返回既有凭证,不重复)
     *
     * @return 计提条数(重跑幂等时为 0)
     */
    int runDepreciation(Long periodId);

    /**
     * 计算单资产某期间应计提折旧额(直线/双倍余额/年数总和,含双倍余额后段转直线)
     */
    BigDecimal calcMonthlyDepreciation(com.nexus.erp.fin.fa.domain.FaAsset asset,
                                       com.nexus.erp.fin.fa.domain.FaAssetCategory category,
                                       long monthsInService);
}
