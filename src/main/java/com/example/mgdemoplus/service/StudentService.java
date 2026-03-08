package com.example.mgdemoplus.service;

import com.example.mgdemoplus.entity.Student;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface StudentService {
    List<Student> getStudent();
    int insertStudent(Student student);
    int updateStudent(Student student);
    int deleteStudent(int id);
    Student selectStudentById(int id);
}
