const lodashModule = require('lodash/lodash.js')
const lodash = lodashModule && lodashModule.default ? lodashModule.default : lodashModule

if (typeof lodash.constant !== 'function') {
  lodash.constant = (value) => () => value
}

module.exports = lodash
module.exports.default = lodash
