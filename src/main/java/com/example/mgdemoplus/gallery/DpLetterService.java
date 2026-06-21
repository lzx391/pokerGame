package com.example.mgdemoplus.gallery;

import com.example.mgdemoplus.gallery.entity.DpGalleryLetter;

public interface DpLetterService {

    int WRITE_OK = 1;
    int WRITE_FAIL = 0;
    int WRITE_SENSITIVE = 2;

    int writeLetter(String letter);

    DpGalleryLetter getMyLetter();

    /** 指定用户的手写信；用户不存在时返回 {@code null}。 */
    DpGalleryLetter getLetterByUserId(int userId);

    int clearMyLetter();
}
