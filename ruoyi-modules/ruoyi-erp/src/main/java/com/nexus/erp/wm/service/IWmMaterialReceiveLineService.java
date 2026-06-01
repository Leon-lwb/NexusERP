package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;

import java.util.List;

/**
 * 物料入库单行Service接口
 *
 * @author nexus
 */
public interface IWmMaterialReceiveLineService {

    TableDataInfo<WmMaterialReceiveLineVo> selectPageList(WmMaterialReceiveLineBo bo, PageQuery pageQuery);

    List<WmMaterialReceiveLineVo> selectList(WmMaterialReceiveLineBo bo);

    WmMaterialReceiveLineVo selectById(Long id);

    int insertByBo(WmMaterialReceiveLineBo bo);

    int updateByBo(WmMaterialReceiveLineBo bo);

    int deleteByIds(Long[] ids);
}
