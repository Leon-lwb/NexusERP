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

    TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery);

    List<SchHolidayVo> selectList(SchHolidayBo bo);

    SchHolidayVo selectById(Long id);

    int insertByBo(SchHolidayBo bo);

    int updateByBo(SchHolidayBo bo);

    int deleteByIds(Long[] ids);
}
