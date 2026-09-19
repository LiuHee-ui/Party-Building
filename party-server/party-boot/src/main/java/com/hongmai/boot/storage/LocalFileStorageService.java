package com.hongmai.boot.storage;

import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;
import com.hongmai.common.storage.FileStorageService;
import com.hongmai.common.storage.UploadTicket;
import com.hongmai.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现（开发环境）。
 *
 * 生产环境替换为对象存储实现：新增一个实现 FileStorageService 的 Bean，
 * 用 @ConditionalOnProperty(storage.provider=cos) 切换，业务代码不需要改动。
 * 与对象存储的差异点：本地实现没有真正的「直传」，uploadUrl 仍指向本服务，
 * 接入云存储后该方法会返回预签名 URL。
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final Path root;
    private final String publicBaseUrl;

    public LocalFileStorageService(String rootDir, String publicBaseUrl) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地存储目录: " + this.root, e);
        }
    }

    @Override
    public UploadTicket createUploadTicket(String bizDir, String fileName, long expireSeconds) {
        String objectKey = buildObjectKey(bizDir, fileName);
        String url = publicBaseUrl + "/files/upload/" + objectKey;
        return UploadTicket.of(objectKey, url, DateUtil.now().plusSeconds(expireSeconds));
    }

    @Override
    public String buildDownloadUrl(String objectKey, long expireSeconds) {
        return publicBaseUrl + "/files/download/" + objectKey;
    }

    @Override
    public String write(String bizDir, String fileName, byte[] content, String contentType) {
        String objectKey = buildObjectKey(bizDir, fileName);
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("本地文件写入失败 objectKey={}", objectKey, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件写入失败");
        }
        return objectKey;
    }

    @Override
    public byte[] read(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件不存在: " + objectKey);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("本地文件读取失败 objectKey={}", objectKey, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            log.warn("本地文件删除失败 objectKey={}", objectKey, e);
        }
    }

    /** 对象键格式：bizDir/yyyyMM/uuid.ext，避免同名覆盖与单目录文件过多。 */
    private String buildObjectKey(String bizDir, String fileName) {
        String safeDir = StringUtils.hasText(bizDir) ? bizDir.replaceAll("[^a-zA-Z0-9/_-]", "") : "misc";
        if (safeDir.startsWith("/")) {
            safeDir = safeDir.substring(1);
        }
        String ext = "";
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            ext = "." + fileName.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "");
        }
        String month = String.format("%04d%02d", DateUtil.today().getYear(), DateUtil.today().getMonthValue());
        return safeDir + "/" + month + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    /**
     * 解析对象键为真实路径，并强制校验结果仍落在根目录内——
     * 防止 objectKey 中出现 ../ 造成目录穿越读取任意文件。
     */
    private Path resolve(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对象键不能为空");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法的对象键");
        }
        return resolved;
    }
}
