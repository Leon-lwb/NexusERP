package com.nexus.erp.ai.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 独立 MockMvc 实际 HTTP 映射→创建服务→持久化调用，不启动服务或访问四个本地库。 */
class ErpCertifiedSourceControllerTest {
    @Test void httpCreateInvokesActualBirthServiceAndReturnsServerGeneratedIdentity() throws Exception {
        var f=new ErpCertifiedSourceBirthServiceTest();f.setup();
        try{
            var mvc=MockMvcBuilders.standaloneSetup(new ErpCertifiedSourceController(f.service,mock(ErpCertifiedSourcePreviewService.class))).build();
            mvc.perform(post("/erp/ai/certified-source/materials").contentType(MediaType.APPLICATION_JSON).content("""
                {"materialCode":"TEST-NEW-001","materialName":"新测试物料","categoryId":"501","baseUomId":"301", "warehouseId":"801","areaId":"802","locationId":"803","scenario":"本地模拟场景"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.materialId").exists()).andExpect(jsonPath("$.data.birthId").exists())
                .andExpect(jsonPath("$.data.supplierTermsState").value("SUPPLIER_TERMS_PENDING"));
            verify(f.mapper).insertMaterial(any());verify(f.mapper).insertBirth(any());verify(f.mapper).insertNewGuard(anyString(),anyLong());
        }finally{f.cleanup();}
    }
}
