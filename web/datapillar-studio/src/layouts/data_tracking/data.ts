import type { EventSchema, TrackingPoint } from './types'

export const EVENT_SCHEMAS: EventSchema[] = [
  {
    id: 'sch_trade_pay',
    key: 'order_paid',
    name: '订单支付成功',
    kind: 'ATOMIC',
    description: '核心交易事件，当支付网关返回成功时触发。包含订单金额、币种及支付方式。',
    domain: 'Trade',
    usageCount: 128,
    standardProperties: [
      { id: 'prop_order_id', name: 'order_id' },
      { id: 'prop_amount', name: 'amount' },
      { id: 'prop_currency', name: 'currency' }
    ]
  },
  {
    id: 'sch_product_view',
    key: 'product_view',
    name: '商品详情浏览',
    kind: 'ATOMIC',
    description: '用户进入商品详情页。用于计算转化率漏斗与推荐效果。',
    domain: 'Product',
    usageCount: 86,
    standardProperties: [
      { id: 'prop_sku_id', name: 'sku_id' },
      { id: 'prop_category', name: 'category' }
    ]
  },
  {
    id: 'sch_banner_click',
    key: 'banner_click',
    name: 'Banner 点击',
    kind: 'ATOMIC',
    description: '通用 Banner 组件点击事件，支持首页、活动页等多场景复用。',
    domain: 'Marketing',
    usageCount: 240,
    standardProperties: [
      { id: 'prop_banner_id', name: 'banner_id' },
      { id: 'prop_target_url', name: 'target_url' }
    ]
  },
  {
    id: 'sch_add_cart',
    key: 'add_to_cart',
    name: '加入购物车',
    kind: 'ATOMIC',
    description: '用户点击加入购物车按钮，记录 SKU 与数量信息。',
    domain: 'Trade',
    usageCount: 52,
    standardProperties: [
      { id: 'prop_sku_id', name: 'sku_id' },
      { id: 'prop_qty', name: 'quantity' }
    ]
  },
  {
    id: 'sch_app_launch',
    key: 'app_launch',
    name: 'App 启动',
    kind: 'ATOMIC',
    description: '应用冷启动或从后台唤醒，用于分析启动耗时与崩溃率。',
    domain: 'Tech',
    usageCount: 912,
    standardProperties: []
  },
  {
    id: 'sch_growth_combo',
    key: 'growth_combo',
    name: '营销组合事件',
    kind: 'COMPOSITE',
    description: '组合事件：广告曝光 + 点击 + 下单，支持多触点归因分析。',
    domain: 'Growth',
    usageCount: 32,
    standardProperties: []
  }
]

export const TRACKING_POINTS: TrackingPoint[] = [
  {
    id: 'tp_web_pay',
    schemaId: 'sch_trade_pay',
    schemaName: '订单支付成功',
    viewPath: '/checkout/success',
    platform: 'Web',
    triggerDescription: '支付网关回跳页面加载完成后触发。',
    status: 'implemented',
    contextProperties: [{ id: 'ctx_load', name: 'page_load_time' }]
  },
  {
    id: 'tp_app_pay',
    schemaId: 'sch_trade_pay',
    schemaName: '订单支付成功',
    viewPath: 'OrderSuccessViewController',
    platform: 'App',
    triggerDescription: 'App 端支付 SDK 回调成功。',
    status: 'tested',
    contextProperties: [
      { id: 'ctx_network', name: 'network_type' },
      { id: 'ctx_app_ver', name: 'app_version' }
    ]
  },
  {
    id: 'tp_web_view',
    schemaId: 'sch_product_view',
    schemaName: '商品详情浏览',
    viewPath: '/product/:sku',
    platform: 'Web',
    triggerDescription: '详情页首屏可见时触发。',
    status: 'implemented',
    contextProperties: [{ id: 'ctx_ref', name: 'referrer' }]
  },
  {
    id: 'tp_banner_click',
    schemaId: 'sch_banner_click',
    schemaName: 'Banner 点击',
    viewPath: '/home',
    platform: 'Web',
    triggerDescription: '用户点击 Banner 区域。',
    status: 'planned',
    contextProperties: [
      { id: 'ctx_position', name: 'position' },
      { id: 'ctx_ab', name: 'ab_group' }
    ]
  }
]
