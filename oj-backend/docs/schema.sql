CREATE DATABASE IF NOT EXISTS `oj_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `oj_db`;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `tb_user` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
                                         `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码密文 (BCrypt 加密)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER / ADMIN / ROOT',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息表';

-- 2. 题目表 (增加 is_public)
CREATE TABLE IF NOT EXISTS `tb_problem` (
                                            `id` BIGINT NOT NULL COMMENT '题目ID (发号器发放)',
                                            `title` VARCHAR(128) NOT NULL COMMENT '题目标题',
    `description` MEDIUMTEXT NOT NULL COMMENT 'Markdown 题面描述',
    `time_limit_ms` INT NOT NULL DEFAULT 1000 COMMENT '时间限制 (毫秒)',
    `memory_limit_mb` INT NOT NULL DEFAULT 256 COMMENT '内存限制 (MB)',
    `author` VARCHAR(64) DEFAULT 'Admin' COMMENT '出题人',
    `is_public` TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开: 1-公开, 0-比赛保密',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_is_public` (`is_public`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目元数据表';

-- 3. 题解表 (增加 is_deleted)
CREATE TABLE IF NOT EXISTS `tb_solution` (
                                             `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题解ID',
                                             `problem_id` BIGINT NOT NULL COMMENT '所属题目ID',
                                             `user_id` BIGINT NOT NULL COMMENT '作者用户ID',
                                             `title` VARCHAR(128) NOT NULL COMMENT '题解标题',
    `content` MEDIUMTEXT NOT NULL COMMENT '题解 Markdown 正文',
    `likes` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑软删除: 0-未删除, 1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_problem` (`problem_id`),
    INDEX `idx_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目解题思路与题解表';

-- 4. 提交评测记录表 (增加 contest_id)
CREATE TABLE IF NOT EXISTS `tb_submission` (
                                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提交唯一ID',
                                               `user_id` BIGINT NOT NULL COMMENT '提交用户ID',
                                               `problem_id` BIGINT NOT NULL COMMENT '对应题目ID',
                                               `contest_id` BIGINT DEFAULT NULL COMMENT '所属比赛ID (NULL为日常练习)',
                                               `language` VARCHAR(20) NOT NULL DEFAULT 'cpp' COMMENT '编程语言',
    `code` MEDIUMTEXT NOT NULL COMMENT '源代码',
    `verdict` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态码',
    `max_time_ms` INT DEFAULT 0 COMMENT '消耗时间(ms)',
    `peak_memory_kb` INT DEFAULT 0 COMMENT '峰值内存(KB)',
    `judge_details` JSON COMMENT '明细报告',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_problem` (`problem_id`),
    INDEX `idx_contest` (`contest_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交评测记录表';

-- 5. 比赛信息表
CREATE TABLE IF NOT EXISTS `tb_contest` (
                                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                                            `title` VARCHAR(128) NOT NULL,
    `description` MEDIUMTEXT NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛信息表';

-- 6. 比赛题目映射表
CREATE TABLE IF NOT EXISTS `tb_contest_problem` (
                                                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                    `contest_id` BIGINT NOT NULL,
                                                    `problem_id` BIGINT NOT NULL,
                                                    `display_id` VARCHAR(10) NOT NULL,
    `display_title` VARCHAR(128) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contest_display` (`contest_id`, `display_id`),
    INDEX `idx_contest_id` (`contest_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛题目映射关联表';

-- 7. 社区帖子/公告表 (预留)
CREATE TABLE IF NOT EXISTS `tb_post` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT,
                                         `user_id` BIGINT NOT NULL,
                                         `title` VARCHAR(128) NOT NULL,
    `content` MEDIUMTEXT NOT NULL,
    `category` VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    `related_id` BIGINT DEFAULT NULL,
    `views` INT NOT NULL DEFAULT 0,
    `likes` INT NOT NULL DEFAULT 0,
    `is_pinned` TINYINT NOT NULL DEFAULT 0,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_category` (`category`),
    INDEX `idx_related` (`related_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区讨论与公告帖子表';

-- 8. 比赛报名参赛人员表
CREATE TABLE IF NOT EXISTS `tb_contest_user` (
                                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                 `contest_id` BIGINT NOT NULL,
                                                 `user_id` BIGINT NOT NULL,
                                                 `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contest_user` (`contest_id`, `user_id`),
    INDEX `idx_contest` (`contest_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛报名人员表';

-- =========================================================
-- 初始化初始数据：内置 1 号初始 ROOT 账号
-- 用户名: root
-- 明文密码: roo@123#@!
-- 密文哈希: $2a$10$7R9N/sQhZ6.gK1gC1y2yauL8Fj9U1FqL9W9V2xX0z1Y2A3B4C5D6E (标准 BCrypt 生成)
-- =========================================================
INSERT INTO `tb_user` (`id`, `username`, `password`, `role`)
VALUES (1, 'root', '$2a$10$dK5j9o5vM9m8f5r2y5U2Vu0K5Q2j6K7R9xL0s5w8z1Y2A3B4C5D6E', 'ROOT')
    ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);