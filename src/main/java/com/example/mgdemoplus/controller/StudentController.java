package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.entity.Student;
import com.example.mgdemoplus.service.StudentService;
import org.apache.ibatis.annotations.Update;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/student")
public class StudentController {
    @Autowired
    private StudentService studentService;
    @GetMapping("/getStudent")
    public List<Student> getStudent(){
        System.out.println("正在获取学生");
        List<Student> studentList =studentService.getStudent();
        System.out.println(studentList);
        return studentService.getStudent();
    }
    @PostMapping("/insertStudent")
    public String insertStudent(@RequestBody  Student student){
        System.out.println("正在存入学生");
        if(studentService.insertStudent(student)==1){
            return "插入成功";
        }
        return "插入失败";
    }
    @PutMapping("/updateStudent")
    public String updateStudent(@RequestBody Student student){
        System.out.println("用户传进来的是"+student.toString());
        if(studentService.updateStudent(student)==1){
            System.out.println("更新成功");
            return "更新成功";
        }

        System.out.println("更新失败");

        return "更新失败";
    }
    @DeleteMapping("/deleteStudent/{id}")
    public String deleteStudent(@PathVariable int id){
        studentService.selectStudentById(id);
        if(studentService.deleteStudent(id)==1){
            System.out.println("删除成功");
            return "删除成功";
        }
        System.out.println("删除失败");
        return "删除失败";
    }
    @GetMapping("/selectStudentById/{id}")
    public Student selectStudentById(@PathVariable int id){
        System.out.println("正在查找id为"+id+"的学生");
        Student student=studentService.selectStudentById(id);
        if(student==null){
            System.out.println("不存在");
        }
        return student;
    }
    @GetMapping
    public String mom(){
        return "老妈你好";
    }
}
