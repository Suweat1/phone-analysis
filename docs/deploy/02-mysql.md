# 02 - MySQL 8.0.31

> 二进制版本（`mysql-8.0.31-linux-glibc2.12-x86_64.tar.xz`），不使用 apt 安装；不使用 systemd，由 `mysqld_safe` 守护。

## 1. 必装系统库

已在 [00-system-prepare.md](./00-system-prepare.md) 中预装：

```
libaio1 libncurses5 libtinfo5
```

> 若启动时报 `libtinfo.so.5: cannot open shared object file` 或 `libaio.so.1`，先把这两个包补上。

## 2. 解压

```bash
cd /opt/bigdata/software/
tar -xJf mysql-8.0.31-linux-glibc2.12-x86_64.tar.xz -C /opt/bigdata/service/
mv /opt/bigdata/service/mysql-8.0.31-linux-glibc2.12-x86_64 /opt/bigdata/service/mysql
```

## 3. 目录与权限

```bash
mkdir -p /opt/bigdata/data/mysql        # 数据目录
mkdir -p /opt/bigdata/log/mysql          # 日志目录
mkdir -p /opt/bigdata/service/mysql/tmp  # 临时目录
chown -R bigdata:bigdata /opt/bigdata/data/mysql /opt/bigdata/log/mysql /opt/bigdata/service/mysql
```

## 4. 配置文件

**项目版本**：`config/mysql/my.cnf`（仓库内维护）

**部署位置**：

```bash
sudo cp ~/phone-analysis/config/mysql/my.cnf /etc/my.cnf
```

`my.cnf` 关键参数（只保留必要项）：

```ini
[mysqld]
basedir          = /opt/bigdata/service/mysql
datadir          = /opt/bigdata/data/mysql
socket           = /opt/bigdata/service/mysql/tmp/mysql.sock
pid-file         = /opt/bigdata/service/mysql/tmp/mysql.pid
log-error        = /opt/bigdata/log/mysql/error.log
port             = 3306
character-set-server = utf8mb4
collation-server     = utf8mb4_unicode_ci
default-time-zone    = '+08:00'
default_authentication_plugin = mysql_native_password    # 兼容 Hive JDBC

[client]
socket = /opt/bigdata/service/mysql/tmp/mysql.sock
port   = 3306
default-character-set = utf8mb4
```

> `default_authentication_plugin=mysql_native_password` **必须开启**，否则 Hive 3.1.3 + mysql-connector-java 5.1.x 连不上 MySQL 8。

## 5. 环境变量（`~/.bashrc`）

```bash
# MySQL
export MYSQL_HOME=/opt/bigdata/service/mysql
export PATH=$MYSQL_HOME/bin:$PATH
```

## 6. 初始化

```bash
mysqld --defaults-file=/etc/my.cnf --initialize --user=bigdata
```

初始化完成后在 `/opt/bigdata/log/mysql/error.log` 搜索 `temporary password` 拿到临时密码。

## 7. 启动

```bash
mysqld_safe --defaults-file=/etc/my.cnf --user=bigdata &
```

## 8. 修改 root 密码 & 远程访问

```bash
mysql -uroot -p'<临时密码>'
```

```sql
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';
CREATE USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
```

## 9. 创建 Hive 元数据库（为 05-hive.md 准备）

```sql
CREATE DATABASE hive_metastore DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
CREATE USER 'hive'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
GRANT ALL PRIVILEGES ON hive_metastore.* TO 'hive'@'%';
FLUSH PRIVILEGES;
```

## 10. 创建业务库（为 Spring Boot 应用准备）

```sql
CREATE DATABASE phone_analysis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 11. 停止

```bash
mysqladmin -uroot -p123456 shutdown
```

## 验证

- `ss -ltnp | grep 3306` 有监听
- `mysql -uroot -p123456 -h phone-analysis -e 'select version();'` 输出 8.0.31
- `mysql -uhive -p123456 -h phone-analysis hive_metastore -e 'show tables;'` 不报权限错误
