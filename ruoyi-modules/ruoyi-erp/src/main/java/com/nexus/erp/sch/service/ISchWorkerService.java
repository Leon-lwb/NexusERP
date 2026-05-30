package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchWorkerBo;
import com.nexus.erp.sch.domain.vo.SchWorkerVo;

import java.util.List;

/**
 * 生产工人档案Service接口
 *
 * @author nexus
 * @date 2025-12-19
 */
public interface ISchWorkerService {

    /**
     * 分页查询生产工人档案列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产工人档案分页列表
     */
    TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery);

    /**
     * 查询生产工人档案列表
     *
     * @param bo 查询参数
     * @return 生产工人档案列表
     */
    List<SchWorkerVo> selectList(SchWorkerBo bo);

    /**
     * 根据ID获取生产工人档案详情
     *
     * @param id 工人档案ID
     * @return 生产工人档案详情
     */
    SchWorkerVo selectById(Long id);

    /**
     * 新增生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    int insertByBo(SchWorkerBo bo);

    /**
     * 修改生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    int updateByBo(SchWorkerBo bo);

    /**
     * 批量删除生产工人档案
     *
     * @param ids 工人档案ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
