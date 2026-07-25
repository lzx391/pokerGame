package com.example.mgdemoplus.gallery.mapper;

import com.example.mgdemoplus.gallery.entity.DpGalleryItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DpGalleryItemMapper {

    @Insert("""
            INSERT INTO dp_gallery_item (user_id, image_url, caption, sort_order)
            VALUES (#{userId}, #{imageUrl}, #{caption}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DpGalleryItem item);

    @Select("""
            SELECT id, user_id AS userId, image_url AS imageUrl, caption, sort_order AS sortOrder,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM dp_gallery_item
            WHERE user_id = #{userId}
            ORDER BY created_at ASC, id ASC
            """)
    List<DpGalleryItem> listByUserId(@Param("userId") int userId);

    @Select("""
            SELECT id, user_id AS userId, image_url AS imageUrl, caption, sort_order AS sortOrder,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM dp_gallery_item
            WHERE id = #{id} AND user_id = #{userId}
            """)
    DpGalleryItem selectByIdAndUserId(@Param("id") long id, @Param("userId") int userId);

    @Update("""
            UPDATE dp_gallery_item
            SET caption = #{caption}, sort_order = #{sortOrder}
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateByIdAndUserId(DpGalleryItem item);

    @Delete("DELETE FROM dp_gallery_item WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("id") long id, @Param("userId") int userId);
}
