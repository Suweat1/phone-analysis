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
    'vue/no-unused-components': 'warn'
  }
}
