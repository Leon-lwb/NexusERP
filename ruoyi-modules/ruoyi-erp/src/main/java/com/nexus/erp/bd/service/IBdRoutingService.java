package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdRoutingBo;
import com.nexus.erp.bd.domain.vo.BdRoutingStepVo;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;

import java.util.List;

/**
 * 工艺路线Service接口
 *
 * @author nexus
 */
public interface IBdRoutingService {

    /**
     * 分页查询工艺路线列表
     */
    TableDataInfo<BdRoutingVo> selectPageList(BdRoutingBo bo, PageQuery pageQuery);

    /**
     * 查询工艺路线列表
     */
    List<BdRoutingVo> selectList(BdRoutingBo bo);

    /**
     * 根据ID查询工艺路线
     */
    BdRoutingVo selectById(Long id);

    /**
     * 新增工艺路线
     */
    int insertByBo(BdRoutingBo bo);

    /**
     * 修改工艺路线
     */
    int updateByBo(BdRoutingBo bo);

    /**
     * 批量删除工艺路线
     */
    int deleteByIds(Long[] ids);

    /**
     * 激活工艺路线版本
     */
    void activateVersion(Long routingId);

    /**
     * 查询物料的当前有效工艺路线
     */
    BdRoutingVo getCurrentRouting(Long materialId);

    /**
     * 获取工艺路线详情（含所有步骤）
     */
    List<BdRoutingStepVo> getRoutingSteps(Long routingId);

    /**
     * 校验工艺路线
     */
    String verifyRouting(Long routingId);
}
