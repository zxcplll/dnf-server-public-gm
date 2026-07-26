package com.aiyi.game.dnfserver.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChinaseUtilTest {

    private static final String CHARACTER_NAME = "\u8d85\u6c14\u4eba\u732b\u5934";
    private static final String LEGACY_MOJIBAKE = "\u00e8\u00b6\u2026\u00e6\u00b0\u201d\u00e4\u00ba\u00ba\u00e7\u0152\u00ab\u00e5\u00a4\u00b4";

    @Test
    public void keepsAlreadyDecodedChineseText() {
        assertEquals(CHARACTER_NAME, ChinaseUtil.toSimple(CHARACTER_NAME));
    }

    @Test
    public void decodesLegacyCp1252Utf8Mojibake() {
        assertEquals(CHARACTER_NAME, ChinaseUtil.toSimple(LEGACY_MOJIBAKE));
    }
}
