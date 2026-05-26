package com.example.mgdemoplus.download.mapper;

import com.example.mgdemoplus.download.entity.DpDownloadAsset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpDownloadAssetMapper {

    @Insert("""
            INSERT INTO dp_download_asset
            (stored_filename, display_name, web_path, sort_order, enabled, uploader_user_id)
            VALUES (#{storedFilename}, #{displayName}, #{webPath}, #{sortOrder}, 1, #{uploaderUserId});
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DpDownloadAsset row);

    @Select("""
            SELECT id, stored_filename AS storedFilename,
            display_name AS displayName, web_path AS webPath,
            sort_order AS sortOrder, enabled, uploader_user_id AS uploaderUserId,
            created_at AS createdAt, updated_at AS updatedAt
            FROM dp_download_asset WHERE enabled = 1 ORDER BY sort_order DESC, id DESC;
            """)
    List<DpDownloadAsset> listEnabled();
}
