package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchWorkerBo;
import org.dromara.erp.sch.domain.vo.SchWorkerVo;

import java.util.List;

/**
 * 生产工人档案Service接口
 *
 * @author nexus
 * @date 2025-12-19
 */
public interface ISchWorkerService {

    TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery);

    List<SchWorkerVo> selectList(SchWorkerBo bo);

    SchWorkerVo selectById(Long id);

    int insertByBo(SchWorkerBo bo);

    int updateByBo(SchWorkerBo bo);

    int deleteByIds(Long[] ids);
}
