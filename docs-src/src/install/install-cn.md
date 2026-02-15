---
title: 安装与配置
subTitle: 2024-12-05 by Frank Cheung
description: 安装与配置
date: 2022-01-05
tags:
  - 安装
  - 配置
layout: layouts/docs-cn.njk
---
# 安装与配置

## 系统要求

- Java 8 及以上，Spring 5.0 及以上
- MySQL 8 及以上，当前配置只支持 MySQL 数据库，但 SQL 执行的数据不限 MySQL
- 轻量级设计，对系统要求低，单核/512MB RAM 即可运行服务


## 下载与源码

AJ DataService 足够轻量级，包括依赖包在内的 JAR 包约 200 多kb。

```xml
<!-- AJ-DataService -->
<dependency>
  <groupid>com.ajaxjs</groupid>
  <artifactid>ajaxjs-data</artifactid>
  <version>1.2.1</version>
</dependency>
```

数据服务依赖于 SqlMan 库，亦是本作者开源作品之一。访问 [SqlMan](https://sqlman.ajaxjs.com) 官网获取更多信息。


## 初始化 SQL

数据服务依赖两张表`ds_common_api` 和`ds_project`作为配置数据表。在你的数据库中执行创建表的 DDL，或者执行[init-dataservice.sql]() DDL。


```sql
CREATE TABLE `ds_common_api` (
    `id` INT(10) NOT NULL AUTO_INCREMENT COMMENT '主键 id，自增',
    `pid` INT(10) NULL DEFAULT '-1' COMMENT '父节点 id，如果是顶级节点为 -1 或者空',
    `name` VARCHAR(90) NULL DEFAULT NULL COMMENT '说明',
    `namespace` VARCHAR(50) NOT NULL COMMENT '命名空间，标识',
    `table_name` VARCHAR(45) NULL DEFAULT NULL COMMENT '表名',
    `type` VARCHAR(10) NULL DEFAULT NULL COMMENT '类型 SINGLE | CRUD',
    `clz_name` VARCHAR(10) NULL DEFAULT NULL COMMENT '实体类引用名称',
    `id_field` VARCHAR(10) NULL DEFAULT 'id' COMMENT '主键字段名称',
    `sql` TEXT NULL DEFAULT NULL COMMENT '单条 SQL 命令',
    `info_sql` TEXT NULL DEFAULT NULL COMMENT '查询详情的 SQL（可选的）',
    `list_sql` TEXT NULL DEFAULT NULL COMMENT '查询列表的 SQL（可选的）',
    `create_sql` TEXT NULL DEFAULT NULL COMMENT '创建的 SQL（可选的）',
    `update_sql` TEXT NULL DEFAULT NULL COMMENT '修改的 SQL（可选的）',
    `delete_sql` TEXT NULL DEFAULT NULL COMMENT '删除的 SQL（可选的）',
    `del_field` VARCHAR(100) NULL DEFAULT 'stat' COMMENT '删除字段名称',
    `has_is_deleted` TINYINT(1) NULL DEFAULT '1' COMMENT '是否有逻辑删除标记',
    `tenant_isolation` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '是否加入租户数据隔离',
    `id_type` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '1=自增；2=雪花；3=UUID',
    `stat` TINYINT(3) UNSIGNED NULL DEFAULT '0' COMMENT '数据字典：状态',
    `creator` VARCHAR(50) NULL DEFAULT NULL COMMENT '创建人名称（可冗余的）' ,
    `creator_id` INT(10) NULL DEFAULT NULL COMMENT '创建人 id',
    `create_date` DATETIME NOT NULL DEFAULT (now()) COMMENT '创建日期',
    `updater` VARCHAR(50) NULL DEFAULT NULL COMMENT '修改人名称（可冗余的）',
    `updater_id` INT(10) NULL DEFAULT NULL COMMENT '修改人 id',
    `update_date` DATETIME NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `id_UNIQUE` (`id`) USING BTREE
)
COMMENT='通用万能型 API 接口的配置'
COLLATE='utf8mb4_unicode_ci'
```

```sql
CREATE TABLE `ds_common_api` (
    `id` INT(10) NOT NULL AUTO_INCREMENT COMMENT '主键 id，自增',
    `pid` INT(10) NULL DEFAULT '-1' COMMENT '父节点 id，如果是顶级节点为 -1 或者空',
    `name` VARCHAR(90) NULL DEFAULT NULL COMMENT '说明',
    `namespace` VARCHAR(50) NOT NULL COMMENT '命名空间，标识',
    `table_name` VARCHAR(45) NULL DEFAULT NULL COMMENT '表名',
    `type` VARCHAR(10) NULL DEFAULT NULL COMMENT '类型 SINGLE | CRUD',
    `clz_name` VARCHAR(10) NULL DEFAULT NULL COMMENT '实体类引用名称',
    `id_field` VARCHAR(10) NULL DEFAULT 'id' COMMENT '主键字段名称',
    `sql` TEXT NULL DEFAULT NULL COMMENT '单条 SQL 命令',
    `info_sql` TEXT NULL DEFAULT NULL COMMENT '查询详情的 SQL（可选的）',
    `list_sql` TEXT NULL DEFAULT NULL COMMENT '查询列表的 SQL（可选的）',
    `create_sql` TEXT NULL DEFAULT NULL COMMENT '创建的 SQL（可选的）',
    `update_sql` TEXT NULL DEFAULT NULL COMMENT '修改的 SQL（可选的）',
    `delete_sql` TEXT NULL DEFAULT NULL COMMENT '删除的 SQL（可选的）',
    `del_field` VARCHAR(100) NULL DEFAULT 'stat' COMMENT '删除字段名称',
    `has_is_deleted` TINYINT(1) NULL DEFAULT '1' COMMENT '是否有逻辑删除标记',
    `tenant_isolation` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '是否加入租户数据隔离',
    `id_type` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '1=自增；2=雪花；3=UUID',
    `stat` TINYINT(3) UNSIGNED NULL DEFAULT '0' COMMENT '数据字典：状态',
    `creator` VARCHAR(50) NULL DEFAULT NULL COMMENT '创建人名称（可冗余的）' ,
    `creator_id` INT(10) NULL DEFAULT NULL COMMENT '创建人 id',
    `create_date` DATETIME NOT NULL DEFAULT (now()) COMMENT '创建日期',
    `updater` VARCHAR(50) NULL DEFAULT NULL COMMENT '修改人名称（可冗余的）',
    `updater_id` INT(10) NULL DEFAULT NULL COMMENT '修改人 id',
    `update_date` DATETIME NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `id_UNIQUE` (`id`) USING BTREE
)
COMMENT='通用万能型 API 接口的配置'
COLLATE='utf8mb4_unicode_ci'
```

插入两笔新的数据，读取配置。


```sql
CREATE TABLE `ds_common_api` (
    `id` INT(10) NOT NULL AUTO_INCREMENT COMMENT '主键 id，自增',
    `pid` INT(10) NULL DEFAULT '-1' COMMENT '父节点 id，如果是顶级节点为 -1 或者空',
    `name` VARCHAR(90) NULL DEFAULT NULL COMMENT '说明',
    `namespace` VARCHAR(50) NOT NULL COMMENT '命名空间，标识',
    `table_name` VARCHAR(45) NULL DEFAULT NULL COMMENT '表名',
    `type` VARCHAR(10) NULL DEFAULT NULL COMMENT '类型 SINGLE | CRUD',
    `clz_name` VARCHAR(10) NULL DEFAULT NULL COMMENT '实体类引用名称',
    `id_field` VARCHAR(10) NULL DEFAULT 'id' COMMENT '主键字段名称',
    `sql` TEXT NULL DEFAULT NULL COMMENT '单条 SQL 命令',
    `info_sql` TEXT NULL DEFAULT NULL COMMENT '查询详情的 SQL（可选的）',
    `list_sql` TEXT NULL DEFAULT NULL COMMENT '查询列表的 SQL（可选的）',
    `create_sql` TEXT NULL DEFAULT NULL COMMENT '创建的 SQL（可选的）',
    `update_sql` TEXT NULL DEFAULT NULL COMMENT '修改的 SQL（可选的）',
    `delete_sql` TEXT NULL DEFAULT NULL COMMENT '删除的 SQL（可选的）',
    `del_field` VARCHAR(100) NULL DEFAULT 'stat' COMMENT '删除字段名称',
    `has_is_deleted` TINYINT(1) NULL DEFAULT '1' COMMENT '是否有逻辑删除标记',
    `tenant_isolation` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '是否加入租户数据隔离',
    `id_type` TINYINT(3) UNSIGNED NULL DEFAULT NULL COMMENT '1=自增；2=雪花；3=UUID',
    `stat` TINYINT(3) UNSIGNED NULL DEFAULT '0' COMMENT '数据字典：状态',
    `creator` VARCHAR(50) NULL DEFAULT NULL COMMENT '创建人名称（可冗余的）' ,
    `creator_id` INT(10) NULL DEFAULT NULL COMMENT '创建人 id',
    `create_date` DATETIME NOT NULL DEFAULT (now()) COMMENT '创建日期',
    `updater` VARCHAR(50) NULL DEFAULT NULL COMMENT '修改人名称（可冗余的）',
    `updater_id` INT(10) NULL DEFAULT NULL COMMENT '修改人 id',
    `update_date` DATETIME NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `id_UNIQUE` (`id`) USING BTREE
)
COMMENT='通用万能型 API 接口的配置'
COLLATE='utf8mb4_unicode_ci'
```