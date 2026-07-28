package com.aiyi.game.dnfserver.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChinaseUtilTest {

    private static final String CHARACTER_NAME = "\u8d85\u6c14\u4eba\u732b\u5934";
    private static final String LEGACY_MOJIBAKE = "\u00e8\u00b6\u2026\u00e6\u00b0\u201d\u00e4\u00ba\u00ba\u00e7\u0152\u00ab\u00e5\u00a4\u00b4";
    private static final String LEGACY_MOJIBAKE_WITH_CONTROL_BYTE = "\u00e4\u00b8\u0081";
    private static final String LEGACY_MOJIBAKE_WITH_MIXED_CP1252_AND_CONTROL_BYTES = "\u00e6\u02c6\u0090\u00e9\u2022\u00b7\u00e8\u2020\u00a0\u00e5\u203a\u0160\u00e6\u00b4\u00bb\u00e5\u2039\u2022";

    @Test
    public void keepsAlreadyDecodedChineseText() {
        assertEquals(CHARACTER_NAME, ChinaseUtil.toSimple(CHARACTER_NAME));
    }

    @Test
    public void decodesLegacyCp1252Utf8Mojibake() {
        assertEquals(CHARACTER_NAME, ChinaseUtil.toSimple(LEGACY_MOJIBAKE));
    }

    @Test
    public void decodesLegacyUtf8MojibakeWithLatin1ControlByte() {
        assertEquals("\u4e01", ChinaseUtil.toSimple(LEGACY_MOJIBAKE_WITH_CONTROL_BYTE));
    }

    @Test
    public void decodesLegacyUtf8MojibakeWithMixedCp1252AndControlBytes() {
        assertEquals("\u6210\u957f\u80f6\u56ca\u6d3b\u52a8", ChinaseUtil.toSimple(LEGACY_MOJIBAKE_WITH_MIXED_CP1252_AND_CONTROL_BYTES));
    }
}
