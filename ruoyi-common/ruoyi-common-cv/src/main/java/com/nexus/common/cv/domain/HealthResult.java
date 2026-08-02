package com.nexus.common.cv.domain;

import lombok.Data;

import java.util.List;

/**
 * 健康检查响应 DTO
 * <p>GET /health → {status, service}；GET /health/ready → {status, models: []}</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@Data
public class HealthResult {

    /** ok / ready 等 */
    private String status;

    private String service;

    private List<String> models;
}
