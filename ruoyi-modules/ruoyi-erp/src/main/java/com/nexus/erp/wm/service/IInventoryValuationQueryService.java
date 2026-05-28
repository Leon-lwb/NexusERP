package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;

public interface IInventoryValuationQueryService {

    TableDataInfo<WmMaterialStockVo> selectValuationPageList(WmMaterialStockBo bo, PageQuery pageQuery);

    List<WmMaterialStockVo> selectValuationList(WmMaterialStockBo bo);
}
