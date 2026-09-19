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

    /** 百分数换算基数（折扣率以「百分数」存储：5 表示让利 5%） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
            // valid_to 为空表示长期有效，原实现用 ge 会让「未填截止日」的价格表永不命中
            .and(orderDate != null, w -> w.isNull(SoPriceList::getValidTo)
                .or().ge(SoPriceList::getValidTo, orderDate))
            // priority 越大越优先，保证多张价格表命中时的结果稳定（原实现无排序，取首条由 DB 顺序决定）
            .orderByDesc(SoPriceList::getPriority);
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
            .and(orderDate != null, w -> w.isNull(SoPriceList::getValidTo)
                .or().ge(SoPriceList::getValidTo, orderDate))
            .orderByDesc(SoPriceList::getPriority);
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
            .eq(SoPriceList::getStatus, "ACTIVE")
            .orderByDesc(SoPriceList::getPriority);
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
     * 在指定价格表中查找匹配物料和数量的行价格，并应用折扣率。
     * <p>命中规则：{@code min_qty <= qty} 且（{@code max_qty} 为空或 {@code max_qty >= qty}）。
     * 多条命中时取数量区间最窄的一条（{@code min_qty} 最大）—— 阶梯价的常见语义。</p>
     * <p>修复记录：原实现用 {@code selectOne}，在 {@code so_price_list_line} 缺少
     * {@code (price_list_id, material_id, min_qty)} 唯一约束的情况下，多行命中会直接抛
     * {@code TooManyResultsException}；且 {@code max_qty} 的 DDL 默认值是 NULL，
     * 原 {@code ge(max_qty, qty)} 让「未填上限」的行恒不命中（NULL 参与比较恒为 NULL）。</p>
     *
     * @param priceListId 价格表ID
     * @param materialId  物料ID
     * @param qty         数量
     * @return 计算后的单价，未匹配返回null
     */
    private BigDecimal findLinePrice(Long priceListId, Long materialId, BigDecimal qty) {
        List<SoPriceListLine> candidates = soPriceListLineMapper.selectList(
            Wrappers.<SoPriceListLine>lambdaQuery()
                .eq(SoPriceListLine::getPriceListId, priceListId)
                .eq(SoPriceListLine::getMaterialId, materialId)
                .le(qty != null, SoPriceListLine::getMinQty, qty)
                .and(qty != null, w -> w.isNull(SoPriceListLine::getMaxQty)
                    .or().ge(SoPriceListLine::getMaxQty, qty))
                .orderByDesc(SoPriceListLine::getMinQty));
        if (candidates.isEmpty()) {
            return null;
        }
        SoPriceListLine line = candidates.get(0);
        return applyDiscount(line.getUnitPrice(), line.getDiscountRate());
    }

    /**
     * 应用折扣率得到成交单价。
     *
     * <p><b>语义（决策 D2）：折扣率是「折扣百分比」</b> —— {@code 5} 表示让利 5%、实付 95%。
     * 因此 {@code 成交价 = 单价 × (1 − 折扣率 / 100)}。</p>
     *
     * <p>修复记录：原实现是 {@code 单价 × 折扣率 / 100}，方向完全相反 ——
     * 折扣率 5 会算出原价的 5%（等于让利 95%），而 DDL 默认值 100 会直接算出 0 元。
     * 订单侧（{@code orderheader/form.vue} 的 calcLine/calcHeader）一直是「折扣百分比」语义，
     * 本次把价目表侧对齐到同一口径。</p>
     *
     * @param unitPrice    原单价
     * @param discountRate 折扣率（百分数，0 表示不让利）
     * @return 成交单价；原单价为空时返回 null
     */
    private BigDecimal applyDiscount(BigDecimal unitPrice, BigDecimal discountRate) {
        if (unitPrice == null) {
            return null;
        }
        BigDecimal rate = discountRate == null ? BigDecimal.ZERO : discountRate;
        // 防御：折扣率越界按边界裁剪，避免出现负价或倒贴
        if (rate.signum() < 0) {
            rate = BigDecimal.ZERO;
        }
        if (rate.compareTo(HUNDRED) > 0) {
            rate = HUNDRED;
        }
        return unitPrice.multiply(HUNDRED.subtract(rate))
            .divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
