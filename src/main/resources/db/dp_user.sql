-- school_db.dp_user definition

CREATE TABLE `dp_user` (
                           `id` int NOT NULL AUTO_INCREMENT,
                           `nickname` varchar(10) NOT NULL,
                           `password` varchar(64) NOT NULL ,
                           `avatar_url` varchar(255) DEFAULT NULL,
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;