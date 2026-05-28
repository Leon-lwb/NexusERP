package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmStorageLocationBo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationVo;

import java.util.List;

/**
 * 库位Service接口
 *
 * @author ruoyi
 * @date 2025-12-02
 */
public interface IWmStorageLocationService {

    /**
     * 分页查询库位列表
     */
    TableDataInfo<WmStorageLocationVo> selectPageList(WmStorageLocationBo bo, PageQuery pageQuery);

    /**
     * 查询库位列表
     */
    List<WmStorageLocationVo> selectList(WmStorageLocationBo bo);

    /**
     * 查询库位
     */
    WmStorageLocationVo selectById(Long id);

    /**
     * 新增库位
     */
    int insertByBo(WmStorageLocationBo bo);

    /**
     * 修改库位
     */
    int updateByBo(WmStorageLocationBo bo);

    /**
     * 批量删除库位
     */
    int deleteByIds(Long[] ids);
}
