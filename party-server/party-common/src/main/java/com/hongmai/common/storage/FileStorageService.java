package com.hongmai.common.storage;

/**
 * 文件存储抽象。
 *
 * 本期提供本地磁盘实现（LocalFileStorageService，位于 party-boot），
 * 生产环境替换为对象存储实现，接口不变、调用方零改动。
 * 之所以先抽象：封面图、字幕文件、身份证明、导出台账四处都要落文件，
 * 若直接写死某家云 SDK，后续换供应商要改四处业务代码。
 */
public interface FileStorageService {

    /**
     * 生成小程序端直传凭证。直传的意义是图片与视频封面不过服务端带宽。
     * 本地实现返回指向本服务的上传地址；对象存储实现返回预签名 URL 与必要表单字段。
     */
    UploadTicket createUploadTicket(String bizDir, String fileName, long expireSeconds);

    /** 由对象键生成带时效的下载链接（导出台账、内网回读用）。 */
    String buildDownloadUrl(String objectKey, long expireSeconds);

    /** 服务端直接写入字节，返回对象键。用于导出 xlsx 等由服务端生成的文件。 */
    String write(String bizDir, String fileName, byte[] content, String contentType);

    /** 读取文件内容，对象不存在时抛 BizException。 */
    byte[] read(String objectKey);

    /** 删除文件。对象不存在时静默返回，不抛异常。 */
    void delete(String objectKey);
}
