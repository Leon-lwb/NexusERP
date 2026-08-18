package com.nexus.mes.flash.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.domain.vo.FlashRecordVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;

import java.util.List;
import java.util.Map;

/**
 * MES固件刷写记录 Service 接口
 * <p>M2 刷写作业: 补录(insertByBo)+作业确认(confirmFlash)+追溯查询。
 * 同一 SN 允许多条记录(FAIL 重刷保留历史, 判定取最新一条);
 * M1 固件删除/归档依赖 mes_flash_record 校验, 故 AUTO 记录禁止删除。</p>
 *
 * @author Nexus
 */
public interface IFlashRecordService {

    /** 审批业务服务名(与 @Service 注解及 wf_custom_form.business_service 一致) */
    String SERVICE_NAME = "mesFlashRecordService";

    /**
     * 分页查询刷写记录(多条件: 记录号/SN/工单/设备/固件/结果/模式/操作人/时间区间)
     */
    TableDataInfo<FlashRecordVo> selectPageList(FlashRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询刷写记录详情
     */
    FlashRecordVo selectById(Long flashRecordId);

    /**
     * 补录刷写记录(管理员纠错/手工补录, 宽松校验, 自动编码+冗余回填)
     */
    Boolean insertByBo(FlashRecordBo bo);

    /**
     * 修改刷写记录(纠错: 结果/失败原因/设备/时间等, 记录号与SN不可变)
     */
    Boolean updateByBo(FlashRecordBo bo);

    /**
     * 批量删除刷写记录(仅 MANUAL 补录记录可删, AUTO 作业记录禁止删除)
     */
    Boolean deleteByIds(Long[] flashRecordIds);

    /**
     * 刷写作业确认: 严格校验 SN 存在且非报废、固件存在且已发布(RELEASED)、设备存在,
     * 自动编码 + 冗余回填 + 落一条记录(FAIL 重刷自动保留历史)
     */
    Boolean confirmFlash(FlashRecordBo bo);

    /**
     * SN 远程搜索(录入/确认弹窗下拉): 按 SN 编码模糊查询 pro_sn, 供选择回填
     */
    List<ProSnVo> listSnOptions(String snCode);

    /**
     * AUTO 设备批量上报刷写结果(开放接口, 宽松模式): 逐条校验落库,
     * 返回 {success: 成功条数, failed: [{snCode, reason}]}
     */
    Map<String, Object> reportFlashBatch(List<FlashRecordBo> list);

    /**
     * 工位编排: 查 SN 工单刷写工序待刷清单(每绑定项含最新刷写结果, 产线工位拉取)
     */
    List<Map<String, Object>> selectStationPending(String snCode);

    /**
     * 待刷写任务清单(登录态, 刷写作业页用): 按工单/流转卡聚合在制SN × 工单刷写工序绑定,
     * 每行含 snId/snCode/woNo/cardId/sequenceNum/processName/flashTypeCode/contentRefId/
     * contentVersion/contentChecksum/latestResult/flashTime/pendingFlag。
     * <p>pendingFlag: 该 SN 该绑定项最新刷写结果非 PASS 视为待刷(未刷/FAIL 重刷)。</p>
     */
    List<Map<String, Object>> selectPendingTasks(Long mesWoId, Long cardId, String woNo);
}