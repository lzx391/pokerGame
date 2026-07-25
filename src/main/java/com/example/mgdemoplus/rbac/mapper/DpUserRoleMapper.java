package com.example.mgdemoplus.rbac.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpUserRoleMapper {

    @Select("SELECT role_id FROM dp_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") int userId);

    @Delete("DELETE FROM dp_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);

    @Insert("INSERT INTO dp_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") int userId, @Param("roleId") long roleId);

    @Select("SELECT DISTINCT user_id FROM dp_user_role WHERE role_id = #{roleId}")
    List<Integer> selectUserIdsByRoleId(@Param("roleId") long roleId);
}
