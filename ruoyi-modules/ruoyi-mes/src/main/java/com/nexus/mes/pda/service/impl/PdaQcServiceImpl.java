package com.nexus.mes.pda.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaQcInspectBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaQcDetailVo;
import com.nexus.mes.pda.domain.vo.PdaQcItemVo;
import com.nexus.mes.pda.service.IPdaQcService;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import com.nexus.mes.qc.service.IQcIqcService;
import com.nexus.mes.qc.service.IQcIpqcService;
import com.nexus.mes.qc.service.IQcOqcService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * PDA 质量三检作业实现
 * <p>
 * inspect 委托既有 qc 服务(状态 CAS/不合格自动 NCR/MQ 回传 ERP), 照片 ossId 串在
 * 同事务内补写 (REQUIRED 传播合并为一个事务, afterCommit 回传在共同提交后触发)。
 * 已检(COMPLETED)单重复提交由既有服务抛错拒绝, 幂等由 @PdaIdempotent 覆盖。
 */
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaQcServiceImpl implements IPdaQcService {

    /** 待检状态集 (与 qc 服务状态机一致) */
    private static final List<String> TODO_STATUS = List.of("PENDING", "INSPECTING");

    private final QcIqcMapper iqcMapper;
    private final QcIpqcMapper ipqcMapper;
    private final QcOqcMapper oqcMapper;
    private final IQcIqcService iqcService;
    private final IQcIpqcService ipqcService;
    private final IQcOqcService oqcService;

    // ==================== IQC ====================

    @Override
    public TableDataInfo<PdaQcItemVo> iqcTodo(String keyword, PageQuery pageQuery) {
        Page<QcIqc> page = iqcMapper.selectPage(pageQuery.build(),
            Wrappers.<QcIqc>lambdaQuery()
                .in(QcIqc::getStatus, TODO_STATUS)
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(QcIqc::getIqcNo, keyword)
                    .or()
                    .like(QcIqc::getMaterialCode, keyword))
                .orderByDesc(QcIqc::getCreateTime));
        List<PdaQcItemVo> items = new ArrayList<>(page.getRecords().size());
        for (QcIqc q : page.getRecords()) {
            PdaQcItemVo vo = new PdaQcItemVo();
            vo.setId(q.getIqcId());
            vo.setCode(q.getIqcNo());
            vo.setMaterialCode(q.getMaterialCode());
            vo.setMaterialName(q.getMaterialName());
            vo.setSubInfo(q.getVendorName());
            vo.setQuantity(q.getQuantity());
            vo.setSampleQty(q.getSampleQty());
            vo.setStatus(q.getStatus());
            items.add(vo);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaQcDetailVo iqcDetail(Long iqcId) {
        QcIqc q = iqcMapper.selectById(iqcId);
        if (q == null) {
            throw new ServiceException("IQC检验单不存在");
        }
        PdaQcDetailVo vo = baseDetail("iqc", q.getIqcId(), q.getIqcNo(), q.getStatus(),
            q.getInspectResult(), q.getMaterialCode(), q.getMaterialName(),
            q.getDefectInfo(), q.getInspectorName(), q.getInspectTime(), q.getRemark(), q.getPhotoOssIds());
        vo.setVendorName(q.getVendorName());
        vo.setSampleQty(q.getSampleQty());
        vo.setReceiveId(q.getReceiveId());
        vo.setQuantity(q.getQuantity());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo iqcInspect(Long iqcId, PdaQcInspectBo bo) {
        iqcService.inspect(iqcId, bo.getInspectResult(), bo.getDefectInfo(), bo.getSampleQty(), bo.getRemark());
        if (StringUtils.isNotBlank(bo.getPhotoOssIds())) {
            iqcMapper.update(null, Wrappers.<QcIqc>lambdaUpdate()
                .set(QcIqc::getPhotoOssIds, bo.getPhotoOssIds())
                .eq(QcIqc::getIqcId, iqcId));
        }
        return MesActionResultVo.ok("IQC检验已提交");
    }

    // ==================== IPQC ====================

    @Override
    public TableDataInfo<PdaQcItemVo> ipqcTodo(String keyword, PageQuery pageQuery) {
        Page<QcIpqc> page = ipqcMapper.selectPage(pageQuery.build(),
            Wrappers.<QcIpqc>lambdaQuery()
                .in(QcIpqc::getStatus, TODO_STATUS)
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(QcIpqc::getIpqcNo, keyword)
                    .or()
                    .like(QcIpqc::getMaterialCode, keyword)
                    .or()
                    .like(QcIpqc::getWoNo, keyword))
                .orderByDesc(QcIpqc::getCreateTime));
        List<PdaQcItemVo> items = new ArrayList<>(page.getRecords().size());
        for (QcIpqc q : page.getRecords()) {
            PdaQcItemVo vo = new PdaQcItemVo();
            vo.setId(q.getIpqcId());
            vo.setCode(q.getIpqcNo());
            vo.setMaterialCode(q.getMaterialCode());
            vo.setMaterialName(q.getMaterialName());
            vo.setSubInfo((q.getWoNo() != null ? q.getWoNo() : "")
                + (q.getProcessName() != null ? " · " + q.getProcessName() : ""));
            vo.setSampleQty(q.getSampleQty());
            vo.setStatus(q.getStatus());
            items.add(vo);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaQcDetailVo ipqcDetail(Long ipqcId) {
        QcIpqc q = ipqcMapper.selectById(ipqcId);
        if (q == null) {
            throw new ServiceException("IPQC检验单不存在");
        }
        PdaQcDetailVo vo = baseDetail("ipqc", q.getIpqcId(), q.getIpqcNo(), q.getStatus(),
            q.getInspectResult(), q.getMaterialCode(), q.getMaterialName(),
            q.getDefectInfo(), q.getInspectorName(), q.getInspectTime(), q.getRemark(), q.getPhotoOssIds());
        vo.setWoNo(q.getWoNo());
        vo.setSampleQty(q.getSampleQty());
        vo.setProcessName(q.getProcessName());
        vo.setDefectQty(q.getDefectQty());
        vo.setDefectRate(q.getDefectRate());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo ipqcInspect(Long ipqcId, PdaQcInspectBo bo) {
        ipqcService.inspect(ipqcId, bo.getInspectResult(), bo.getDefectInfo(), bo.getSampleQty(),
            bo.getDefectQty(), bo.getRemark());
        if (StringUtils.isNotBlank(bo.getPhotoOssIds())) {
            ipqcMapper.update(null, Wrappers.<QcIpqc>lambdaUpdate()
                .set(QcIpqc::getPhotoOssIds, bo.getPhotoOssIds())
                .eq(QcIpqc::getIpqcId, ipqcId));
        }
        return MesActionResultVo.ok("IPQC检验已提交");
    }

    // ==================== OQC ====================

    @Override
    public TableDataInfo<PdaQcItemVo> oqcTodo(String keyword, PageQuery pageQuery) {
        Page<QcOqc> page = oqcMapper.selectPage(pageQuery.build(),
            Wrappers.<QcOqc>lambdaQuery()
                .in(QcOqc::getStatus, TODO_STATUS)
                .and(StringUtils.isNotBlank(keyword), w -> w
                    .like(QcOqc::getOqcNo, keyword)
                    .or()
                    .like(QcOqc::getMaterialCode, keyword))
                .orderByDesc(QcOqc::getCreateTime));
        List<PdaQcItemVo> items = new ArrayList<>(page.getRecords().size());
        for (QcOqc q : page.getRecords()) {
            PdaQcItemVo vo = new PdaQcItemVo();
            vo.setId(q.getOqcId());
            vo.setCode(q.getOqcNo());
            vo.setMaterialCode(q.getMaterialCode());
            vo.setMaterialName(q.getMaterialName());
            vo.setSubInfo(q.getSoId() != null ? "销售订单 " + q.getSoId() : null);
            vo.setQuantity(q.getQuantity());
            vo.setSampleQty(q.getSampleQty());
            vo.setStatus(q.getStatus());
            items.add(vo);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaQcDetailVo oqcDetail(Long oqcId) {
        QcOqc q = oqcMapper.selectById(oqcId);
        if (q == null) {
            throw new ServiceException("OQC检验单不存在");
        }
        PdaQcDetailVo vo = baseDetail("oqc", q.getOqcId(), q.getOqcNo(), q.getStatus(),
            q.getInspectResult(), q.getMaterialCode(), q.getMaterialName(),
            q.getDefectInfo(), q.getInspectorName(), q.getInspectTime(), q.getRemark(), q.getPhotoOssIds());
        vo.setSoId(q.getSoId());
        vo.setSampleQty(q.getSampleQty());
        vo.setQuantity(q.getQuantity());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo oqcInspect(Long oqcId, PdaQcInspectBo bo) {
        oqcService.inspect(oqcId, bo.getInspectResult(), bo.getDefectInfo(), bo.getSampleQty(), bo.getRemark());
        if (StringUtils.isNotBlank(bo.getPhotoOssIds())) {
            oqcMapper.update(null, Wrappers.<QcOqc>lambdaUpdate()
                .set(QcOqc::getPhotoOssIds, bo.getPhotoOssIds())
                .eq(QcOqc::getOqcId, oqcId));
        }
        return MesActionResultVo.ok("OQC检验已提交");
    }

    // ==================== 通用 ====================

    private PdaQcDetailVo baseDetail(String domain, Long id, String code, String status, String inspectResult,
                                     String materialCode, String materialName, String defectInfo,
                                     String inspectorName, java.util.Date inspectTime,
                                     String remark, String photoOssIds) {
        PdaQcDetailVo vo = new PdaQcDetailVo();
        vo.setDomain(domain);
        vo.setId(id);
        vo.setCode(code);
        vo.setStatus(status);
        vo.setInspectResult(inspectResult);
        vo.setMaterialCode(materialCode);
        vo.setMaterialName(materialName);
        vo.setDefectInfo(defectInfo);
        vo.setInspectorName(inspectorName);
        vo.setInspectTime(inspectTime != null ? DateUtil.formatDateTime(inspectTime) : null);
        vo.setRemark(remark);
        vo.setPhotoOssIds(photoOssIds);
        return vo;
    }
}
