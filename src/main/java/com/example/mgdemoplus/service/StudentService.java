package com.example.mgdemoplus.service;

import com.example.mgdemoplus.entity.Student;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface StudentService {
    public List<Student> getStudent();
    public  int insertStudent(Student student);
    public int updateStudent( Student student);
    public int deleteStudent(int id);
    public Student selectStudentById(int id);
}
