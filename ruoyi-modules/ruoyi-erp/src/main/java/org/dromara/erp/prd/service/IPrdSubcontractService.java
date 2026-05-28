package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.bo.PrdSubcontractBo;
import org.dromara.erp.prd.domain.vo.PrdSubcontractVo;

import java.util.List;

/**
 * 委外订单Service接口
 *
 * @author nexus
 */
public interface IPrdSubcontractService {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_SUBMITTED = "SUBMITTED";
    String STATUS_APPROVED = "APPROVED";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CLOSED = "CLOSED";

    /**
     * 分页查询委外订单列表
     */
    TableDataInfo<PrdSubcontractVo> selectPageList(PrdSubcontractBo bo, PageQuery pageQuery);

    /**
     * 查询委外订单列表
     */
    List<PrdSubcontractVo> selectList(PrdSubcontractBo bo);

    /**
     * 查询委外订单详情
     */
    PrdSubcontractVo selectById(Long id);

    /**
     * 新增委外订单
     */
    int insertByBo(PrdSubcontractBo bo);

    /**
     * 修改委外订单
     */
    int updateByBo(PrdSubcontractBo bo);

    /**
     * 删除委外订单
     */
    int deleteByIds(Long[] ids);

    /**
     * 下达委外订单
     * 创建关联工单并更新状态为进行中
     * @param subcontractId 委外单ID
     */
    void release(Long subcontractId);

    /**
     * 开始委外订单
     * @param subcontractId 委外单ID
     */
    void start(Long subcontractId);

    /**
     * 完工委外订单
     *
     * @param subcontractId 委外单ID
     */
    void complete(Long subcontractId);

    /**
     * 关闭委外订单
     *
     * @param subcontractId 委外单ID
     */
    void close(Long subcontractId);
}
