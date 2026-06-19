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
     * 分页查询数据点列表
     */
    TableDataInfo<SpcDataPointVo> selectPageList(SpcDataPointBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取数据点详情
     */
    SpcDataPointVo selectById(Long dataId);

    /**
     * 按控制图查询全部数据点(按sample_group升序, 供前端绘图)
     */
    List<SpcDataPointVo> listByChartId(Long chartId);
}
