package com.nexus.station.service;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 原请求指纹必须拒绝非法 Unicode，不能由 UTF-8 替换字符合并不同原输入。 */
class StationSinglePassFingerprintTest {
    String fingerprint(String code,Long requestedId) {
        return StationSinglePassFingerprint.of("000000","SN-1","STATION",1000,"PASS",requestedId,code,null);
    }
    @ParameterizedTest @ValueSource(strings={"\uD800","\uDC00"})
    void malformedSurrogateCannotAliasQuestionMarkRequest(String malformed) {
        assertThrows(ServiceException.class,()->fingerprint(malformed,null));
    }
    @Test void validChineseAndSupplementaryUnicodeRemainStable() {
        String code="员工-\uD83D\uDE00";
        assertEquals(fingerprint(code,105L),fingerprint(code,105L));
        assertTrue(fingerprint(code,105L).matches("[0-9a-f]{64}"));
        assertNotEquals(fingerprint(code,105L),fingerprint(code,106L));
    }
    @Test void requestedNullIdAndExplicitResolvedIdAreDifferentRawInputs() {
        assertNotEquals(fingerprint("EMP-1",null),fingerprint("EMP-1",105L));
    }
}
