package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchShiftBo;
import com.nexus.erp.sch.domain.vo.SchShiftVo;

import java.util.List;

/**
 * 班次定义Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchShiftService {

    /**
     * 分页查询班次定义列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班次定义分页列表
     */
    TableDataInfo<SchShiftVo> selectPageList(SchShiftBo bo, PageQuery pageQuery);

    /**
     * 查询班次定义列表
     *
     * @param bo 查询参数
     * @return 班次定义列表
     */
    List<SchShiftVo> selectList(SchShiftBo bo);

    /**
     * 根据ID获取班次定义详情
     *
     * @param id 班次ID
     * @return 班次定义详情
     */
    SchShiftVo selectById(Long id);

    /**
     * 新增班次定义
     *
     * @param bo 班次定义业务对象
     * @return 影响行数
     */
    int insertByBo(SchShiftBo bo);

    /**
     * 修改班次定义
     *
     * @param bo 班次定义业务对象
     * @return 影响行数
     */
    int updateByBo(SchShiftBo bo);

    /**
     * 批量删除班次定义
     *
     * @param ids 班次ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
