package com.nexus.erp.wm.domain.bo;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
@Data public class WmTraceAuthorizationBo {
 @NotNull private Long erpWoId; @NotNull private Long mesWoId;
 @NotBlank @Size(max=128) private String commandId;
 @NotNull private Long warehouseId; private Long locationId;
 @Min(1) private long policyVersion;
 private Long materialId; private Long requirementDetailId;
 private List<Selection> selections;
 public record Selection(Long sourceStockId,Long identityId,Long erpBatchId,BigDecimal quantity) { }
}
