package com.aiyi.game.dnfserver.pvf;

import com.xiaoyouma.dnf.parser.npk.coder.NpkCoder;
import com.xiaoyouma.dnf.parser.npk.model.NpkTexture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * NPK管理器
 * @author xiatian
 */
@Component
public class NpkManager {

    protected static final Logger logger = LoggerFactory.getLogger(NpkManager.class);

    @PostConstruct
    public void init(){
        File file = new File("data/ImagePacks2");
        if (!file.exists()){
            logger.warn("Folder ImagePacks2 not found!");
        }else{
            try {
                NpkCoder.initialize(file.getAbsolutePath());
            } catch (Throwable e) {
                logger.warn("NPK initialize failed, fallback to IconCache only: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取图片字节数组
     * @param path 图片路径
     * @param index 图片索引
     * @return 图片字节数组
     */
    public byte[] getImageBytes(String path, int index){
        NpkTexture[] textures = null;
        try {
            textures = NpkCoder.loadImg(path).getTextures();
        }catch (Exception e){
            return readCachedIcon(path, index);
        }
        if (null == textures || textures.length <= index){
            return readCachedIcon(path, index);
        }
        try {
            byte[] imageBytes = textures[index].toPngBytes();
            if (imageBytes != null && imageBytes.length > 0) {
                return imageBytes;
            }
        } catch (Exception ignored) {
        }
        return readCachedIcon(path, index);
    }

    private byte[] readCachedIcon(String path, int index) {
        try {
            Path cachePath = new File("data/IconCache", iconCacheName(path, index)).toPath();
            if (Files.isRegularFile(cachePath)) {
                return Files.readAllBytes(cachePath);
            }
        } catch (Exception ignored) {
        }
        return new byte[0];
    }

    private String iconCacheName(String path, int index) throws Exception {
        String key = (path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT)) + "@" + index;
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb + ".png";
    }
}
