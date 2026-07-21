package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;

import java.util.List;
/**
 * 库存计价查询服务接口
 * <p>
 * 定义库存计价查询的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IInventoryValuationQueryService {

    /**
     * 查询库存计价查询
     *
     * @param bo 库存计价查询业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMaterialStockVo> selectValuationPageList(WmMaterialStockBo bo, PageQuery pageQuery);

    /**
     * 查询库存计价查询
     *
     * @param bo 库存计价查询业务对象（BO）
     * @return 结果列表
     */
    List<WmMaterialStockVo> selectValuationList(WmMaterialStockBo bo);
}
