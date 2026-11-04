package com.common.system.sys.att.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 附件模块配置
 *
 * @author Eric
 * @date 2026/8/7
 * @description 读取 application.properties 中 common.att.storage-path 配置，
 *              即附件物理文件存储根目录；field_file_path 存储相对该目录的路径。
 */
@Component
public class SysAttProperties {

    private final String storagePath;

    public SysAttProperties(@Value("${common.att.storage-path:./data/att}") String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStoragePath() {
        return storagePath;
    }
}
