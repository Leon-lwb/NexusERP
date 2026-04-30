package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveLineVo;

import java.util.List;

/**
 * 物料入库单行Service接口
 *
 * @author ruoyi
 */
public interface IWmMaterialReceiveLineService {

    TableDataInfo<WmMaterialReceiveLineVo> selectPageList(WmMaterialReceiveLineBo bo, PageQuery pageQuery);

    List<WmMaterialReceiveLineVo> selectList(WmMaterialReceiveLineBo bo);

    WmMaterialReceiveLineVo selectById(Long id);

    int insertByBo(WmMaterialReceiveLineBo bo);

    int updateByBo(WmMaterialReceiveLineBo bo);

    int deleteByIds(Long[] ids);
}
