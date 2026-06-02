package com.nexus.erp.so;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.so.domain.SoPriceList;
import com.nexus.erp.so.domain.SoPriceListLine;
import com.nexus.erp.so.mapper.SoPriceListLineMapper;
import com.nexus.erp.so.mapper.SoPriceListMapper;
import com.nexus.erp.so.service.impl.PricingEngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 价格引擎Service单元测试
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PricingEngineServiceImplTest {

    @Mock
    private SoPriceListMapper soPriceListMapper;

    @Mock
    private SoPriceListLineMapper soPriceListLineMapper;

    @Mock
    private BdMaterialMapper bdMaterialMapper;

    @InjectMocks
    private PricingEngineServiceImpl pricingEngineService;

    private Long clientId;
    private Long materialId;
    private BigDecimal qty;
    private LocalDate orderDate;

    @BeforeEach
    void setUp() {
        clientId = 100L;
        materialId = 200L;
        qty = BigDecimal.TEN;
        orderDate = LocalDate.of(2026, 6, 1);
    }

    /**
     * 当没有客户专属价和促销价时，返回标准基价
     */
    @Test
    void testResolvePrice_StandardBasePrice() {
        // 客户专属价 - 无匹配，促销价 - 无匹配，标准基价 - 有匹配
        SoPriceList basePriceList = new SoPriceList();
        basePriceList.setPriceListId(1L);
        basePriceList.setListType("BASE");
        basePriceList.setStatus("ACTIVE");

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())   // 客户专属价查询
            .thenReturn(Collections.emptyList())   // 促销价查询
            .thenReturn(List.of(basePriceList));    // 标准基价查询

        SoPriceListLine line = new SoPriceListLine();
        line.setPriceListId(1L);
        line.setMaterialId(materialId);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setMinQty(BigDecimal.ONE);
        line.setMaxQty(new BigDecimal("9999"));
        line.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(line);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("100.0000").compareTo(result));
    }

    /**
     * 客户专属价优先于标准基价
     */
    @Test
    void testResolvePrice_CustomerSpecificPrice() {
        SoPriceList customerPriceList = new SoPriceList();
        customerPriceList.setPriceListId(10L);
        customerPriceList.setListType("CUSTOMER");
        customerPriceList.setClientId(clientId);
        customerPriceList.setStatus("ACTIVE");
        customerPriceList.setValidFrom(LocalDate.of(2026, 1, 1));
        customerPriceList.setValidTo(LocalDate.of(2026, 12, 31));

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(customerPriceList));

        SoPriceListLine line = new SoPriceListLine();
        line.setPriceListId(10L);
        line.setMaterialId(materialId);
        line.setUnitPrice(new BigDecimal("80.00"));
        line.setMinQty(BigDecimal.ONE);
        line.setMaxQty(new BigDecimal("9999"));
        line.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(line);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        // 客户专属价80，折扣率null默认100，结果 = 80 * 100 / 100 = 80
        assertEquals(0, new BigDecimal("80.0000").compareTo(result));
    }

    /**
     * 期间促销价（在日期范围内）优先于标准基价
     */
    @Test
    void testResolvePrice_PromotionPrice() {
        // 客户专属价 - 无匹配（clientId为null时直接返回null）
        Long nullClientId = null;

        SoPriceList promotionPriceList = new SoPriceList();
        promotionPriceList.setPriceListId(20L);
        promotionPriceList.setListType("PROMOTION");
        promotionPriceList.setStatus("ACTIVE");
        promotionPriceList.setValidFrom(LocalDate.of(2026, 5, 1));
        promotionPriceList.setValidTo(LocalDate.of(2026, 7, 31));

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(promotionPriceList));

        SoPriceListLine line = new SoPriceListLine();
        line.setPriceListId(20L);
        line.setMaterialId(materialId);
        line.setUnitPrice(new BigDecimal("90.00"));
        line.setMinQty(BigDecimal.ONE);
        line.setMaxQty(new BigDecimal("9999"));
        line.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(line);

        BigDecimal result = pricingEngineService.resolvePrice(nullClientId, materialId, qty, orderDate);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("90.0000").compareTo(result));
    }

    /**
     * 价格行中minQty/maxQty数量范围匹配
     */
    @Test
    void testResolvePrice_QuantityRange() {
        SoPriceList basePriceList = new SoPriceList();
        basePriceList.setPriceListId(1L);
        basePriceList.setListType("BASE");
        basePriceList.setStatus("ACTIVE");

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())   // 客户专属价
            .thenReturn(Collections.emptyList())   // 促销价
            .thenReturn(List.of(basePriceList));    // 标准基价

        // 数量范围 5-20，当前qty=10，应匹配
        SoPriceListLine line = new SoPriceListLine();
        line.setPriceListId(1L);
        line.setMaterialId(materialId);
        line.setUnitPrice(new BigDecimal("85.00"));
        line.setMinQty(new BigDecimal("5"));
        line.setMaxQty(new BigDecimal("20"));
        line.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(line);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("85.0000").compareTo(result));
    }

    /**
     * 折扣率应用测试
     */
    @Test
    void testResolvePrice_DiscountRate() {
        SoPriceList basePriceList = new SoPriceList();
        basePriceList.setPriceListId(1L);
        basePriceList.setListType("BASE");
        basePriceList.setStatus("ACTIVE");

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())   // 客户专属价
            .thenReturn(Collections.emptyList())   // 促销价
            .thenReturn(List.of(basePriceList));    // 标准基价

        // 单价100，折扣率90（即90%），结果 = 100 * 90 / 100 = 90
        SoPriceListLine line = new SoPriceListLine();
        line.setPriceListId(1L);
        line.setMaterialId(materialId);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setMinQty(BigDecimal.ONE);
        line.setMaxQty(new BigDecimal("9999"));
        line.setDiscountRate(new BigDecimal("90"));

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(line);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("90.0000").compareTo(result));
    }

    /**
     * 无任何价格匹配时，返回物料参考价；物料参考价也为null时返回BigDecimal.ZERO
     */
    @Test
    void testResolvePrice_NoPriceFound() {
        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        BdMaterial material = new BdMaterial();
        material.setMaterialId(materialId);
        material.setReferencePrice(null);

        when(bdMaterialMapper.selectById(materialId))
            .thenReturn(material);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    /**
     * 客户专属价 > 促销价 > 标准基价 优先级验证
     */
    @Test
    void testResolvePrice_CustomerPriceOverPromotion() {
        SoPriceList customerPriceList = new SoPriceList();
        customerPriceList.setPriceListId(10L);
        customerPriceList.setListType("CUSTOMER");
        customerPriceList.setClientId(clientId);
        customerPriceList.setStatus("ACTIVE");
        customerPriceList.setValidFrom(LocalDate.of(2026, 1, 1));
        customerPriceList.setValidTo(LocalDate.of(2026, 12, 31));

        SoPriceList promotionPriceList = new SoPriceList();
        promotionPriceList.setPriceListId(20L);
        promotionPriceList.setListType("PROMOTION");
        promotionPriceList.setStatus("ACTIVE");
        promotionPriceList.setValidFrom(LocalDate.of(2026, 1, 1));
        promotionPriceList.setValidTo(LocalDate.of(2026, 12, 31));

        SoPriceList basePriceList = new SoPriceList();
        basePriceList.setPriceListId(1L);
        basePriceList.setListType("BASE");
        basePriceList.setStatus("ACTIVE");

        // 客户专属价查询返回结果，后续查询不应再执行
        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(customerPriceList));

        SoPriceListLine customerLine = new SoPriceListLine();
        customerLine.setPriceListId(10L);
        customerLine.setMaterialId(materialId);
        customerLine.setUnitPrice(new BigDecimal("70.00"));
        customerLine.setMinQty(BigDecimal.ONE);
        customerLine.setMaxQty(new BigDecimal("9999"));
        customerLine.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(customerLine);

        BigDecimal result = pricingEngineService.resolvePrice(clientId, materialId, qty, orderDate);

        assertNotNull(result);
        // 客户专属价70应被返回，而非促销价90或标准价100
        assertEquals(0, new BigDecimal("70.0000").compareTo(result));
        // 验证只查询了一次价格表（客户专属价命中后不再查询促销价和标准基价）
        verify(soPriceListMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 已过期的促销价不应匹配
     */
    @Test
    void testResolvePrice_PromotionExpired() {
        // 客户ID为null，跳过客户专属价
        Long nullClientId = null;

        // 促销价已过期（validTo在orderDate之前）
        SoPriceList expiredPromotion = new SoPriceList();
        expiredPromotion.setPriceListId(20L);
        expiredPromotion.setListType("PROMOTION");
        expiredPromotion.setStatus("ACTIVE");
        expiredPromotion.setValidFrom(LocalDate.of(2025, 1, 1));
        expiredPromotion.setValidTo(LocalDate.of(2025, 12, 31));

        SoPriceList basePriceList = new SoPriceList();
        basePriceList.setPriceListId(1L);
        basePriceList.setListType("BASE");
        basePriceList.setStatus("ACTIVE");

        when(soPriceListMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())   // 促销价查询（因日期范围不匹配，MyBatis条件过滤后为空）
            .thenReturn(List.of(basePriceList));    // 标准基价查询

        SoPriceListLine baseLine = new SoPriceListLine();
        baseLine.setPriceListId(1L);
        baseLine.setMaterialId(materialId);
        baseLine.setUnitPrice(new BigDecimal("100.00"));
        baseLine.setMinQty(BigDecimal.ONE);
        baseLine.setMaxQty(new BigDecimal("9999"));
        baseLine.setDiscountRate(null);

        when(soPriceListLineMapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(baseLine);

        BigDecimal result = pricingEngineService.resolvePrice(nullClientId, materialId, qty, orderDate);

        assertNotNull(result);
        // 应返回标准基价100，而非过期促销价
        assertEquals(0, new BigDecimal("100.0000").compareTo(result));
    }
}
