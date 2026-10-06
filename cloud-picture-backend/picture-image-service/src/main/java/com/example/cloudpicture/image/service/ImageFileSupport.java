package com.example.cloudpicture.image.service;

import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片文件级共性逻辑：上传校验（格式/大小/魔数/尺寸）与下载文件名生成。
 * 共享图库与私有空间共用同一套规则，避免两处校验出现偏差
 */
@Slf4j
@Component
public class ImageFileSupport {

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    /** JDK ImageIO 能读尺寸的格式；WebP 无内置解码器，只能靠文件头魔数校验 */
    private static final Set<String> DIMENSION_READABLE_FORMATS = Set.of("jpg", "jpeg", "png");
    private static final int HEADER_LENGTH = 12;
    /** 路径分隔符、控制字符及 Windows 文件名非法字符 */
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final long maxFileSizeBytes;
    private final String maxFileSizeText;

    public ImageFileSupport(@Value("${picture.upload.max-file-size:10MB}") String maxFileSize) {
        this.maxFileSizeText = maxFileSize;
        this.maxFileSizeBytes = DataSize.parse(maxFileSize).toBytes();
    }

    public String validateAndGetFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 " + maxFileSizeText);
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件名不合法");
        }
        String format = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/jpeg/png/webp 格式");
        }
        if (!matchesMagic(readHeader(file), format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不是有效图片");
        }
        return format;
    }

    /** 只读取图片头部尺寸，不整图解码；读不出时返回 null，由调用方决定是否视为无效图片 */
    public int[] readImageSize(MultipartFile file) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInputStream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.warn("读取图片尺寸失败: {}", e.getMessage());
            return null;
        }
    }

    /** JDK 可解码格式读不出尺寸才算无效图片；WebP 只能靠魔数校验，不做尺寸要求 */
    public boolean dimensionReadable(String format) {
        return DIMENSION_READABLE_FORMATS.contains(format);
    }

    /** 下载文件名：清理非法字符，仅保留与格式匹配的扩展名；名称为空时用 image-{id}.{format} */
    public String downloadFilename(Long id, String name, String format) {
        String cleaned = name == null ? "" : ILLEGAL_FILENAME_CHARS.matcher(name).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return "image-" + id + "." + format;
        }
        int dot = cleaned.lastIndexOf('.');
        boolean hasMatchingExtension = dot > 0 && dot < cleaned.length() - 1
                && cleaned.substring(dot + 1).equalsIgnoreCase(format);
        return hasMatchingExtension ? cleaned : cleaned + "." + format;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[HEADER_LENGTH];
            int read = inputStream.readNBytes(header, 0, HEADER_LENGTH);
            return read == HEADER_LENGTH ? header : Arrays.copyOf(header, Math.max(read, 0));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取上传文件失败");
        }
    }

    /** 文件头魔数校验，避免改扩展名把非图片文件传上来 */
    private static boolean matchesMagic(byte[] header, String format) {
        return switch (format) {
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A;
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }
}