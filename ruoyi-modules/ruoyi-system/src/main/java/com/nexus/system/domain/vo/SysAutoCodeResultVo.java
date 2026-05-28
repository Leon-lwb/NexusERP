package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysAutoCodeResult;

import java.io.Serial;
import java.io.Serializable;

@Data
@AutoMapper(target = SysAutoCodeResult.class)
public class SysAutoCodeResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    private Long codeId;
    private Long ruleId;
    private String genDate;
    private Integer genIndex;
    private String lastResult;
    private Integer lastSerialNo;
    private String lastInputChar;
    private String remark;
    private String attr1;
    private String attr2;
    private Integer attr3;
    private Integer attr4;
}
