package org.dromara.erp.so.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.bo.SoReturnBo;
import org.dromara.erp.so.domain.vo.SoReturnVo;

import java.util.List;

public interface ISoReturnService {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_RECEIVED = "RECEIVED";
    String STATUS_CLOSED = "CLOSED";

    TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery);

    List<SoReturnVo> selectList(SoReturnBo bo);

    SoReturnVo selectById(Long id);

    int insertByBo(SoReturnBo bo);

    int updateByBo(SoReturnBo bo);

    int deleteByIds(Long[] ids);

    void confirm(Long returnId);

    /**
     * 执行退货（退货入库）
     */
    void execute(Long returnId);
}
