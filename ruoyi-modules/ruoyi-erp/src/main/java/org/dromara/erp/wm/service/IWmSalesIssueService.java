package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmSalesIssueBo;
import org.dromara.erp.wm.domain.vo.WmSalesIssueVo;

import java.util.List;

/**
 * 销售出库Service接口
 *
 * @author ruoyi
 * @date 2026-04-03
 */
public interface IWmSalesIssueService {

    /**
     * 分页查询销售出库列表
     *
     * @param bo         销售出库业务对象
     * @param pageQuery  分页参数
     * @return 销售出库分页集合
     */
    TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery);

    /**
     * 查询销售出库列表
     *
     * @param bo 销售出库业务对象
     * @return 销售出库集合
     */
    List<WmSalesIssueVo> selectList(WmSalesIssueBo bo);

    /**
     * 查询销售出库
     *
     * @param id 销售出库主键
     * @return 销售出库
     */
    WmSalesIssueVo selectById(Long id);

    /**
     * 新增销售出库
     *
     * @param bo 销售出库业务对象
     * @return 结果
     */
    int insertByBo(WmSalesIssueBo bo);

    /**
     * 修改销售出库
     *
     * @param bo 销售出库业务对象
     * @return 结果
     */
    int updateByBo(WmSalesIssueBo bo);

    /**
     * 批量删除销售出库
     *
     * @param ids 需要删除的销售出库主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认销售出库
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    int confirmSalesIssue(Long issueId);

    /**
     * 完成销售出库（扣减库存）
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    int completeSalesIssue(Long issueId);
}
