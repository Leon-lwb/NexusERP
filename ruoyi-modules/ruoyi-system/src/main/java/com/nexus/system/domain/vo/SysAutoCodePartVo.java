package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysAutoCodePart;

import java.io.Serial;
import java.io.Serializable;

@Data
@AutoMapper(target = SysAutoCodePart.class)
public class SysAutoCodePartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    private Long partId;
    private Long ruleId;
    private Integer partIndex;
    private String partType;
    private String partCode;
    private String partName;
    private Integer partLength;
    private String dateFormat;
    private String inputCharacter;
    private String fixCharacter;
    private Integer seriaStartNo;
    private Integer seriaStep;
    private Integer seriaNowNo;
    private String cycleFlag;
    private String cycleMethod;
    private String remark;
    private String attr1;
    private String attr2;
    private Integer attr3;
    private Integer attr4;
}
