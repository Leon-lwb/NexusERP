package com.nexus.core.so.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.so.domain.SoSalesOrderLine;
import com.nexus.core.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.core.so.domain.SoSalesOrderHeader;
import com.nexus.core.so.service.ISoSalesOrderHeaderService;

/**
 * 销售订单Service业务层处理
 *
 * @author nexus
 * @date 2025-10-22
 */
@Service
public class SoSalesOrderHeaderServiceImpl extends ServiceImpl<SoSalesOrderHeaderMapper, SoSalesOrderHeader> implements ISoSalesOrderHeaderService {

    @Autowired
    private SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;

    /**
     * 查询销售订单
     *
     * @param soId 销售订单主键
     * @return 销售订单
     */
    @Override
    public SoSalesOrderHeader selectSoSalesOrderHeaderBySoId(Long soId) {
        return soSalesOrderHeaderMapper.selectSoSalesOrderHeaderBySoId(soId);
    }

    /**
     * 查询销售订单列表
     *
     * @param soSalesOrderHeader 销售订单
     * @return 销售订单
     */
    @Override
    public List<SoSalesOrderHeader> selectSoSalesOrderHeaderList(SoSalesOrderHeader soSalesOrderHeader) {
        return soSalesOrderHeaderMapper.selectSoSalesOrderHeaderList(soSalesOrderHeader);
    }

    /**
     * 新增销售订单
     *
     * @param soSalesOrderHeader 销售订单
     * @return 结果
     */
    @Transactional
    @Override
    public int insertSoSalesOrderHeader(SoSalesOrderHeader soSalesOrderHeader) {
        soSalesOrderHeader.setCreateTime(DateUtils.getNowDate());
        int rows = soSalesOrderHeaderMapper.insertSoSalesOrderHeader(soSalesOrderHeader);
        insertSoSalesOrderLine(soSalesOrderHeader);
        return rows;
    }

    /**
     * 修改销售订单
     *
     * @param soSalesOrderHeader 销售订单
     * @return 结果
     */
    @Transactional
    @Override
    public int updateSoSalesOrderHeader(SoSalesOrderHeader soSalesOrderHeader) {
        soSalesOrderHeader.setUpdateTime(DateUtils.getNowDate());
        soSalesOrderHeaderMapper.deleteSoSalesOrderLineBySoId(soSalesOrderHeader.getSoId());
        insertSoSalesOrderLine(soSalesOrderHeader);
        return soSalesOrderHeaderMapper.updateSoSalesOrderHeader(soSalesOrderHeader);
    }

    /**
     * 批量删除销售订单
     *
     * @param soIds 需要删除的销售订单主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteSoSalesOrderHeaderBySoIds(Long[] soIds) {
        soSalesOrderHeaderMapper.deleteSoSalesOrderLineBySoIds(soIds);
        return soSalesOrderHeaderMapper.deleteSoSalesOrderHeaderBySoIds(soIds);
    }

    /**
     * 删除销售订单信息
     *
     * @param soId 销售订单主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteSoSalesOrderHeaderBySoId(Long soId) {
        soSalesOrderHeaderMapper.deleteSoSalesOrderLineBySoId(soId);
        return soSalesOrderHeaderMapper.deleteSoSalesOrderHeaderBySoId(soId);
    }

    /**
     * 新增销售订单行信息
     *
     * @param soSalesOrderHeader 销售订单对象
     */
    public void insertSoSalesOrderLine(SoSalesOrderHeader soSalesOrderHeader) {
        List<SoSalesOrderLine> soSalesOrderLineList = soSalesOrderHeader.getSoSalesOrderLineList();
        Long soId = soSalesOrderHeader.getSoId();
        if (StringUtils.isNotNull(soSalesOrderLineList)) {
            List<SoSalesOrderLine> list = new ArrayList<SoSalesOrderLine>();
            for (SoSalesOrderLine soSalesOrderLine : soSalesOrderLineList) {
                soSalesOrderLine.setSoId(soId);
                list.add(soSalesOrderLine);
            }
            if (list.size() > 0) {
                soSalesOrderHeaderMapper.batchSoSalesOrderLine(list);
            }
        }
    }
}
