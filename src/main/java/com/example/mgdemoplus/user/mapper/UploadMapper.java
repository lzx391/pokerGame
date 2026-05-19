package com.example.mgdemoplus.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

public interface UploadMapper {
    @Update("update dp_user set avatar_url = #{url} where id =#{id}")
    int upload(int id ,String url);
}
