module.exports = {
  root: true,
  env: { node: true, browser: true },
  extends: [
    'plugin:vue/essential',
    'eslint:recommended'
  ],
  parserOptions: { parser: 'babel-eslint', ecmaVersion: 2020 },
  rules: {
    'no-console': 'off',
    'no-unused-vars': 'warn',
    'vue/no-unused-components': 'warn',
    // 仅对全局注册的组件有意义（避免与 HTML 元素冲突）；
    // 本项目 dashboard view 由 router 按文件名引入，不会作为标签直接使用。
    'vue/multi-word-component-names': 'off'
  }
}
