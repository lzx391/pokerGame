package com.example.mgdemoplus.mapper;

import com.example.mgdemoplus.entity.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MovieMapper {
    @Select("select * from tbl_movie")
    public List<Movie> getMovie();
}
