package com.example.mgdemoplus.service.serviceImpl.demo;

import com.example.mgdemoplus.entity.demo.Student;
import com.example.mgdemoplus.mapper.demo.StudentMapper;
import com.example.mgdemoplus.service.demo.StudentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentServiceImpl implements StudentService {
    @Autowired
    private StudentMapper studentMapper ;
    public List<Student> getStudent(){
        System.out.println("请求到达服务层");
        return studentMapper.getStudent();
    }
    public  int insertStudent(Student student){
        System.out.println("插入进来的数据是"+student.toString());
        return studentMapper.insertStudent(student);
    }
    public int updateStudent(Student student){
        return studentMapper.updateStudent(student);
    }
    public int deleteStudent(int id){
        return studentMapper.deleteStudent(id);
    }
    public Student selectStudentById(int id){
        return studentMapper.selectStudentById(id);
    }
}
