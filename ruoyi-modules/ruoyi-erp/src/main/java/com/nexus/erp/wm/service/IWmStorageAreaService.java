package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmStorageAreaBo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;

import java.util.List;

/**
 * 库区Service接口
 *
 * @author nexus
 * @date 2025-12-01
 */
public interface IWmStorageAreaService {

    /**
     * 分页查询库区列表
     */
    TableDataInfo<WmStorageAreaVo> selectPageList(WmStorageAreaBo bo, PageQuery pageQuery);

    /**
     * 查询库区列表
     */
    List<WmStorageAreaVo> selectList(WmStorageAreaBo bo);

    /**
     * 查询库区
     */
    WmStorageAreaVo selectById(Long id);

    /**
     * 新增库区
     */
    int insertByBo(WmStorageAreaBo bo);

    /**
     * 修改库区
     */
    int updateByBo(WmStorageAreaBo bo);

    /**
     * 批量删除库区
     */
    int deleteByIds(Long[] ids);
}
