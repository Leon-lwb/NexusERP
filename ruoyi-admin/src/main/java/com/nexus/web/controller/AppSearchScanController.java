package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.wm.domain.WmBarcode;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.mapper.WmBarcodeMapper;
import com.nexus.erp.wm.mapper.WmSnMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App 扫码解析 / 全局搜索 Controller
 * <p>补齐《接口状态与后端缺口清单》§5.5 缺失的 {@code POST /app/scan/resolve}
 * 与 {@code GET /app/search/global}。
 * <p>实现约定：
 * <ul>
 *   <li>均为只读查询，数据按租户自动隔离（走 MyBatis-Plus 条件构造器）</li>
 *   <li>扫码解析按「条码实例 → SN → 物料编码」优先级依次命中，全部未命中返回 UNKNOWN</li>
 *   <li>全局搜索按业务域分组返回，每域最多 {@value #MAX_PER_TYPE} 条，避免大结果集拖垮移动端</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app")
public class AppSearchScanController {

    /** 单域最大返回条数 */
    private static final int MAX_PER_TYPE = 5;
    /** 关键词最大长度 */
    private static final int MAX_KEYWORD_LENGTH = 50;

    /** 条码类型：序列号 */
    private static final String BIZ_TYPE_SN = "SN";
    /** 解析结果类型：未识别 */
    private static final String BIZ_TYPE_UNKNOWN = "UNKNOWN";

    /** 条码实例 Mapper */
    private final WmBarcodeMapper wmBarcodeMapper;
    /** 序列号 Mapper */
    private final WmSnMapper wmSnMapper;
    /** 物料 Mapper */
    private final BdMaterialMapper bdMaterialMapper;
    /** 生产工单 Mapper */
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    /** 销售订单 Mapper */
    private final SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
    /** 采购订单 Mapper */
    private final PuPurchaseOrderMapper puPurchaseOrderMapper;

    // ==================== 扫码解析 ====================

    /**
     * 扫码解析
     * <p>请求体支持 {@code code} / {@code barcode} / {@code snCode} 任一字段（兼容不同扫描入口）。
     * <p>解析优先级：
     * <ol>
     *   <li>{@code wm_barcode.barcode_code} 命中 → 使用条码记录上的 bizType / bizId</li>
     *   <li>{@code wm_sn.sn_code} 命中 → bizType = SN</li>
     *   <li>{@code bd_material.material_code} 命中 → bizType = MATERIAL</li>
     *   <li>均未命中 → bizType = UNKNOWN</li>
     * </ol>
     *
     * @param body 请求体，包含待解析的码值
     * @return bizType / targetId / targetName / targetUrl，以及条码附加信息
     */
    @SaCheckLogin
    @PostMapping("/scan/resolve")
    public R<Map<String, Object>> scanResolve(@RequestBody Map<String, String> body) {
        String code = firstNonBlank(body == null ? null : body.get("code"),
            body == null ? null : body.get("barcode"),
            body == null ? null : body.get("snCode"));
        if (StrUtil.isBlank(code)) {
            return R.fail("扫描内容不能为空");
        }
        String scanned = code.trim();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", scanned);

        try {
            // 1. 条码实例优先（唯一命中即返回）
            WmBarcode barcode = selectFirst(new LambdaQueryWrapper<WmBarcode>()
                .eq(WmBarcode::getBarcodeCode, scanned), wmBarcodeMapper);
            if (barcode != null) {
                fillCommon(result, StrUtil.blankToDefault(barcode.getBizType(), BIZ_TYPE_UNKNOWN),
                    barcode.getBizId(), scanned, barcodeRoute(barcode));
                putIfPresent(result, "materialId", barcode.getMaterialId());
                putIfPresent(result, "batchId", barcode.getBatchId());
                putIfPresent(result, "batchCode", barcode.getBatchCode());
                putIfPresent(result, "snId", barcode.getSnId());
                putIfPresent(result, "stockId", barcode.getStockId());
                putIfPresent(result, "status", barcode.getStatus());
                return R.ok(result);
            }

            // 2. 序列号
            WmSn sn = selectFirst(new LambdaQueryWrapper<WmSn>().eq(WmSn::getSnCode, scanned), wmSnMapper);
            if (sn != null) {
                fillCommon(result, BIZ_TYPE_SN, sn.getSnId(),
                    StrUtil.blankToDefault(sn.getMaterialName(), scanned), snRoute(sn.getSnId()));
                putIfPresent(result, "materialId", sn.getMaterialId());
                putIfPresent(result, "materialCode", sn.getMaterialCode());
                putIfPresent(result, "materialName", sn.getMaterialName());
                return R.ok(result);
            }

            // 3. 物料编码
            BdMaterial material = selectFirst(new LambdaQueryWrapper<BdMaterial>()
                .eq(BdMaterial::getMaterialCode, scanned), bdMaterialMapper);
            if (material != null) {
                fillCommon(result, "MATERIAL", material.getMaterialId(),
                    StrUtil.blankToDefault(material.getMaterialName(), scanned), null);
                putIfPresent(result, "materialCode", material.getMaterialCode());
                putIfPresent(result, "materialName", material.getMaterialName());
                return R.ok(result);
            }
        } catch (Exception e) {
            log.warn("[AppScan] 扫码解析失败, code={}: {}", scanned, e.getMessage(), e);
            return R.fail("解析失败，请稍后重试");
        }

        // 4. 未识别（不视为错误，便于前端提示"未找到对应单据"）
        fillCommon(result, BIZ_TYPE_UNKNOWN, null, scanned, null);
        return R.ok(result);
    }

    /**
     * 条码记录 → 前端跳转路由（仅对已实现的页面返回，其余为 null）
     */
    private String barcodeRoute(WmBarcode barcode) {
        if (BIZ_TYPE_SN.equalsIgnoreCase(barcode.getBizType()) && barcode.getSnId() != null) {
            return snRoute(barcode.getSnId());
        }
        return null;
    }

    /**
     * SN 详情页路由（pages/mes/pro/sn/detail 读取 options.snId）
     */
    private String snRoute(Long snId) {
        return snId == null ? null : "/pages/mes/pro/sn/detail?snId=" + snId;
    }

    private void fillCommon(Map<String, Object> result, String bizType, Long targetId, String targetName, String targetUrl) {
        result.put("bizType", bizType);
        result.put("targetId", targetId);
        result.put("targetName", targetName);
        result.put("targetUrl", targetUrl);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    // ==================== 全局搜索 ====================

    /**
     * 全局搜索（按业务域分组）
     * <p>支持域：material（物料）、sn（序列号）、workorder（生产工单）、
     * so（销售订单）、pu（采购订单）；不传 module 时搜索全部域。
     *
     * @param keyword 搜索关键词（编号 / 名称模糊匹配）
     * @param module  可选，限定业务域
     * @return keyword / module / total / resultList
     */
    @SaCheckLogin
    @GetMapping("/search/global")
    public R<Map<String, Object>> globalSearch(@RequestParam String keyword,
                                               @RequestParam(required = false) String module) {
        String kw = StrUtil.trim(keyword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", kw);
        data.put("module", module);

        List<Map<String, Object>> results = new ArrayList<>();
        if (StrUtil.isBlank(kw)) {
            data.put("total", 0);
            data.put("resultList", results);
            return R.ok(data);
        }
        if (kw.length() > MAX_KEYWORD_LENGTH) {
            return R.fail("搜索关键词过长");
        }

        try {
            if (hitModule(module, "material")) {
                searchMaterials(kw, results);
            }
            if (hitModule(module, "sn")) {
                searchSn(kw, results);
            }
            if (hitModule(module, "workorder")) {
                searchWorkOrders(kw, results);
            }
            if (hitModule(module, "so")) {
                searchSalesOrders(kw, results);
            }
            if (hitModule(module, "pu")) {
                searchPurchaseOrders(kw, results);
            }
        } catch (Exception e) {
            log.warn("[AppSearch] 全局搜索失败, keyword={}: {}", kw, e.getMessage(), e);
            return R.fail("搜索失败，请稍后重试");
        }

        data.put("total", results.size());
        data.put("resultList", results);
        return R.ok(data);
    }

    /**
     * 是否检索该业务域：未指定 module 时全查，指定时精确匹配。
     */
    private boolean hitModule(String module, String target) {
        return StrUtil.isBlank(module) || target.equalsIgnoreCase(module);
    }

    private void searchMaterials(String kw, List<Map<String, Object>> results) {
        List<BdMaterial> list = bdMaterialMapper.selectList(new LambdaQueryWrapper<BdMaterial>()
            .and(w -> w.like(BdMaterial::getMaterialCode, kw).or().like(BdMaterial::getMaterialName, kw))
            .last("limit " + MAX_PER_TYPE));
        for (BdMaterial item : list) {
            results.add(searchItem("material", "物料", item.getMaterialId(),
                item.getMaterialCode(), item.getMaterialName(), null));
        }
    }

    private void searchSn(String kw, List<Map<String, Object>> results) {
        List<WmSn> list = wmSnMapper.selectList(new LambdaQueryWrapper<WmSn>()
            .like(WmSn::getSnCode, kw)
            .last("limit " + MAX_PER_TYPE));
        for (WmSn item : list) {
            results.add(searchItem("sn", "序列号", item.getSnId(), item.getSnCode(),
                item.getMaterialName(), snRoute(item.getSnId())));
        }
    }

    private void searchWorkOrders(String kw, List<Map<String, Object>> results) {
        List<PrdWorkOrder> list = prdWorkOrderMapper.selectList(new LambdaQueryWrapper<PrdWorkOrder>()
            .like(PrdWorkOrder::getWoNo, kw)
            .last("limit " + MAX_PER_TYPE));
        for (PrdWorkOrder item : list) {
            results.add(searchItem("workorder", "生产工单", item.getWoId(), item.getWoNo(),
                item.getMaterialName(), null));
        }
    }

    private void searchSalesOrders(String kw, List<Map<String, Object>> results) {
        List<SoSalesOrderHeader> list = soSalesOrderHeaderMapper.selectList(new LambdaQueryWrapper<SoSalesOrderHeader>()
            .like(SoSalesOrderHeader::getSoNo, kw)
            .last("limit " + MAX_PER_TYPE));
        for (SoSalesOrderHeader item : list) {
            results.add(searchItem("so", "销售订单", item.getSoId(), item.getSoNo(), null, null));
        }
    }

    private void searchPurchaseOrders(String kw, List<Map<String, Object>> results) {
        List<PuPurchaseOrder> list = puPurchaseOrderMapper.selectList(new LambdaQueryWrapper<PuPurchaseOrder>()
            .like(PuPurchaseOrder::getOrderNo, kw)
            .last("limit " + MAX_PER_TYPE));
        for (PuPurchaseOrder item : list) {
            results.add(searchItem("pu", "采购订单", item.getOrderId(), item.getOrderNo(), null, null));
        }
    }

    /**
     * 组装单条搜索结果。
     *
     * @param module   业务域标识
     * @param moduleName 业务域中文名
     * @param id       业务主键
     * @param title    主标题（编号）
     * @param subtitle 副标题（名称等，可为空）
     * @param route    前端跳转路由（无对应页面时为 null）
     */
    private Map<String, Object> searchItem(String module, String moduleName, Long id,
                                           String title, String subtitle, String route) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("resultType", module);
        item.put("module", module);
        item.put("moduleName", moduleName);
        item.put("id", id);
        item.put("title", title);
        item.put("subtitle", subtitle);
        item.put("route", route);
        return item;
    }

    // ==================== 通用查询辅助 ====================

    /**
     * 只取首条（避免 selectOne 在多命中时抛 TooManyResultsException）。
     *
     * <p>使用分页 limit 1，不拼接原生 SQL 片段。
     */
    private <T> T selectFirst(LambdaQueryWrapper<T> wrapper, BaseMapper<T> mapper) {
        Page<T> page = mapper.selectPage(new Page<>(1, 1), wrapper);
        List<T> records = page.getRecords();
        return records == null || records.isEmpty() ? null : records.get(0);
    }
}
