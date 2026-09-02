package com.nexus.mes.eq.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.vo.EqHealthRecordVo;

import java.util.List;

/**
 * 设备健康评分 Service 接口（预测性维护）
 *
 * @author nexus
 */
public interface IEqHealthService {

    /**
     * 分页查询最新健康评分(每台设备取最近一条, 按评分升序 = 最差优先)
     *
     * @param equipmentName 设备名称(模糊)
     * @param grade         等级过滤
     * @param pageQuery     分页参数
     * @return 健康评分分页列表
     */
    TableDataInfo<EqHealthRecordVo> selectLatestPage(String equipmentName, String grade, PageQuery pageQuery);

    /**
     * 查询设备评分历史趋势
     *
     * @param equipmentId 设备ID
     * @param limitDays   最近天数
     * @return 评分记录列表(按日期升序)
     */
    List<EqHealthRecordVo> selectTrend(Long equipmentId, int limitDays);

    /**
     * 评估全部关联 IoT 的设备健康(定时任务入口)
     *
     * @return 处理设备数
     */
    int evaluateAll();

    /**
     * 评估单台设备健康：IoT 24h 数据 EWMA 异常点 + 维修历史 → 评分落库；
     * LOW 级且评分下滑时触发 EQUIPMENT 安灯。
     *
     * @param equipment 设备(需已关联 iotDeviceId)
     * @return 评分记录(未落库前构造的实体)
     */
    com.nexus.mes.eq.domain.EqHealthRecord evaluateOne(com.nexus.mes.eq.domain.EqEquipment equipment);
}
