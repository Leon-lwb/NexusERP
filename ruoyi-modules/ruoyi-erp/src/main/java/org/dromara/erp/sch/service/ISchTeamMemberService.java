package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchTeamMemberBo;
import org.dromara.erp.sch.domain.vo.SchTeamMemberVo;

import java.util.List;

/**
 * 班组成员Service接口
 *
 * @author nexus
 * @date 2025-12-24
 */
public interface ISchTeamMemberService {

    TableDataInfo<SchTeamMemberVo> selectPageList(SchTeamMemberBo bo, PageQuery pageQuery);

    List<SchTeamMemberVo> selectList(SchTeamMemberBo bo);

    SchTeamMemberVo selectById(Long id);

    int insertByBo(SchTeamMemberBo bo);

    int updateByBo(SchTeamMemberBo bo);

    int deleteByIds(Long[] ids);
}
