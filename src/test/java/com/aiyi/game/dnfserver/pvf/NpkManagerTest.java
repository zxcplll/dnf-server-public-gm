package com.aiyi.game.dnfserver.pvf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.junit.Assert.assertArrayEquals;

public class NpkManagerTest {

    private static final String ITEM_PATH = "item/title/title_cn.img";
    private static final int ICON_INDEX = 98;
    private static final byte[] PNG_BYTES = new byte[]{1, 2, 3, 4};

    private Path cacheFile;
    private byte[] originalBytes;

    @Before
    public void setUp() throws Exception {
        Path cacheDirectory = Paths.get("data", "IconCache");
        Files.createDirectories(cacheDirectory);
        cacheFile = cacheDirectory.resolve(cacheName("sprite/" + ITEM_PATH, ICON_INDEX));
        if (Files.exists(cacheFile)) {
            originalBytes = Files.readAllBytes(cacheFile);
        }
        Files.write(cacheFile, PNG_BYTES);
    }

    @After
    public void tearDown() throws Exception {
        if (cacheFile == null) {
            return;
        }
        if (originalBytes == null) {
            Files.deleteIfExists(cacheFile);
        } else {
            Files.write(cacheFile, originalBytes);
        }
    }

    @Test
    public void readsPvfUtilitySpritePrefixCacheEntryForAnApiItemPath() throws Exception {
        Method readCachedIcon = NpkManager.class.getDeclaredMethod("readCachedIcon", String.class, int.class);
        readCachedIcon.setAccessible(true);

        byte[] result = (byte[]) readCachedIcon.invoke(new NpkManager(), ITEM_PATH, ICON_INDEX);

        assertArrayEquals(PNG_BYTES, result);
    }

    private String cacheName(String path, int index) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] hash = md5.digest((path + "@" + index).getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (byte current : hash) {
            value.append(String.format("%02x", current & 0xff));
        }
        return value + ".png";
    }
}
