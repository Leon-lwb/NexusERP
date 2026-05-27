package org.dromara.erp.pu.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.bo.PuVendorEvaluationBo;
import org.dromara.erp.pu.domain.vo.PuVendorEvaluationVo;

import java.util.List;

public interface IPuVendorEvaluationService {

    TableDataInfo<PuVendorEvaluationVo> selectPageList(PuVendorEvaluationBo bo, PageQuery pageQuery);

    List<PuVendorEvaluationVo> selectList(PuVendorEvaluationBo bo);

    PuVendorEvaluationVo selectById(Long evaluationId);

    int insertByBo(PuVendorEvaluationBo bo);

    int updateByBo(PuVendorEvaluationBo bo);

    int deleteByIds(Long[] evaluationIds);

    void calculateByVendor(Long vendorId);

    void batchCalculateAll();
}
