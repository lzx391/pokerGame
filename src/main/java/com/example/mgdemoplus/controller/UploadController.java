package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.service.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class UploadController {
    @Autowired
    private UploadService uploadService;
    @PostMapping("/upload")
    public String upload(@RequestParam int id ,@RequestParam("file") MultipartFile file) throws IOException {
        // 1. 确定本地保存的物理路径
        String filePath = "P:/javaworkspace/MGDemoPlusFiles/";
        File folder = new File(filePath);
        if (!folder.exists()) folder.mkdirs();

        // 2. 给文件起个新名字（防止重复）
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        // 3. 保存到硬盘
        file.transferTo(new File(filePath + fileName));

        // 4. 生成“访问路径”（这就是存入数据库的 URL）
        // 注意：这里不要存 D:/... 这种物理路径，要存 Web 访问路径
        String url = "/images/" + fileName;
        int isSuccess =uploadService.upload(id,url);
        if(isSuccess==1){
            System.out.println(
                    "上传成功"
            );
            return url;
        }else return "上传失败";
        // 5. 执行 SQL 语句（伪代码）：update users set avatar_url = url where id = 1;
        // saveToDatabase(url);

    }
}
