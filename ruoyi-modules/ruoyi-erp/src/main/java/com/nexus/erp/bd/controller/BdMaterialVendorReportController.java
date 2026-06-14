package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.vo.PriceComparisonVo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdMaterialVendorMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物料-供应商报表 Controller
 * <p>
 * 提供价格对比、价格排名等报表查询功能。
 * </p>
 *
 * @author nexus
 * @date 2026-06-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/materialVendor/report")
public class BdMaterialVendorReportController extends BaseController {

    private final BdMaterialVendorMapper materialVendorMapper;
    private final BdMaterialMapper materialMapper;

    /**
     * 按物料查询供应商价格对比
     * <p>权限标识：{@code bd:materialVendor:list}</p>
     *
     * @param materialId 物料ID
     * @return 供应商价格对比列表（按价格升序）
     */
    @SaCheckPermission("bd:materialVendor:list")
    @GetMapping("/priceComparison")
    public R<List<PriceComparisonVo>> priceComparison(@RequestParam Long materialId) {
        // 查询物料信息
        BdMaterial material = materialMapper.selectById(materialId);
        String materialCode = material != null ? material.getMaterialCode() : null;
        String materialName = material != null ? material.getMaterialName() : null;

        List<BdMaterialVendor> list = materialVendorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BdMaterialVendor>()
                .eq(BdMaterialVendor::getMaterialId, materialId)
                .eq(BdMaterialVendor::getStatus, 1L)
                .orderByAsc(BdMaterialVendor::getPrice)
        );
        List<PriceComparisonVo> voList = list.stream().map(entity -> {
            PriceComparisonVo vo = convertToPriceVo(entity);
            vo.setMaterialCode(materialCode);
            vo.setMaterialName(materialName);
            return vo;
        }).collect(Collectors.toList());
        return R.ok(voList);
    }

    /**
     * 按供应商查询物料报价列表
     * <p>权限标识：{@code bd:materialVendor:list}</p>
     *
     * @param vendorId 供应商ID
     * @return 物料报价列表
     */
    @SaCheckPermission("bd:materialVendor:list")
    @GetMapping("/vendorMaterials")
    public R<List<PriceComparisonVo>> vendorMaterials(@RequestParam Long vendorId) {
        List<BdMaterialVendor> list = materialVendorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BdMaterialVendor>()
                .eq(BdMaterialVendor::getVendorId, vendorId)
                .eq(BdMaterialVendor::getStatus, 1L)
                .orderByAsc(BdMaterialVendor::getMaterialId)
        );
        // 批量查询物料信息
        List<Long> materialIds = list.stream()
            .map(BdMaterialVendor::getMaterialId)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, BdMaterial> materialMap = materialMapper.selectBatchIds(materialIds).stream()
            .collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m));

        List<PriceComparisonVo> voList = list.stream().map(entity -> {
            PriceComparisonVo vo = convertToPriceVo(entity);
            BdMaterial material = materialMap.get(entity.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
            return vo;
        }).collect(Collectors.toList());
        return R.ok(voList);
    }

    /**
     * 物料价格排名（Top N 供应商）
     * <p>权限标识：{@code bd:materialVendor:list}</p>
     *
     * @param materialId 物料ID
     * @param limit      返回数量限制（默认10）
     * @return 价格排名列表
     */
    @SaCheckPermission("bd:materialVendor:list")
    @GetMapping("/priceRanking")
    public R<List<PriceComparisonVo>> priceRanking(
        @RequestParam Long materialId,
        @RequestParam(defaultValue = "10") Integer limit) {
        // 查询物料信息
        BdMaterial material = materialMapper.selectById(materialId);
        String materialCode = material != null ? material.getMaterialCode() : null;
        String materialName = material != null ? material.getMaterialName() : null;

        List<BdMaterialVendor> list = materialVendorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BdMaterialVendor>()
                .eq(BdMaterialVendor::getMaterialId, materialId)
                .eq(BdMaterialVendor::getStatus, 1L)
                .orderByAsc(BdMaterialVendor::getPrice)
                .last("LIMIT " + limit)
        );
        List<PriceComparisonVo> voList = list.stream().map(entity -> {
            PriceComparisonVo vo = convertToPriceVo(entity);
            vo.setMaterialCode(materialCode);
            vo.setMaterialName(materialName);
            return vo;
        }).collect(Collectors.toList());
        return R.ok(voList);
    }

    /**
     * 将实体转换为价格对比VO
     */
    private PriceComparisonVo convertToPriceVo(BdMaterialVendor entity) {
        PriceComparisonVo vo = new PriceComparisonVo();
        vo.setMaterialId(entity.getMaterialId());
        vo.setVendorId(entity.getVendorId());
        vo.setVendorCode(entity.getVendorCode());
        vo.setVendorName(entity.getVendorName());
        vo.setPrice(entity.getPrice());
        vo.setCurrency(entity.getCurrency());
        vo.setLeadTime(entity.getLeadTime());
        vo.setMinOrderQty(entity.getMinOrderQty());
        vo.setIsDefault(entity.getIsDefault());
        vo.setPriority(entity.getPriority());
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setExpiryDate(entity.getExpiryDate());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
