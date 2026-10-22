package com.nexus.mes.dc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.dc.domain.bo.DcDataRecordBo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;

import java.util.Date;
import java.util.List;

/**
 * MES采集数据记录 Service 业务层接口
 * <p>
 * 来源: MANUAL(手动录入)/IoT(自动,预留)
 * SPC消费dc_data_record归档数据(按采集点聚合)
 *
 * @author Nexus
 */
public interface IDcDataRecordService {

    /** 来源: 手动 */
    String SOURCE_MANUAL = "MANUAL";
    /** 来源: IoT(预留) */
    String SOURCE_IOT = "IoT";

    /**
     * 分页查询采集数据记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<DcDataRecordVo> selectPageList(DcDataRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    DcDataRecordVo selectById(Long recordId);

    /**
     * 手动录入采集数据(source=MANUAL, collectBy=当前用户)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    DcDataRecordVo insertByBo(DcDataRecordBo bo);

    /**
     * 按采集点查询全部记录(按collect_time升序, 供SPC analyze用)
     *
     * @param pointId 采集点ID
     * @return 结果列表
     * @deprecated 全量拉取无上限, 大数据量下会拖垮 SPC 分析; 请改用 {@link #listByPointId(Long, Date, Date, Integer)}
     */
    @Deprecated
    List<DcDataRecordVo> listByPointId(Long pointId);

    /**
     * 按采集点 + 时间窗 + 条数上限查询记录(始终按 collect_time 升序返回)
     *
     * <p>指定 limit 时先按 collect_time DESC LIMIT n 取最近 n 条, 再在内存反转为升序, 避免"取最早 n 条"而不是"最近 n 条"。
     *
     * @param pointId 采集点ID
     * @param startTime 起始时间(含), null 表示不限
     * @param endTime 结束时间(含), null 表示不限
     * @param limit 最大条数, null 或 <=0 表示不限
     * @return 结果列表(collect_time 升序)
     */
    List<DcDataRecordVo> listByPointId(Long pointId, Date startTime, Date endTime, Integer limit);
}
