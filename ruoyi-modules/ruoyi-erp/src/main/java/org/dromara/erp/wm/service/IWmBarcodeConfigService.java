package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmBarcodeConfigBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeConfigVo;

import java.util.List;

/**
 * 条码配置 Service 接口
 */
public interface IWmBarcodeConfigService {

    TableDataInfo<WmBarcodeConfigVo> selectPageList(WmBarcodeConfigBo bo, PageQuery pageQuery);

    List<WmBarcodeConfigVo> selectList(WmBarcodeConfigBo bo);

    WmBarcodeConfigVo selectById(Long id);

    int insertByBo(WmBarcodeConfigBo bo);

    int updateByBo(WmBarcodeConfigBo bo);

    int deleteByIds(Long[] ids);
}
