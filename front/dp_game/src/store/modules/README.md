# 已废弃（Wave 8）

Vuex 模块已迁入 `src/features/*/store/`；根 `store/index.js` 仍从此处聚合注册。

**不要**在此目录新建文件；旧 `@/store/modules/...` 路径不再提供兼容 re-export。
