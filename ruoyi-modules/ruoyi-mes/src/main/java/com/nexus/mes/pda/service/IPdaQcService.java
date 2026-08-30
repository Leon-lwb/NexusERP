package com.nexus.mes.pda.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaQcInspectBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaQcDetailVo;
import com.nexus.mes.pda.domain.vo.PdaQcItemVo;

/**
 * PDA 质量三检作业接口 (M3 批次3; 契约见 nexus-pda doc/plans/2026-08-30-M3-执行计划.md)
 * <p>
 * 三检同构: todo(待检 PENDING+INSPECTING) / detail / inspect(委托既有 qc 服务 inspect(),
 * CAS 推进 + 不合格自动 NCR + afterCommit MQ 回传; 照片 ossId 串同事务补写)。
 */
public interface IPdaQcService {

    TableDataInfo<PdaQcItemVo> iqcTodo(String keyword, PageQuery pageQuery);

    PdaQcDetailVo iqcDetail(Long iqcId);

    MesActionResultVo iqcInspect(Long iqcId, PdaQcInspectBo bo);

    TableDataInfo<PdaQcItemVo> ipqcTodo(String keyword, PageQuery pageQuery);

    PdaQcDetailVo ipqcDetail(Long ipqcId);

    MesActionResultVo ipqcInspect(Long ipqcId, PdaQcInspectBo bo);

    TableDataInfo<PdaQcItemVo> oqcTodo(String keyword, PageQuery pageQuery);

    PdaQcDetailVo oqcDetail(Long oqcId);

    MesActionResultVo oqcInspect(Long oqcId, PdaQcInspectBo bo);
}
