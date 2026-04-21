package com.example.mgdemoplus.mapper.dp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mgdemoplus.entity.dp.DpRoomLobby;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大厅表只读查询（动态条件、分页）使用 MyBatis-Plus；写入仍走 {@link DpRoomLobbyMapper}。
 */
@Mapper
public interface DpRoomLobbyMpMapper extends BaseMapper<DpRoomLobby> {
}
