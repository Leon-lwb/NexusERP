package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchEmployeeScheduleBo;
import org.dromara.erp.sch.domain.vo.SchEmployeeScheduleVo;

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
