package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.WmSnToWmSnVoMapperImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WmSnIdentityVersionProjectionTest {
    @Test
    void theOriginalIdentityVersionSurvivesTheActualVoMappingAsAnExactString() throws Exception {
        var identity = new WmSn();
        identity.setSnId(8100000000000000001L);
        identity.setSnCode("FORMAL");
        identity.setIdentityVersion(9007199254741997L);
        var detail = new WmSnToWmSnVoMapperImpl().convert(identity);
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(detail));
        assertTrue(
                json.path("identityVersion").isTextual(),
                "safe retirement requires the actual immutable-version field from GET detail");
        assertEquals("9007199254741997", json.path("identityVersion").asText());
    }
}
