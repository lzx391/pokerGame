package com.example.mgdemoplus.storage.impl;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MinIO 对象存储：object key = webPath 去掉 leading {@code /}。
 */
public class DpMinioObjectStorage implements DpObjectStorage {

    private final MinioClient minioClient;
    private final String bucket;

    public DpMinioObjectStorage(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void put(String webPath, InputStream data, long size, String contentType) throws IOException {
        String key = requireObjectKey(webPath);
        String ct = contentType != null && !contentType.isBlank()
                ? contentType
                : DpWebPathSupport.guessContentType(webPath);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(data, size >= 0 ? size : -1, 10 * 1024 * 1024)
                            .contentType(ct)
                            .build());
        } catch (Exception e) {
            throw new IOException("minio put failed key=" + key, e);
        }
    }

    @Override
    public Optional<StoredObject> get(String webPath) {
        String key = objectKeyOrNull(webPath);
        if (key == null) {
            return Optional.empty();
        }
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(key).build());
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build());
            String contentType = stat.contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = DpWebPathSupport.guessContentType(webPath);
            }
            return Optional.of(new StoredObject(stream, stat.size(), contentType));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String webPath) {
        String key = objectKeyOrNull(webPath);
        if (key == null) {
            return false;
        }
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            return !"NoSuchKey".equals(e.errorResponse().code()) && false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void delete(String webPath) throws IOException {
        String key = objectKeyOrNull(webPath);
        if (key == null) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IOException("minio delete failed key=" + key, e);
        }
    }

    @Override
    public List<String> listObjectKeysByPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(keyPrefix).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item != null && !item.isDir()) {
                    keys.add(item.objectName());
                }
            }
        } catch (Exception e) {
            // 列表失败时返回已收集项（通常为空）
        }
        return keys;
    }

    private static String requireObjectKey(String webPath) throws IOException {
        String key = objectKeyOrNull(webPath);
        if (key == null) {
            throw new IOException("invalid webPath: " + webPath);
        }
        return key;
    }

    private static String objectKeyOrNull(String webPath) {
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            return null;
        }
        return DpWebPathSupport.webPathToObjectKey(webPath);
    }
}
