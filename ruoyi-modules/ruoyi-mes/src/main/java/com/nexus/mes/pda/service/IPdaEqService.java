package com.nexus.mes.pda.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqInspectionVo;
import com.nexus.mes.pda.domain.vo.PdaEqItemVo;

/**
 * PDA 设备点检作业服务 (编排既有设备服务)
 */
public interface IPdaEqService {

    /** 设备列表 (按编码/名称模糊, 供扫码定位与巡检浏览) */
    TableDataInfo<PdaEqItemVo> todo(String keyword, PageQuery pageQuery);

    /** 点检详情: 点检项模板 + 上次结果 */
    PdaEqInspectionVo detail(Long equipmentId);

    /** 提交点检结果: 委托 submitInspection(不合格自动 FAULT+安灯), 并写点检历史 */
    MesActionResultVo submit(Long equipmentId, InspectionResultBo bo);
}
