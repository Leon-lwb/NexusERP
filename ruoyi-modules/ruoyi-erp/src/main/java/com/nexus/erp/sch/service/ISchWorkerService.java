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

    TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery);

    List<SchWorkerVo> selectList(SchWorkerBo bo);

    SchWorkerVo selectById(Long id);

    int insertByBo(SchWorkerBo bo);

    int updateByBo(SchWorkerBo bo);

    int deleteByIds(Long[] ids);
}
