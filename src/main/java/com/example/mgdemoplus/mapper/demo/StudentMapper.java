package com.example.mgdemoplus.mapper.demo;

import org.apache.ibatis.annotations.*;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.example.mgdemoplus.entity.demo.Student;

import java.util.List;

@Mapper
public interface StudentMapper {
    @Select("select * from tbl_student")//自动找配置文件的数据库的表
    public List<Student> getStudent();
    @Insert("insert into tbl_student (name,password) values (#{name},#{password})")
    public  int insertStudent(Student student);
    @Update("update  tbl_student set name=#{name},password=#{password} where id=#{id}")
    public int updateStudent( Student student);
    @Delete("delete from tbl_student where id=#{id}")
    public int deleteStudent(int id);
    @Select("select * from tbl_student where id=#{id}")
    public Student selectStudentById(int id);
}
