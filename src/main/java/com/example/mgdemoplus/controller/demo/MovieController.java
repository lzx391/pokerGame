package com.example.mgdemoplus.controller.demo;

import com.example.mgdemoplus.entity.demo.Movie;
import com.example.mgdemoplus.service.demo.MovieService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/movie")
public class MovieController {
    @Autowired
    private MovieService movieService;
    @GetMapping("/getMovie")
    public List<Movie>  getMovie(){
        System.out.println("正在请求获取Movie集合");
        return movieService.getMovie();
    }
}
