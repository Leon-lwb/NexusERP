package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.so.domain.SoPriceList;
import com.nexus.erp.so.domain.SoPriceListLine;
import com.nexus.erp.so.mapper.SoPriceListLineMapper;
import com.nexus.erp.so.mapper.SoPriceListMapper;
import com.nexus.erp.so.service.IPricingEngineService;
import org.springframework.stereotype.Service;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 价格引擎Service实现类，按优先级解析销售价格
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PricingEngineServiceImpl implements IPricingEngineService {

    private final SoPriceListMapper soPriceListMapper;
    private final SoPriceListLineMapper soPriceListLineMapper;
    private final BdMaterialMapper bdMaterialMapper;

    /**
     * 解析销售价格，按优先级匹配：客户专属价 > 期间促销价 > 标准基价 > 物料参考价
     *
     * @param clientId  客户ID
     * @param materialId 物料ID
     * @param qty       数量
     * @param orderDate 订单日期
     * @return 匹配到的单价
     */
    @Override
    public BigDecimal resolvePrice(Long clientId, Long materialId, BigDecimal qty, LocalDate orderDate) {
        BigDecimal price = tryCustomerPrice(clientId, materialId, qty, orderDate);
        if (price != null) {
            return price;
        }
        price = tryPromotionPrice(materialId, qty, orderDate);
        if (price != null) {
            return price;
        }
        price = tryBasePrice(materialId, qty);
        if (price != null) {
            return price;
        }
        return fallbackReferencePrice(materialId);
    }

    /**
     * 尝试匹配客户专属价
     *
     * @param clientId   客户ID
     * @param materialId 物料ID
     * @param qty        数量
     * @param orderDate  订单日期
     * @return 匹配到的单价，未匹配返回null
     */
    private BigDecimal tryCustomerPrice(Long clientId, Long materialId, BigDecimal qty, LocalDate orderDate) {
        if (clientId == null) {
            return null;
        }
        LambdaQueryWrapper<SoPriceList> headerWrapper = Wrappers.<SoPriceList>lambdaQuery()
            .eq(SoPriceList::getListType, "CUSTOMER")
            .eq(SoPriceList::getClientId, clientId)
            .eq(SoPriceList::getStatus, "ACTIVE")
            .le(orderDate != null, SoPriceList::getValidFrom, orderDate)
            .ge(orderDate != null, SoPriceList::getValidTo, orderDate);
        List<SoPriceList> priceLists = soPriceListMapper.selectList(headerWrapper);
        for (SoPriceList pl : priceLists) {
            BigDecimal price = findLinePrice(pl.getPriceListId(), materialId, qty);
            if (price != null) {
                log.debug("命中客户专属价: priceListId={}, clientId={}, materialId={}, price={}",
                    pl.getPriceListId(), clientId, materialId, price);
                return price;
            }
        }
        return null;
    }

    /**
     * 尝试匹配期间促销价
     *
     * @param materialId 物料ID
     * @param qty        数量
     * @param orderDate  订单日期
     * @return 匹配到的单价，未匹配返回null
     */
    private BigDecimal tryPromotionPrice(Long materialId, BigDecimal qty, LocalDate orderDate) {
        LambdaQueryWrapper<SoPriceList> headerWrapper = Wrappers.<SoPriceList>lambdaQuery()
            .eq(SoPriceList::getListType, "PROMOTION")
            .eq(SoPriceList::getStatus, "ACTIVE")
            .le(orderDate != null, SoPriceList::getValidFrom, orderDate)
            .ge(orderDate != null, SoPriceList::getValidTo, orderDate);
        List<SoPriceList> priceLists = soPriceListMapper.selectList(headerWrapper);
        for (SoPriceList pl : priceLists) {
            BigDecimal price = findLinePrice(pl.getPriceListId(), materialId, qty);
            if (price != null) {
                log.debug("命中期间促销价: priceListId={}, materialId={}, price={}",
                    pl.getPriceListId(), materialId, price);
                return price;
            }
        }
        return null;
    }

    /**
     * 尝试匹配标准基价
     *
     * @param materialId 物料ID
     * @param qty        数量
     * @return 匹配到的单价，未匹配返回null
     */
    private BigDecimal tryBasePrice(Long materialId, BigDecimal qty) {
        LambdaQueryWrapper<SoPriceList> headerWrapper = Wrappers.<SoPriceList>lambdaQuery()
            .eq(SoPriceList::getListType, "BASE")
            .eq(SoPriceList::getStatus, "ACTIVE");
        List<SoPriceList> priceLists = soPriceListMapper.selectList(headerWrapper);
        for (SoPriceList pl : priceLists) {
            BigDecimal price = findLinePrice(pl.getPriceListId(), materialId, qty);
            if (price != null) {
                log.debug("命中标准基价: priceListId={}, materialId={}, price={}",
                    pl.getPriceListId(), materialId, price);
                return price;
            }
        }
        return null;
    }

    /**
     * 兜底使用物料参考价
     *
     * @param materialId 物料ID
     * @return 物料参考价，无则返回0
     */
    private BigDecimal fallbackReferencePrice(Long materialId) {
        BdMaterial material = bdMaterialMapper.selectById(materialId);
        if (material != null && material.getReferencePrice() != null) {
            log.debug("兜底使用物料参考价: materialId={}, referencePrice={}", materialId, material.getReferencePrice());
            return material.getReferencePrice();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 在指定价格表中查找匹配物料和数量的行价格，并应用折扣率
     *
     * @param priceListId 价格表ID
     * @param materialId  物料ID
     * @param qty         数量
     * @return 计算后的单价，未匹配返回null
     */
    private BigDecimal findLinePrice(Long priceListId, Long materialId, BigDecimal qty) {
        LambdaQueryWrapper<SoPriceListLine> lineWrapper = Wrappers.<SoPriceListLine>lambdaQuery()
            .eq(SoPriceListLine::getPriceListId, priceListId)
            .eq(SoPriceListLine::getMaterialId, materialId)
            .le(qty != null, SoPriceListLine::getMinQty, qty)
            .ge(qty != null, SoPriceListLine::getMaxQty, qty);
        SoPriceListLine line = soPriceListLineMapper.selectOne(lineWrapper);
        if (line == null) {
            return null;
        }
        BigDecimal discountRate = line.getDiscountRate() != null ? line.getDiscountRate() : BigDecimal.valueOf(100);
        return line.getUnitPrice().multiply(discountRate)
            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}
