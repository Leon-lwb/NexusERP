package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;

import java.util.List;

/**
 * 物料入库单明细Service接口
 *
 * @author nexus
 */
public interface IWmMaterialReceiveDetailService {

    /**
     * 分页查询物料领用明细列表
     *
     * @param bo 物料领用明细业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMaterialReceiveDetailVo> selectPageList(WmMaterialReceiveDetailBo bo, PageQuery pageQuery);

    /**
     * 查询物料领用明细列表
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 结果列表
     */
    List<WmMaterialReceiveDetailVo> selectList(WmMaterialReceiveDetailBo bo);

    /**
     * 查询物料领用明细
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmMaterialReceiveDetailVo selectById(Long id);

    /**
     * 新增物料领用明细
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmMaterialReceiveDetailBo bo);

    /**
     * 修改物料领用明细
     *
     * @param bo 物料领用明细业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmMaterialReceiveDetailBo bo);

    /**
     * 删除物料领用明细
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
