# 08 - Node 14+（Vue 前端构建）

> 仅在运行机上用作 Vue 项目的构建器；前端最终产物是静态文件，由 Spring Boot 内嵌或 nginx 提供。本机开发期前端调试不依赖运行机。

## 1. 解压

```bash
cd /opt/bigdata/software/
tar -xJf node-v14.21.3-linux-x64.tar.xz -C /opt/bigdata/service/
mv /opt/bigdata/service/node-v14.21.3-linux-x64 /opt/bigdata/service/node
```

## 2. 环境变量（`~/.bashrc`）

```bash
# Node
export NODE_HOME=/opt/bigdata/service/node
export PATH=$NODE_HOME/bin:$PATH
```

## 3. npm 镜像（可选，国内更快）

```bash
npm config set registry https://registry.npmmirror.com
```

## 4. 验证

```bash
node -v       # v14.21.x
npm -v
```

## 5. 构建前端项目

```bash
cd ~/phone-analysis/web
npm install
npm run build
# 产物：dist/  → 由 Spring Boot 静态资源或 nginx 暴露
```
