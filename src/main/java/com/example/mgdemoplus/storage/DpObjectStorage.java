package com.example.mgdemoplus.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 统一对象存储：webPath 形如 {@code /images/12.jpg}；MinIO object key 为去掉 leading {@code /} 的形式。
 */
public interface DpObjectStorage {

    void put(String webPath, InputStream data, long size, String contentType) throws IOException;

    Optional<StoredObject> get(String webPath);

    boolean exists(String webPath);

    void delete(String webPath) throws IOException;

    /** 列出 object key 前缀匹配项（用于头像批量删除等）。 */
    List<String> listObjectKeysByPrefix(String keyPrefix);

    record StoredObject(InputStream stream, long size, String contentType) {}
}
