package com.dodosupermarket.im.service;

import com.dodosupermarket.im.config.ImProperties;
import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.exception.ImException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Private, bounded JPEG/PNG MVP. ImageIO decodes and re-encodes; filenames/EXIF/URLs are not trusted.
 */
public class ImImageService {
    private static final Logger LOG = LoggerFactory.getLogger(ImImageService.class);
    public static final int MAX_BYTES = 1024 * 1024;
    private final ImStore store;
    private final ImAccountService accounts;
    private final Path root;

    public ImImageService(ImStore store, ImAccountService accounts, ImProperties config) {
        this.store = store;
        this.accounts = accounts;
        root = config.getAttachmentDirectory().isBlank() ? null : Path.of(config.getAttachmentDirectory()).toAbsolutePath().normalize();
        if (root != null) try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("IM private attachment volume unavailable");
        }
    }

    public boolean enabled() {
        return root != null;
    }

    private void required() {
        if (!enabled()) throw new ImException(503, "IM_IMAGES_DISABLED");
    }

    private Map<String, Object> find(String conversation, String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new ImException(400, "IM_INVALID_ATTACHMENT");
        List<Map<String, Object>> rows = store.attachments(conversation, id);
        if (rows.isEmpty()) throw new ImException(404, "IM_ATTACHMENT_NOT_FOUND");
        return rows.get(0);
    }

    private Map<String, Object> metadata(Map<String, Object> row) {
        return Map.of("attachmentId", row.get("id"), "width", row.get("width"), "height", row.get("height"),
                "mime", row.get("mime"), "byteSize", row.get("byte_size"));
    }

    public Map<String, Object> upload(HttpSession session, String conversation, InputStream stream, long length) {
        ImStore.Lease lease = accounts.requireReader(session, conversation);
        required();
        if (length <= 0 || length > MAX_BYTES) throw new ImException(413, "IM_IMAGE_TOO_LARGE");
        final byte[] bytes;
        final int width, height;
        final String mime;
        try {
            byte[] original = stream.readNBytes(MAX_BYTES + 1);
            if (original.length > MAX_BYTES) throw new ImException(413, "IM_IMAGE_TOO_LARGE");
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new ImException(400, "IM_INVALID_IMAGE");
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw new ImException(400, "IM_INVALID_IMAGE");
                    reader.setInput(input, true, true);
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 12000000)
                        throw new ImException(400, "IM_IMAGE_DIMENSIONS_EXCEEDED");
                    BufferedImage image = reader.read(0);
                    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, format, encoded)) throw new ImException(400, "IM_INVALID_IMAGE");
                    bytes = encoded.toByteArray();
                    mime = format.equals("jpeg") ? "image/jpeg" : "image/png";
                    if (bytes.length > MAX_BYTES) throw new ImException(413, "IM_IMAGE_TOO_LARGE");
                } finally {
                    reader.dispose();
                }
            }
        } catch (ImException e) {
            throw e;
        } catch (Exception e) {
            throw new ImException(400, "IM_INVALID_IMAGE");
        }
        final String digest;
        try {
            digest = hash(bytes);
        } catch (Exception e) {
            throw new ImException(503, "IM_ATTACHMENT_STORAGE_FAILED");
        }
        // 先在事务内占用配额并落库，事务提交后再写文件：
        // 事务提交失败不会留下没有记录的孤儿文件；写文件失败则回滚记录，避免"有记录没文件"。
        String id = store.locked(conversation, () -> {
            accounts.requireReader(session, conversation);
            Map<String, Object> usage = store.attachmentUsage(conversation, lease.getUserId(), System.currentTimeMillis() - 86400000);
            if (((Number) usage.get("count")).longValue() >= 100 || ((Number) usage.get("bytes")).longValue() + bytes.length > 20L * MAX_BYTES)
                throw new ImException(429, "IM_IMAGE_QUOTA_EXCEEDED");
            String attachmentId = ImStore.randomId();
            store.insertAttachment(attachmentId, conversation, lease.getUserId(), mime, width, height, bytes.length, digest, System.currentTimeMillis());
            return attachmentId;
        });
        Path file = root.resolve(id);
        try {
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) {
            try {
                store.deleteAttachment(id);
            } catch (Exception ignored) {
                LOG.warn("IM attachment record could not be rolled back after a storage failure");
            }
            throw new ImException(503, "IM_ATTACHMENT_STORAGE_FAILED");
        }
        return metadata(find(conversation, id));
    }

    public Map<String, Object> payload(HttpSession session, String conversation, String id) {
        ImStore.Lease lease = accounts.requireReader(session, conversation);
        required();
        Map<String, Object> row = find(conversation, id);
        if (((Number) row.get("user_id")).longValue() != lease.getUserId())
            throw new ImException(403, "IM_ATTACHMENT_NOT_OWNED");
        Map<String, Object> payload = new LinkedHashMap<>(metadata(row));
        payload.remove("byteSize");
        payload.put("type", 2);
        return payload;
    }

    public static class Download {
        private final byte[] bytes;
        private final String mime;

        Download(byte[] bytes, String mime) {
            this.bytes = bytes;
            this.mime = mime;
        }

        public byte[] bytes() {
            return bytes;
        }

        public String mime() {
            return mime;
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x", b & 255));
        return value.toString();
    }

    public Download download(HttpSession session, String conversation, String id) {
        ImStore.Lease lease = accounts.requireReader(session, conversation);
        required();
        return store.locked(conversation, () -> {
            accounts.requireReader(session, conversation);
            Map<String, Object> row = find(conversation, id);
            if (((Number) row.get("user_id")).longValue() != lease.getUserId()) {
                if (!store.hasAcceptedAttachment(conversation, id))
                    throw new ImException(404, "IM_ATTACHMENT_NOT_FOUND");
            }
            Path file = root.resolve(id);
            try {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
                    throw new IOException();
                byte[] bytes = Files.readAllBytes(file);
                if (!hash(bytes).equals(row.get("sha256"))) throw new IOException();
                return new Download(bytes, (String) row.get("mime"));
            } catch (Exception e) {
                throw new ImException(503, "IM_ATTACHMENT_STORAGE_FAILED");
            }
        });
    }
}
