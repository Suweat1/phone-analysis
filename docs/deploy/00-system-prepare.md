# 00 - 系统初始化

> 对象：全新安装的 Ubuntu 20.04 server（glibc 2.31）。本文操作通常以 `root` 或现有 sudo 用户执行，最后切换到 `bigdata`。

## 1. 主机名与 hosts

```bash
sudo hostnamectl set-hostname phone-analysis
```

编辑 `/etc/hosts`，确保如下条目（IP 用本机实际 IP，方便后续 Spring Boot 客户端外部访问）：

```
127.0.0.1   localhost
127.0.1.1   phone-analysis
<本机IP>    phone-analysis
```

> Hadoop / Hive / Kafka 配置中均使用 `phone-analysis` 这个主机名，**不要** 用 `localhost` 写死。

## 2. 创建 bigdata 用户

```bash
sudo useradd -m -s /bin/bash bigdata
sudo passwd bigdata          # 设置密码（部署期使用，运行期一律走 ssh key）
```

### 2.1 sudo 免密

新建 `/etc/sudoers.d/bigdata`，写入：

```
bigdata ALL=(ALL) NOPASSWD:ALL
```

权限：

```bash
sudo chmod 440 /etc/sudoers.d/bigdata
```

### 2.2 ssh 免密 localhost（Hadoop 强制要求）

切换到 bigdata 用户：

```bash
su - bigdata
ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
ssh localhost           # 首次接受指纹后应能直接登入
ssh phone-analysis      # 同上
```

## 3. 必需的系统依赖（.so 与基础工具）

MySQL 8.0.31 二进制版依赖 `libaio` 与 `libncurses5`；Redis 编译需要 `build-essential`；其余组件依赖少量工具：

```bash
sudo apt update
sudo apt install -y \
  libaio1 libncurses5 libtinfo5 \
  build-essential tcl pkg-config \
  openssh-server rsync curl wget vim git unzip xz-utils \
  net-tools psmisc
```

> 若后续 MySQL 启动报 `error while loading shared libraries: libtinfo.so.5`，确认 `libtinfo5` 已安装即可。

## 4. 关闭对 Hadoop 不友好的特性

```bash
# 关闭 swap（YARN 容器 OOM 判断更准）
sudo swapoff -a
sudo sed -i.bak '/ swap / s/^/#/' /etc/fstab

# 关闭防火墙（伪分布式单机环境）
sudo ufw disable
```

## 5. 创建标准目录

```bash
sudo mkdir -p /opt/bigdata/{log,data,software,service}
sudo chown -R bigdata:bigdata /opt/bigdata
```

后续所有组件均：
- 安装包传到 `/opt/bigdata/software/`
- 解压到 `/opt/bigdata/service/`
- 日志输出到 `/opt/bigdata/log/<组件名>/`
- 业务数据（含 HDFS 本地存储）放 `/opt/bigdata/data/`

## 6. 环境变量统一入口

**所有组件的环境变量** 统一追加到 `/home/bigdata/.bashrc`，**禁止** 写到 `/etc/profile.d/*.sh`。

建议在 `.bashrc` 末尾留一个分段标记，方便人工维护：

```bash
# ===== phone-analysis env begin =====
# JDK / Hadoop / Hive / Spark / Kafka / Maven / Node 等逐个追加
# ===== phone-analysis env end =====
```

## 7. 代码同步

```bash
cd /home/bigdata
git clone <远程仓库地址> phone-analysis
```

后续每次升级：

```bash
cd ~/phone-analysis && git pull
```

部署脚本将以 `~/phone-analysis/config/<组件>/` 为配置源。

## 验证清单

- [ ] `hostname` 输出 `phone-analysis`
- [ ] `sudo -n true` 不提示输入密码
- [ ] `ssh localhost` 无密码登入并退出
- [ ] `ls /opt/bigdata/` 显示 4 个目录且属主为 bigdata
- [ ] `ldconfig -p | grep libaio` 有输出
