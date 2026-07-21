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

    /**
     * 分页查询物料领用行列表
     *
     * @param bo 物料领用行业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMaterialReceiveLineVo> selectPageList(WmMaterialReceiveLineBo bo, PageQuery pageQuery);

    /**
     * 查询物料领用行列表
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 结果列表
     */
    List<WmMaterialReceiveLineVo> selectList(WmMaterialReceiveLineBo bo);

    /**
     * 查询物料领用行
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmMaterialReceiveLineVo selectById(Long id);

    /**
     * 新增物料领用行
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmMaterialReceiveLineBo bo);

    /**
     * 修改物料领用行
     *
     * @param bo 物料领用行业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmMaterialReceiveLineBo bo);

    /**
     * 删除物料领用行
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
