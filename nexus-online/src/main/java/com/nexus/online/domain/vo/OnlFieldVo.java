package com.nexus.online.domain.vo;

import java.io.Serializable;
import java.util.List;

import com.nexus.generator.domain.GenTableColumn;

import lombok.Data;

@Data
public class OnlFieldVo extends GenTableColumn {
    private static final long serialVersionUID = 1L;
    private List<DictVo> commonDictCode;
}