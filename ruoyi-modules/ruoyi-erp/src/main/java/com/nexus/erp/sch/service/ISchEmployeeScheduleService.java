package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchEmployeeScheduleBo;
import com.nexus.erp.sch.domain.vo.SchEmployeeScheduleVo;

import java.util.List;

/**
 * 员工排班明细Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchEmployeeScheduleService {

    TableDataInfo<SchEmployeeScheduleVo> selectPageList(SchEmployeeScheduleBo bo, PageQuery pageQuery);

    List<SchEmployeeScheduleVo> selectList(SchEmployeeScheduleBo bo);

    SchEmployeeScheduleVo selectById(Long id);

    int insertByBo(SchEmployeeScheduleBo bo);

    int updateByBo(SchEmployeeScheduleBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 生成员工排班明细
     */
    int generate();
}
