package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchHolidayBo;
import org.dromara.erp.sch.domain.vo.SchHolidayVo;

import java.util.List;

/**
 * 节假日设置Service接口
 *
 * @author nexus
 * @date 2025-12-17
 */
public interface ISchHolidayService {

    TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery);

    List<SchHolidayVo> selectList(SchHolidayBo bo);

    SchHolidayVo selectById(Long id);

    int insertByBo(SchHolidayBo bo);

    int updateByBo(SchHolidayBo bo);

    int deleteByIds(Long[] ids);
}
