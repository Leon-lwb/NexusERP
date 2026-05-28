package com.nexus.online.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

// DTO定义
@Data
public class MainSubDataDTO {
    private Map<String, Object> main;
    private List<Map<String, Object>> subs;
}
