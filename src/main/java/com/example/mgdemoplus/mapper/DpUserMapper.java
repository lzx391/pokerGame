package com.example.mgdemoplus.mapper;

import com.example.mgdemoplus.entity.DpUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpUserMapper {
    @Insert("INSERT INTO dp_user (nickname,password) values (#{nickname},#{password})")
    public  int registerUser (DpUser dpUser);
    @Select("select * from dp_user where id = #{id}")
    public DpUser selectById(int id);
    @Select("SELECT * from dp_user where nickname= #{nickname} and password = #{password}")
    public DpUser loginUser(String nickname,String password);
    @Select("SELECT * from dp_user where nickname= #{nickname}" )
    public DpUser selectByNickname(String nickname);
}
