package com.nexus.im.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nexus.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App 位置选点辅助接口。
 * <p>高德 Web 服务 Key 只保存在后端，不下发 App/H5；客户端只拿 POI 结果。</p>
 */
@Slf4j
@SaCheckLogin
@RestController
@RequestMapping("/im/app/location")
public class AppLocationController {

    private static final String AMAP_AROUND_URL = "https://restapi.amap.com/v3/place/around";

    @Value("${amap.web-service-key:}")
    private String webServiceKey;

    /**
     * 查询当前位置附近的地点，供 App 自定义位置选择面板使用。
     */
    @GetMapping("/nearby")
    public R<List<Map<String, Object>>> nearby(@RequestParam double latitude,
                                               @RequestParam double longitude,
                                               @RequestParam(defaultValue = "2000") int radius) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return R.fail("坐标无效");
        }
        if (StrUtil.isBlank(webServiceKey)) {
            return R.fail("未配置高德 Web 服务 Key");
        }
        int safeRadius = Math.max(200, Math.min(radius, 5000));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", webServiceKey);
        params.put("location", longitude + "," + latitude);
        params.put("radius", safeRadius);
        params.put("offset", 20);
        params.put("page", 1);
        params.put("extensions", "base");
        params.put("sortrule", "distance");

        try {
            String body = HttpUtil.get(AMAP_AROUND_URL, params, 5000);
            JSONObject root = JSONUtil.parseObj(body);
            if (!"1".equals(root.getStr("status"))) {
                log.warn("amap nearby failed: status={}, info={}, infocode={}",
                    root.getStr("status"), root.getStr("info"), root.getStr("infocode"));
                return R.fail("附近地点加载失败");
            }
            JSONArray pois = root.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                return R.ok(new ArrayList<>());
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < pois.size(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                if (poi == null) {
                    continue;
                }
                String location = poi.getStr("location");
                if (StrUtil.isBlank(location) || !location.contains(",")) {
                    continue;
                }
                String[] lngLat = location.split(",", 2);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", poi.getStr("id"));
                item.put("name", poi.getStr("name"));
                item.put("address", normalizeAddress(poi.get("address")));
                item.put("distance", poi.getStr("distance"));
                item.put("longitude", parseDouble(lngLat[0]));
                item.put("latitude", parseDouble(lngLat[1]));
                item.put("type", poi.getStr("type"));
                list.add(item);
            }
            return R.ok(list);
        } catch (Exception e) {
            log.warn("amap nearby exception: {}", e.getMessage());
            return R.fail("附近地点加载失败，请稍后重试");
        }
    }

    private String normalizeAddress(Object address) {
        if (address == null || address instanceof JSONArray) {
            return "";
        }
        return StrUtil.nullToEmpty(String.valueOf(address));
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
