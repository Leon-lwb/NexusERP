package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchPlanBo;
import org.dromara.erp.sch.domain.vo.SchPlanVo;

import java.util.List;

/**
 * 排班计划Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchPlanService {

    TableDataInfo<SchPlanVo> selectPageList(SchPlanBo bo, PageQuery pageQuery);

    List<SchPlanVo> selectList(SchPlanBo bo);

    SchPlanVo selectById(Long id);

    int insertByBo(SchPlanBo bo);

    int updateByBo(SchPlanBo bo);

    int deleteByIds(Long[] ids);
}
