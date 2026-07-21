package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmPutawayRuleBo;
import com.nexus.erp.wm.domain.vo.WmPutawayRuleVo;

import java.util.List;
/**
 * 上架规则服务接口
 * <p>
 * 定义上架规则的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IPutawayRuleService {

    /**
     * 分页查询上架规则列表
     *
     * @param bo 上架规则业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmPutawayRuleVo> selectPageList(WmPutawayRuleBo bo, PageQuery pageQuery);

    /**
     * 查询上架规则列表
     *
     * @param bo 上架规则业务对象（BO）
     * @return 结果列表
     */
    List<WmPutawayRuleVo> selectList(WmPutawayRuleBo bo);

    /**
     * 查询上架规则
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmPutawayRuleVo selectById(Long id);

    /**
     * 新增上架规则
     *
     * @param bo 上架规则业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmPutawayRuleBo bo);

    /**
     * 修改上架规则
     *
     * @param bo 上架规则业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmPutawayRuleBo bo);

    /**
     * 删除上架规则
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
