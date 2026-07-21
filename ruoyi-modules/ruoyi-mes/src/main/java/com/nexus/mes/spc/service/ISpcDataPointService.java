package com.nexus.mes.spc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.spc.domain.bo.SpcDataPointBo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;

import java.util.List;

/**
 * MES SPC数据点 Service 业务层接口
 * <p>
 * 数据点由 analyze 自动生成(组统计值 + OOC判定), 供前端绘图。
 *
 * @author Nexus
 */
public interface ISpcDataPointService {

    /**
     * 分页查询 SPC 数据点列表。
     *
     * @param bo        数据点查询条件（控制图/样本组/OOC 标志）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含数据点视图列表及分页元信息
     */
    TableDataInfo<SpcDataPointVo> selectPageList(SpcDataPointBo bo, PageQuery pageQuery);

    /**
     * 根据主键 ID 获取 SPC 数据点详情。
     *
     * @param dataId 数据点 ID
     * @return 数据点视图对象
     */
    SpcDataPointVo selectById(Long dataId);

    /**
     * 按控制图查询其全部数据点（按样本组号升序），供前端绘制控制图。
     *
     * @param chartId 控制图 ID
     * @return 该控制图下的数据点列表
     */
    List<SpcDataPointVo> listByChartId(Long chartId);
}
