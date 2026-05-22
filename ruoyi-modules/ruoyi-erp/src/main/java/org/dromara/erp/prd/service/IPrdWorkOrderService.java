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

    /** 工单状态: 新建 */
    String STATUS_CREATED = "CREATED";
    /** 工单状态: 已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 工单状态: 生产中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 工单状态: 已完工 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 工单状态: 已关闭 */
    String STATUS_CLOSED = "CLOSED";

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

    /**
     * 下达工单 (CREATED → RELEASED)
     */
    void release(Long woId);

    /**
     * 开始生产 (RELEASED → IN_PROGRESS)
     */
    void startProduction(Long woId);

    /**
     * 完工 (IN_PROGRESS → COMPLETED)
     */
    void complete(Long woId);

    /**
     * 关闭工单 (→ CLOSED)
     */
    void close(Long woId);
}
