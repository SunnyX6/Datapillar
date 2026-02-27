# 快速开始

## 1. 安装依赖

```bash
cd docs
npm install
```

## 2. 本地预览

```bash
npm run docs:dev
```

默认访问地址：`http://localhost:4173/`

## 3. 生产构建

```bash
npm run docs:build
npm run docs:preview
```

## 4. GitHub Pages 部署要点

- 仓库地址为 `SunnyX6/Datapillar`，`base` 必须是 `/Datapillar/`
- 推荐使用 GitHub Actions 产出并发布 `docs/.vitepress/dist`
