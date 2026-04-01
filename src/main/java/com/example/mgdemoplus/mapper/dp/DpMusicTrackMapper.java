package com.example.mgdemoplus.mapper.dp;

import com.example.mgdemoplus.entity.dp.DpMusicTrack;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpMusicTrackMapper {

    @Insert("INSERT INTO dp_music_track (stored_filename, display_name, web_path, sort_order, enabled, uploader_user_id) "
            + "VALUES (#{storedFilename}, #{displayName}, #{webPath}, #{sortOrder}, 1, #{uploaderUserId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DpMusicTrack row);

    @Select("SELECT id, stored_filename AS storedFilename, display_name AS displayName, web_path AS webPath, "
            + "sort_order AS sortOrder, enabled, uploader_user_id AS uploaderUserId "
            + "FROM dp_music_track WHERE enabled = 1 ORDER BY sort_order DESC, id DESC")
    List<DpMusicTrack> listEnabled();
}
