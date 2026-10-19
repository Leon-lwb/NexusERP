package com.nexus.station.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.CodeResolverPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * 工位设备解析沿用当前固定租户和密钥通道，不接受请求体覆盖租户。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mes/station/code")
public class StationCodeController {
    private final CodeResolverPort resolver;

    @Value("${mes.api-key:}")
    private String apiKey;

    @PostMapping("/resolve")
    public R<CodeResolution> resolve(
            @RequestHeader(value = "X-API-KEY", required = false) String header,
            @RequestBody CodeResolveRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException("工位服务未配置访问密钥");
        }
        if (header == null
                || !MessageDigest.isEqual(
                        apiKey.getBytes(StandardCharsets.UTF_8),
                        header.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException("无效的API密钥");
        }
        return R.ok(
                TenantHelper.dynamic(
                        "000000",
                        () ->
                                resolver.resolve(
                                        "000000",
                                        request,
                                        Set.of(
                                                CodeObjectType.SN,
                                                CodeObjectType.BATCH,
                                                CodeObjectType.MATERIAL))));
    }
}
