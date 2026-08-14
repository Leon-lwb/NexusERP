package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;

import java.util.List;

/**
 * 到货通知单行Service接口
 *
 * @author nexus
 */
public interface IWmArrivalNoticeLineService {

    /**
     * 分页查询到货通知行列表
     *
     * @param bo 到货通知行业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmArrivalNoticeLineVo> selectPageList(WmArrivalNoticeLineBo bo, PageQuery pageQuery);

    /**
     * 查询到货通知行列表
     *
     * @param bo 到货通知行业务对象（BO）
     * @return 结果列表
     */
    List<WmArrivalNoticeLineVo> selectList(WmArrivalNoticeLineBo bo);

    /**
     * 查询到货通知行
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmArrivalNoticeLineVo selectById(Long id);

    /**
     * 新增到货通知行
     *
     * @param bo 到货通知行业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmArrivalNoticeLineBo bo);

    /**
     * 修改到货通知行
     *
     * @param bo 到货通知行业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmArrivalNoticeLineBo bo);

    /**
     * 删除到货通知行
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 按通知单ID和物料ID更新IQC结果
     *
     * @param noticeId   到货通知单ID
     * @param materialId 物料ID
     * @param iqcId      IQC检验单ID
     * @param iqcCode    IQC检验单号
     * @param iqcStatus  IQC检验状态
     * @return 影响行数
     */
    int updateIqcResult(Long noticeId, Long materialId, Long iqcId, String iqcCode, String iqcStatus);
}
