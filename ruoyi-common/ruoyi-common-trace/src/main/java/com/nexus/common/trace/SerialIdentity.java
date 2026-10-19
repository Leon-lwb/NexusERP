package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** ERP正式实物身份；MES本地SN主键单独维护。 */
public record SerialIdentity(@JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId,String snCode,@JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId,String batchCode) { }
