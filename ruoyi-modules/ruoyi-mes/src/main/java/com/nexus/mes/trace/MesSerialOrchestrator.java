package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.nexus.mes.trace.MesTraceContext.need;

/** No transaction or lock spans ERP reserve and the proxied MES writer. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesSerialOrchestrator {
    private final SerialIdentityPort identities;
    private final MesTraceContext context;
    private final ProFlowCardMapper cards;
    private final MesSnCreationWriter writer;
    private final MesErpSourceReader sources;
    public List<Long> forCard(String commandId,Long cardId,Integer quantity,String initialStatus,Long productLotId) {
        context.requireNoTransaction();need(quantity!=null && quantity>0,"生成数量必须大于0");
        need(initialStatus==null || initialStatus.isBlank() || "IN_PRODUCTION".equals(initialStatus),"生成SN不得跳过实际生产和质量门禁");
        var card=cards.selectById(cardId);need(card!=null && context.tenant().equals(card.getTenantId()),"实际流转卡不存在或租户不匹配");
        var request=new MesSnCreationWriter.Request(commandId,cardId,card.getMesWoId(),cardId,"BATCH",null,card.getMaterialId(),productLotId,null,null,null);
        var replay=writer.replay(request,quantity);if(replay!=null)return replay;
        var reservation=identities.reserve(context.tenant(),new SerialReservationCommand(commandId,card.getMaterialId(),quantity,null,"MES_FLOW_CARD",cardId));
        return writer.write(reservation,request);
    }
    public List<Long> forSubcontract(String commandId,Long subcontractId,String subcontractNo,Long materialId,int quantity) {
        context.requireNoTransaction();need(quantity>0,"生成数量必须大于0");
        var request=new MesSnCreationWriter.Request(commandId,null,null,subcontractId,"SUBCONTRACT",subcontractNo,materialId,null,null,null,null);
        var replay=writer.replay(request,quantity);if(replay!=null)return replay;
        var proof=sources.subcontract(subcontractId,materialId,subcontractNo,quantity);
        var reservation=identities.reserve(context.tenant(),new SerialReservationCommand(commandId,materialId,quantity,null,"SUBCONTRACT",subcontractId));
        return writer.writeWithSource(reservation,request,proof);
    }
    public List<Long> manual(String commandId,Long mesWoId,Long cardId,Long materialId,String expectedCode,Long parentSnId,String remark,Long productLotId) {
        context.requireNoTransaction();need(mesWoId!=null && materialId!=null,"手工生成必须关联实际工单和物料");
        var request=new MesSnCreationWriter.Request(commandId,cardId,mesWoId,mesWoId,"MANUAL",null,materialId,productLotId,parentSnId,remark,expectedCode);
        var replay=writer.replay(request,1);if(replay!=null)return replay;
        var reservation=identities.reserve(context.tenant(),new SerialReservationCommand(commandId,materialId,1,null,"MES_MANUAL",mesWoId));
        return writer.write(reservation,request);
    }
}
