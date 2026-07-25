package com.example.mgdemoplus.rbac.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpRolePermissionMapper {

    @Select("SELECT permission_id FROM dp_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") long roleId);

    @Delete("DELETE FROM dp_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Insert("INSERT INTO dp_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") long roleId, @Param("permissionId") long permissionId);
}
