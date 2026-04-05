package com.example.mgdemoplus.service.demo;

import org.springframework.web.bind.annotation.RequestBody;

import com.example.mgdemoplus.entity.demo.Student;

import java.util.List;

public interface StudentService {
    List<Student> getStudent();
    int insertStudent(Student student);
    int updateStudent(Student student);
    int deleteStudent(int id);
    Student selectStudentById(int id);
}
