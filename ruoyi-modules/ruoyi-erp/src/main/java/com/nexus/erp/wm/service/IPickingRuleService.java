package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmPickingRuleBo;
import com.nexus.erp.wm.domain.vo.WmPickingRuleVo;

import java.util.List;
/**
 * 拣货规则服务接口
 * <p>
 * 定义拣货规则的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IPickingRuleService {

    /**
     * 分页查询拣货规则列表
     *
     * @param bo 拣货规则业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmPickingRuleVo> selectPageList(WmPickingRuleBo bo, PageQuery pageQuery);

    /**
     * 查询拣货规则列表
     *
     * @param bo 拣货规则业务对象（BO）
     * @return 结果列表
     */
    List<WmPickingRuleVo> selectList(WmPickingRuleBo bo);

    /**
     * 查询拣货规则
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmPickingRuleVo selectById(Long id);

    /**
     * 新增拣货规则
     *
     * @param bo 拣货规则业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmPickingRuleBo bo);

    /**
     * 修改拣货规则
     *
     * @param bo 拣货规则业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmPickingRuleBo bo);

    /**
     * 删除拣货规则
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
