package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmMaterialStockBo;
import org.dromara.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;

public interface IInventoryValuationQueryService {

    TableDataInfo<WmMaterialStockVo> selectValuationPageList(WmMaterialStockBo bo, PageQuery pageQuery);

    List<WmMaterialStockVo> selectValuationList(WmMaterialStockBo bo);
}
