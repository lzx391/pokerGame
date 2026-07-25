package com.example.mgdemoplus.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.rbac.entity.DpRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DpRoleMapper extends BaseMapper<DpRole> {

    @Select("SELECT id FROM dp_role WHERE code = #{code} LIMIT 1")
    Long selectIdByCode(String code);
}
