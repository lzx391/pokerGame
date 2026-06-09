package com.example.mgdemoplus.oauth.mapper;

import com.example.mgdemoplus.oauth.entity.DpSocialAuth;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpSocialAuthMapper {

    @Select("SELECT * FROM dp_user_social_auth WHERE provider = #{provider} AND open_id = #{openId}")
    DpSocialAuth selectByProviderAndOpenId(@Param("provider") String provider, @Param("openId") String openId);

    @Insert("INSERT INTO dp_user_social_auth (user_id, provider, open_id) VALUES (#{userId}, #{provider}, #{openId})")
    int insert(DpSocialAuth auth);
}
