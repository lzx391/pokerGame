package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.common.entity.DpUser;

import com.example.mgdemoplus.gallery.DpGalleryItemService;

import com.example.mgdemoplus.gallery.DpLetterService;

import com.example.mgdemoplus.gallery.dto.DpGalleryUploadResult;

import com.example.mgdemoplus.gallery.entity.DpGalleryLetter;

import com.example.mgdemoplus.gallery.vo.DpGalleryItemView;

import com.example.mgdemoplus.security.DpCurrentUserSupport;

import com.example.mgdemoplus.user.impl.DpUserServiceImpl;

import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.stream.Collectors;

@RestController

@RequestMapping("/dp/gallery")

public class DpGalleryController {

    @Autowired
    private DpCurrentUserSupport currentUserSupport;
    @Autowired
    private DpLetterService dpLetterService;
    @Autowired
    private DpGalleryItemService dpGalleryItemService;
    @PostMapping("/writeLetter")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil writeLetter(@RequestParam("letter") String letter) {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        if (letter == null || letter.isBlank()) {

            return ResultUtil.error().data("message", "手写信内容不能为空");

        }

        int result = dpLetterService.writeLetter(letter);

        if (result == DpLetterService.WRITE_OK) {

            return ResultUtil.ok().data("message", "手写信内容填写成功");

        }

        if (result == DpLetterService.WRITE_SENSITIVE) {

            return ResultUtil.error().data("message", DpUserServiceImpl.MSG_SENSITIVE);

        }

        return ResultUtil.error().data("message", "手写信内容填写失败");

    }
    @GetMapping("/letter")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil getLetter() {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        DpGalleryLetter letter = dpLetterService.getMyLetter();

        if (letter == null) {

            return ResultUtil.ok().data("letter", null);

        }

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("content", letter.getContent());

        body.put("createdAt", letter.getCreatedAt());

        body.put("updatedAt", letter.getUpdatedAt());

        return ResultUtil.ok().data("letter", body);

    }

    @DeleteMapping("/letter")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")

    public ResultUtil clearLetter() {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        if (dpLetterService.clearMyLetter() == DpLetterService.WRITE_OK) {

            return ResultUtil.ok().data("message", "手写信已清除");

        }

        return ResultUtil.error().data("message", "清除手写信失败");

    }

    @PostMapping("/items")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")

    public ResultUtil uploadItem(

            @RequestParam("file") MultipartFile file,

            @RequestParam(value = "caption", required = false) String caption,

            @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        DpGalleryUploadResult result = dpGalleryItemService.uploadItem(file, caption, sortOrder);

        if (!result.isSuccess()) {

            return ResultUtil.error().data("message", result.getMessage());

        }

        return ResultUtil.ok().data("item", result.getItem().toMap());

    }

    @GetMapping("/items")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")

    public ResultUtil listItems() {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        List<Map<String, Object>> items = dpGalleryItemService.listMyItems().stream()

                .map(DpGalleryItemView::toMap)

                .collect(Collectors.toList());

        return ResultUtil.ok().data("items", items);

    }

    @PutMapping("/items/{id}")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil updateItem(

            @PathVariable("id") long id,

            @RequestParam(value = "caption", required = false) String caption,

            @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        if (caption == null && sortOrder == null) {

            return ResultUtil.error().data("message", "没有需要保存的修改");

        }

        String error = dpGalleryItemService.updateItem(id, caption, sortOrder);

        if (error != null) {

            return ResultUtil.error().data("message", error);

        }

        return ResultUtil.ok().data("message", "保存成功");

    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil deleteItem(@PathVariable("id") long id) {

        ResultUtil err = ResultUtil.error();

        DpUser user = currentUserSupport.requireUser(err);

        if (user == null) {

            return err.data("message", err.getMessage());

        }

        String error = dpGalleryItemService.deleteItem(id);

        if (error != null) {

            return ResultUtil.error().data("message", error);

        }

        return ResultUtil.ok().data("message", "删除成功");

    }

    /**
     * 查看指定用户的公开画廊（须登录且具备 gallery:view 权限）。
     */
    @GetMapping("/users/{userId}/items")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil listUserItems(@PathVariable("userId") int userId) {
        ResultUtil err = ResultUtil.error();
        if (currentUserSupport.requireUser(err) == null) {
            return err.data("message", err.getMessage());
        }
        List<DpGalleryItemView> items = dpGalleryItemService.listItemsByUserId(userId);
        if (items == null) {
            return ResultUtil.error().data("message", "用户不存在");
        }
        List<Map<String, Object>> body = items.stream()
                .map(DpGalleryItemView::toPublicMap)
                .collect(Collectors.toList());
        return ResultUtil.ok().data("items", body);
    }

    /**
     * 查看指定用户的手写信（须登录且具备 gallery:view 权限）。
     */
    @GetMapping("/users/{userId}/letter")
    @PreAuthorize("@dpPermissionService.hasPermi(T(com.example.mgdemoplus.rbac.support.DpPermissionCodes).GALLERY_VIEW)")
    public ResultUtil getUserLetter(@PathVariable("userId") int userId) {
        ResultUtil err = ResultUtil.error();
        if (currentUserSupport.requireUser(err) == null) {
            return err.data("message", err.getMessage());
        }
        if (dpGalleryItemService.listItemsByUserId(userId) == null) {
            return ResultUtil.error().data("message", "用户不存在");
        }
        DpGalleryLetter letter = dpLetterService.getLetterByUserId(userId);
        if (letter == null) {
            return ResultUtil.ok().data("letter", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", letter.getContent());
        body.put("createdAt", letter.getCreatedAt());
        body.put("updatedAt", letter.getUpdatedAt());
        return ResultUtil.ok().data("letter", body);
    }
}
