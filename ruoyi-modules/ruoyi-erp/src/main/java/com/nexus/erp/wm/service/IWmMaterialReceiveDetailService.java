package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;

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
