package com.example.mgdemoplus.gallery.mapper;

import com.example.mgdemoplus.gallery.entity.DpGalleryLetter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpLetterMapper {

    @Insert("""
            INSERT INTO dp_gallery_letter (user_id, content)
            VALUES (#{userId}, #{content})
            ON DUPLICATE KEY UPDATE content = #{content}, updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("userId") int userId, @Param("content") String content);

    @Select("""
            SELECT user_id AS userId, content, created_at AS createdAt, updated_at AS updatedAt
            FROM dp_gallery_letter
            WHERE user_id = #{userId}
            """)
    DpGalleryLetter selectByUserId(@Param("userId") int userId);

    @Delete("DELETE FROM dp_gallery_letter WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);
}
