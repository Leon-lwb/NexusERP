package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.bo.PrdReworkOrderBo;
import org.dromara.erp.prd.domain.vo.PrdReworkOrderVo;

import java.util.List;

/**
 * 返工订单Service接口
 *
 * @author nexus
 */
public interface IPrdReworkOrderService {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";

    /**
     * 分页查询返工订单列表
     */
    TableDataInfo<PrdReworkOrderVo> selectPageList(PrdReworkOrderBo bo, PageQuery pageQuery);

    /**
     * 查询返工订单列表
     */
    List<PrdReworkOrderVo> selectList(PrdReworkOrderBo bo);

    /**
     * 查询返工订单详情
     */
    PrdReworkOrderVo selectById(Long id);

    /**
     * 新增返工订单
     */
    int insertByBo(PrdReworkOrderBo bo);

    /**
     * 修改返工订单
     */
    int updateByBo(PrdReworkOrderBo bo);

    /**
     * 删除返工订单
     */
    int deleteByIds(Long[] ids);

    /**
     * 开始返工订单
     * @param reworkId 返工单ID
     */
    void start(Long reworkId);

    /**
     * 完成返工订单
     * 完成时将报废数量回写到来源工单
     * @param reworkId 返工单ID
     */
    void complete(Long reworkId);
}
