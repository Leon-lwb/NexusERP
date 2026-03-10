package com.nexus.online.domain.vo;

import java.util.List;

import com.nexus.generator.domain.GenTable;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class TableVo extends GenTable {
    @Valid
    private List<OnlFieldVo> columnsVo;
}