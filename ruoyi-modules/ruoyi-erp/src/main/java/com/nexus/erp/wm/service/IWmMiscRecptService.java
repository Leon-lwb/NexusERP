package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMiscRecptBo;
import com.nexus.erp.wm.domain.vo.WmMiscRecptVo;

import java.util.List;
/**
 * 杂项收货服务接口
 * <p>
 * 定义杂项收货的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IWmMiscRecptService {

    /**
     * 分页查询杂项收货列表
     *
     * @param bo 杂项收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMiscRecptVo> selectPageList(WmMiscRecptBo bo, PageQuery pageQuery);

    /**
     * 查询杂项收货列表
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 结果列表
     */
    List<WmMiscRecptVo> selectList(WmMiscRecptBo bo);

    /**
     * 查询杂项收货
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmMiscRecptVo selectById(Long id);

    /**
     * 新增杂项收货
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmMiscRecptBo bo);

    /**
     * 修改杂项收货
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmMiscRecptBo bo);

    /**
     * 删除杂项收货
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行杂项入库过账（增加库存）
     */
    void execute(Long recptId);
}
