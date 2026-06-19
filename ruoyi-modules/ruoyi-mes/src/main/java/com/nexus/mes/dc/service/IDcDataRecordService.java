package com.nexus.mes.dc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.dc.domain.bo.DcDataRecordBo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;

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
     */
    TableDataInfo<DcDataRecordVo> selectPageList(DcDataRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取记录详情
     */
    DcDataRecordVo selectById(Long recordId);

    /**
     * 手动录入采集数据(source=MANUAL, collectBy=当前用户)
     */
    DcDataRecordVo insertByBo(DcDataRecordBo bo);

    /**
     * 按采集点查询全部记录(按collect_time升序, 供SPC analyze用)
     */
    List<DcDataRecordVo> listByPointId(Long pointId);
}
