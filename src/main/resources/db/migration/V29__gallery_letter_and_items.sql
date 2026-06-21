-- Flyway V29: 画廊手写信与用户画廊条目

CREATE TABLE dp_gallery_letter (
    user_id    INT          NOT NULL COMMENT 'dp_user.id，一用户一条',
    content    TEXT         NOT NULL COMMENT '手写信正文',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_dp_gallery_letter_user FOREIGN KEY (user_id) REFERENCES dp_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户手写信（与用户一对一）';

CREATE TABLE dp_gallery_item (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    INT          NOT NULL COMMENT 'dp_user.id',
    image_url  VARCHAR(512) NULL DEFAULT NULL COMMENT '图片访问路径，可空（纯文字条目）',
    caption    TEXT         NULL DEFAULT NULL COMMENT '文本介绍/说明',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '展示排序，越大越靠前',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_dp_gallery_item_user_sort (user_id, sort_order),
    CONSTRAINT fk_dp_gallery_item_user FOREIGN KEY (user_id) REFERENCES dp_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户画廊条目（图片+文字，一对多）';
