package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchTeamBo;
import org.dromara.erp.sch.domain.vo.SchTeamVo;

import java.util.List;

/**
 * 班组设置Service接口
 *
 * @author nexus
 * @date 2025-12-23
 */
public interface ISchTeamService {

    TableDataInfo<SchTeamVo> selectPageList(SchTeamBo bo, PageQuery pageQuery);

    List<SchTeamVo> selectList(SchTeamBo bo);

    SchTeamVo selectById(Long id);

    int insertByBo(SchTeamBo bo);

    int updateByBo(SchTeamBo bo);

    int deleteByIds(Long[] ids);
}
