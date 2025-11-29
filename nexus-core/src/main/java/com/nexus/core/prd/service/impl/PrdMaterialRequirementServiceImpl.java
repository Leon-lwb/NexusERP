package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.core.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.core.prd.domain.PrdMaterialRequirement;
import com.nexus.core.prd.service.IPrdMaterialRequirementService;

/**
 * 物料清单Service业务层处理
 *
 * @author nexus
 * @date 2025-11-21
 */
@Service
public class PrdMaterialRequirementServiceImpl extends ServiceImpl<PrdMaterialRequirementMapper, PrdMaterialRequirement> implements IPrdMaterialRequirementService {

    @Autowired
    private PrdMaterialRequirementMapper prdMaterialRequirementMapper;

    /**
     * 查询物料清单
     *
     * @param requirementId 物料清单主键
     * @return 物料清单
     */
    @Override
    public PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId) {
        return prdMaterialRequirementMapper.selectPrdMaterialRequirementByRequirementId(requirementId);
    }

    /**
     * 查询物料清单列表
     *
     * @param prdMaterialRequirement 物料清单
     * @return 物料清单
     */
    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement) {
        return prdMaterialRequirementMapper.selectPrdMaterialRequirementList(prdMaterialRequirement);
    }

    /**
     * 新增物料清单
     *
     * @param prdMaterialRequirement 物料清单
     * @return 结果
     */
        @Transactional
    @Override
    public int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
                prdMaterialRequirement.setCreateTime(DateUtils.getNowDate());
            int rows = prdMaterialRequirementMapper.insertPrdMaterialRequirement(prdMaterialRequirement);
            insertPrdMaterialRequirementDetail(prdMaterialRequirement);
            return rows;
    }

    /**
     * 修改物料清单
     *
     * @param prdMaterialRequirement 物料清单
     * @return 结果
     */
        @Transactional
    @Override
    public int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
                prdMaterialRequirement.setUpdateTime(DateUtils.getNowDate());
                prdMaterialRequirementMapper.deletePrdMaterialRequirementDetailByRequirementId(prdMaterialRequirement.getRequirementId());
            insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return prdMaterialRequirementMapper.updatePrdMaterialRequirement(prdMaterialRequirement);
    }

    /**
     * 批量删除物料清单
     *
     * @param requirementIds 需要删除的物料清单主键
     * @return 结果
     */
        @Transactional
    @Override
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds) {
                prdMaterialRequirementMapper.deletePrdMaterialRequirementDetailByRequirementIds(requirementIds);
        return prdMaterialRequirementMapper.deletePrdMaterialRequirementByRequirementIds(requirementIds);
    }

    /**
     * 删除物料清单信息
     *
     * @param requirementId 物料清单主键
     * @return 结果
     */
        @Transactional
    @Override
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId) {
                prdMaterialRequirementMapper.deletePrdMaterialRequirementDetailByRequirementId(requirementId);
        return prdMaterialRequirementMapper.deletePrdMaterialRequirementByRequirementId(requirementId);
    }

        /**
         * 新增物料需求单明细信息
         *
         * @param prdMaterialRequirement 物料清单对象
         */
        public void insertPrdMaterialRequirementDetail(PrdMaterialRequirement prdMaterialRequirement) {
            List<PrdMaterialRequirementDetail> prdMaterialRequirementDetailList = prdMaterialRequirement.getPrdMaterialRequirementDetailList();
            Long requirementId = prdMaterialRequirement.getRequirementId();
            if (StringUtils.isNotNull(prdMaterialRequirementDetailList))
            {
                List<PrdMaterialRequirementDetail> list = new ArrayList<PrdMaterialRequirementDetail>();
                for (PrdMaterialRequirementDetail prdMaterialRequirementDetail : prdMaterialRequirementDetailList)
                {
                    prdMaterialRequirementDetail.setRequirementId(requirementId);
                    list.add(prdMaterialRequirementDetail);
                }
                if (list.size() > 0)
                {
                        prdMaterialRequirementMapper.batchPrdMaterialRequirementDetail(list);
                }
            }
        }

    /**
     * 批量插入物料需求明细
     *
     * @param detailList 物料需求明细列表
     * @return 结果
     */
    @Transactional
    @Override
    public int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList) {
        int count = 0;
        for (PrdMaterialRequirementDetail detail : detailList) {
            count += prdMaterialRequirementMapper.insertPrdMaterialRequirementDetail(detail);
        }
        return count;
    }
}