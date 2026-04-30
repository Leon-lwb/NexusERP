package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveDetailVo;

import java.util.List;

/**
 * 物料入库单明细Service接口
 *
 * @author ruoyi
 */
public interface IWmMaterialReceiveDetailService {

    TableDataInfo<WmMaterialReceiveDetailVo> selectPageList(WmMaterialReceiveDetailBo bo, PageQuery pageQuery);

    List<WmMaterialReceiveDetailVo> selectList(WmMaterialReceiveDetailBo bo);

    WmMaterialReceiveDetailVo selectById(Long id);

    int insertByBo(WmMaterialReceiveDetailBo bo);

    int updateByBo(WmMaterialReceiveDetailBo bo);

    int deleteByIds(Long[] ids);
}
