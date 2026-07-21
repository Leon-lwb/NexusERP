package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMiscIssueBo;
import com.nexus.erp.wm.domain.vo.WmMiscIssueVo;

import java.util.List;
/**
 * 杂项发料服务接口
 * <p>
 * 定义杂项发料的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IWmMiscIssueService {

    /**
     * 分页查询杂项发料列表
     *
     * @param bo 杂项发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMiscIssueVo> selectPageList(WmMiscIssueBo bo, PageQuery pageQuery);

    /**
     * 查询杂项发料列表
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 结果列表
     */
    List<WmMiscIssueVo> selectList(WmMiscIssueBo bo);

    /**
     * 查询杂项发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmMiscIssueVo selectById(Long id);

    /**
     * 新增杂项发料
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmMiscIssueBo bo);

    /**
     * 修改杂项发料
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmMiscIssueBo bo);

    /**
     * 删除杂项发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行杂项出库过账（扣减库存）
     */
    void execute(Long issueId);
}
