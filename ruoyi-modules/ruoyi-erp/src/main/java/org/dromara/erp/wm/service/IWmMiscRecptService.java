package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmMiscRecptBo;
import org.dromara.erp.wm.domain.vo.WmMiscRecptVo;

import java.util.List;

public interface IWmMiscRecptService {

    TableDataInfo<WmMiscRecptVo> selectPageList(WmMiscRecptBo bo, PageQuery pageQuery);

    List<WmMiscRecptVo> selectList(WmMiscRecptBo bo);

    WmMiscRecptVo selectById(Long id);

    int insertByBo(WmMiscRecptBo bo);

    int updateByBo(WmMiscRecptBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行杂项入库过账（增加库存）
     */
    void execute(Long recptId);
}
