package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchShiftBo;
import com.nexus.erp.sch.domain.vo.SchShiftVo;

import java.util.List;

/**
 * 班次定义Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchShiftService {

    TableDataInfo<SchShiftVo> selectPageList(SchShiftBo bo, PageQuery pageQuery);

    List<SchShiftVo> selectList(SchShiftBo bo);

    SchShiftVo selectById(Long id);

    int insertByBo(SchShiftBo bo);

    int updateByBo(SchShiftBo bo);

    int deleteByIds(Long[] ids);
}
