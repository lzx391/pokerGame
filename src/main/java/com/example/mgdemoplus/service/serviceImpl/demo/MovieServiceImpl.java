package com.example.mgdemoplus.service.serviceImpl.demo;

import com.example.mgdemoplus.entity.demo.Movie;
import com.example.mgdemoplus.mapper.demo.MovieMapper;
import com.example.mgdemoplus.service.demo.MovieService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class MovieServiceImpl implements MovieService {
    @Autowired
    private MovieMapper movieMapper;
    public List<Movie> getMovie(){
        return movieMapper.getMovie();
    }
}
