import Teek from "vitepress-theme-teek";
import TeekLayoutProvider from "./components/TeekLayoutProvider.vue";
import EcosystemLanding from "./components/EcosystemLanding.vue";
import MonicaCloudComingSoon from "./components/MonicaCloudComingSoon.vue";
import { h } from "vue";

import "vitepress-theme-teek/index.css";
import "vitepress-theme-teek/theme-chalk/tk-code-block-mobile.css"; // 移动端代码块样式优化
import "vitepress-theme-teek/theme-chalk/tk-sidebar.css"; // 侧边栏优化
import "vitepress-theme-teek/theme-chalk/tk-nav.css"; // 导航栏优化
import "vitepress-theme-teek/theme-chalk/tk-aside.css"; // 右侧目栏录文字悬停和激活样式
import "vitepress-theme-teek/theme-chalk/tk-doc-h1-gradient.css"; // 一级标题渐变色
import "vitepress-theme-teek/theme-chalk/tk-table.css"; // 表格样式调整，去掉单元格之间的线条
import "vitepress-theme-teek/theme-chalk/tk-mark.css"; // <mark></mark> 样式
import "vitepress-theme-teek/theme-chalk/tk-blockquote.css"; // > 引用块样式
import "vitepress-theme-teek/theme-chalk/tk-index-rainbow.css"; // 首页图片彩虹动画
//import "vitepress-theme-teek/theme-chalk/tk-banner-desc-gradient.css"; // 博客风格 Banner 描述渐变样式
import "vitepress-theme-teek/theme-chalk/tk-home-card-hover.css"; // 首页卡片悬停效果
import "vitepress-theme-teek/theme-chalk/tk-fade-up-animation.css"; // 首次加载的动画效果

import "./styles/code-bg.scss";
import "./styles/iframe.scss";

const setupRootLocaleNavigation = () => {
  const selector = ".VPNavBarTranslations a, .VPNavBarExtra a";

  const isRootLocaleLink = (link: HTMLAnchorElement) =>
    link.textContent?.trim() === "简体中文" && link.origin === window.location.origin;

  const forceDocumentNavigation = (link: HTMLAnchorElement) => {
    if (!isRootLocaleLink(link)) return;

    link.target = "_self";

    if (link.pathname !== window.location.pathname || link.search !== window.location.search || link.hash !== window.location.hash) {
      window.location.assign(link.href);
    }
  };

  const normalizeRootLocaleLinks = () => {
    document.querySelectorAll<HTMLAnchorElement>(selector).forEach((link) => {
      if (isRootLocaleLink(link)) link.target = "_self";
    });
  };

  normalizeRootLocaleLinks();

  const observer = new MutationObserver(normalizeRootLocaleLinks);
  observer.observe(document.body, { childList: true, subtree: true });

  window.addEventListener(
    "click",
    (event) => {
      if (event.button !== 0 || event.ctrlKey || event.shiftKey || event.altKey || event.metaKey) return;

      const target = event.target instanceof Element ? event.target : null;
      const link = target?.closest(selector);
      if (!(link instanceof HTMLAnchorElement) || !isRootLocaleLink(link)) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      setTimeout(() => forceDocumentNavigation(link), 0);
    },
    true
  );
};

export default {
  extends: Teek,
  Layout: () => h(TeekLayoutProvider),
  enhanceApp(ctx) {
    Teek.enhanceApp?.(ctx);
    ctx.app.component("EcosystemLanding", EcosystemLanding);
    ctx.app.component("MonicaCloudComingSoon", MonicaCloudComingSoon);

    if (typeof window === "undefined") return;

    setupRootLocaleNavigation();
  },
};


