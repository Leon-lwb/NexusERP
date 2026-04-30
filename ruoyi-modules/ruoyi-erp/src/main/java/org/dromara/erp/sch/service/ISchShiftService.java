package org.dromara.erp.sch.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.bo.SchShiftBo;
import org.dromara.erp.sch.domain.vo.SchShiftVo;

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
