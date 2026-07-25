-- Flyway V26: RBAC 角色/权限与用户绑定

CREATE TABLE dp_role (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键',
    code       VARCHAR(32)  NOT NULL COMMENT '角色编码，如 PLAYER、ADMIN',
    name       VARCHAR(64)  NOT NULL COMMENT '角色名称',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_dp_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='RBAC 角色';

CREATE TABLE dp_permission (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '权限主键',
    code       VARCHAR(64)   NOT NULL COMMENT '权限编码，如 game:hole_cards:view',
    name       VARCHAR(128)  NOT NULL COMMENT '权限名称',
    created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_dp_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='RBAC 权限';

CREATE TABLE dp_role_permission (
    role_id       BIGINT NOT NULL COMMENT 'dp_role.id',
    permission_id BIGINT NOT NULL COMMENT 'dp_permission.id',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_dp_role_permission_permission (permission_id),
    CONSTRAINT fk_dp_role_permission_role FOREIGN KEY (role_id) REFERENCES dp_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_role_permission_permission FOREIGN KEY (permission_id) REFERENCES dp_permission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色-权限关联';

CREATE TABLE dp_user_role (
    user_id INT    NOT NULL COMMENT 'dp_user.id',
    role_id BIGINT NOT NULL COMMENT 'dp_role.id',
    PRIMARY KEY (user_id, role_id),
    KEY idx_dp_user_role_role (role_id),
    CONSTRAINT fk_dp_user_role_user FOREIGN KEY (user_id) REFERENCES dp_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_user_role_role FOREIGN KEY (role_id) REFERENCES dp_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户-角色关联';

INSERT INTO dp_role (code, name) VALUES
    ('PLAYER', '普通用户'),
    ('ADMIN', '管理员');

INSERT INTO dp_permission (code, name) VALUES
    ('game:hole_cards:view', '看牌');

INSERT INTO dp_user_role (user_id, role_id)
SELECT u.id, r.id
FROM dp_user u
CROSS JOIN dp_role r
WHERE r.code = 'PLAYER'
  AND NOT EXISTS (
      SELECT 1 FROM dp_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
