package com.example.mgdemoplus.common.mapper;

import com.example.mgdemoplus.common.entity.DpUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DpUserMapper {
    @Insert("INSERT INTO dp_user (nickname,password) values (#{nickname},#{password})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    public int registerUser(DpUser dpUser);
    @Select("select * from dp_user where id = #{id}")
    public DpUser selectById(int id);
    @Select("SELECT * from dp_user where nickname= #{nickname}" )
    public DpUser selectByNickname(String nickname);
    @Update("UPDATE dp_user SET nickname = #{nickname},password = #{password} WHERE id = #{id}")
    public int updateUserInfo(DpUser dpUser);
}
