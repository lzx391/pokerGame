package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 已废弃：原 {@code POST /upload?id=} 允许客户端篡改用户 id。
 * 请使用 {@link DpUserController#uploadAvatar}（{@code POST /dpUser/avatar}，JWT 鉴权）。
 */
@RestController
@Deprecated
public class UploadController {

    @PostMapping("/upload")
    public ResponseEntity<ResultUtil> upload(
            @RequestParam(required = false) Integer id,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(
                        ResultUtil.error()
                                .data(
                                        "message",
                                        "接口已下线，请使用 POST /dpUser/avatar 上传头像（须登录）"));
    }
}
