package com.example.mgdemoplus.mapper.demo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.example.mgdemoplus.entity.demo.Movie;

import java.util.List;

@Mapper
public interface MovieMapper {
    @Select("select * from tbl_movie")
    public List<Movie> getMovie();
}
