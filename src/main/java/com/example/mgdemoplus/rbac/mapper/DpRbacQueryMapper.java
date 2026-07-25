package com.example.mgdemoplus.rbac.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DpRbacQueryMapper {

    @Select("""
            SELECT DISTINCT p.code
            FROM dp_permission p
            INNER JOIN dp_role_permission rp ON rp.permission_id = p.id
            INNER JOIN dp_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") int userId);
}
