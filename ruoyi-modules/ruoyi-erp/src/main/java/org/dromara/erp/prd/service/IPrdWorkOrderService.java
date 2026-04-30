package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdWorkOrder;
import org.dromara.erp.prd.domain.bo.PrdWorkOrderBo;
import org.dromara.erp.prd.domain.vo.PrdWorkOrderVo;

import java.util.List;

/**
 * 生产工单Service接口
 *
 * @author ruoyi
 */
public interface IPrdWorkOrderService {

    TableDataInfo<PrdWorkOrderVo> selectPageList(PrdWorkOrderBo bo, PageQuery pageQuery);

    List<PrdWorkOrderVo> selectList(PrdWorkOrderBo bo);

    PrdWorkOrderVo selectById(Long id);

    PrdWorkOrder selectPrdWorkOrderByWoId(Long woId);

    List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder);

    int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder);

    int insertByBo(PrdWorkOrderBo bo);

    int updateByBo(PrdWorkOrderBo bo);

    int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder);

    int deleteByIds(Long[] ids);

    int deletePrdWorkOrderByWoIds(Long[] woIds);

    int deletePrdWorkOrderByWoId(Long woId);
}
