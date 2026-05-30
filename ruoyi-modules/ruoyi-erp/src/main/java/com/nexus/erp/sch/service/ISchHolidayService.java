package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchHolidayBo;
import com.nexus.erp.sch.domain.vo.SchHolidayVo;

import java.util.List;

/**
 * 节假日设置Service接口
 *
 * @author nexus
 * @date 2025-12-17
 */
public interface ISchHolidayService {

    /**
     * 分页查询节假日设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 节假日设置分页列表
     */
    TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery);

    /**
     * 查询节假日设置列表
     *
     * @param bo 查询参数
     * @return 节假日设置列表
     */
    List<SchHolidayVo> selectList(SchHolidayBo bo);

    /**
     * 根据ID获取节假日设置详情
     *
     * @param id 节假日ID
     * @return 节假日设置详情
     */
    SchHolidayVo selectById(Long id);

    /**
     * 新增节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    int insertByBo(SchHolidayBo bo);

    /**
     * 修改节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    int updateByBo(SchHolidayBo bo);

    /**
     * 批量删除节假日设置
     *
     * @param ids 节假日ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
