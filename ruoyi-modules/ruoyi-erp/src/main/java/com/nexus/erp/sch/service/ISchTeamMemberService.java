package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchTeamMemberBo;
import com.nexus.erp.sch.domain.vo.SchTeamMemberVo;

import java.util.List;

/**
 * 班组成员Service接口
 *
 * @author nexus
 * @date 2025-12-24
 */
public interface ISchTeamMemberService {

    /**
     * 分页查询班组成员列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组成员分页列表
     */
    TableDataInfo<SchTeamMemberVo> selectPageList(SchTeamMemberBo bo, PageQuery pageQuery);

    /**
     * 查询班组成员列表
     *
     * @param bo 查询参数
     * @return 班组成员列表
     */
    List<SchTeamMemberVo> selectList(SchTeamMemberBo bo);

    /**
     * 根据ID获取班组成员详情
     *
     * @param id 成员ID
     * @return 班组成员详情
     */
    SchTeamMemberVo selectById(Long id);

    /**
     * 新增班组成员
     *
     * @param bo 班组成员业务对象
     * @return 影响行数
     */
    int insertByBo(SchTeamMemberBo bo);

    /**
     * 修改班组成员
     *
     * @param bo 班组成员业务对象
     * @return 影响行数
     */
    int updateByBo(SchTeamMemberBo bo);

    /**
     * 批量删除班组成员
     *
     * @param ids 成员ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
