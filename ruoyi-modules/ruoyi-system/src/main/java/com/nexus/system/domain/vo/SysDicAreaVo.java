package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysDicArea;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@AutoMapper(target = SysDicArea.class)
public class SysDicAreaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long pkid;
    private Long areaId;
    private Long parentAreaId;
    private String areaName;
    private String lat;
    private String lng;
    private Long layered;
    private Long sort;
    private String address;
    private List<SysDicAreaVo> children;
}
