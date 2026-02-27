import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "zh-CN",
  title: "Datapillar 文档",
  description: "Datapillar 产品使用手册",
  base: "/Datapillar/",
  lastUpdated: true,
  themeConfig: {
    nav: [
      { text: "首页", link: "/" },
      { text: "快速开始", link: "/guide/getting-started" }
    ],
    sidebar: [
      {
        text: "开始使用",
        items: [{ text: "快速开始", link: "/guide/getting-started" }]
      },
      {
        text: "产品文档",
        items: [
          { text: "后端开发规范", link: "/backend-development-specification" },
          { text: "AI 重构规范", link: "/ai-refactor-specification" }
        ]
      }
    ],
    search: {
      provider: "local"
    },
    socialLinks: [
      { icon: "github", link: "https://github.com/SunnyX6/Datapillar" }
    ]
  }
});
