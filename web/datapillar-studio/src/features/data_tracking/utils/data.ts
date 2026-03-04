import type { EventSchema,TrackingPoint } from './types'

export const EVENT_SCHEMAS:EventSchema[] = [{
 id:'sch_trade_pay',key:'order_paid',name:'Order payment successful',kind:'ATOMIC',description:'core transaction events,Triggered when the payment gateway returns success.Contains order amount,Currency and payment method.',domain:'Trade',usageCount:128,standardProperties:[{ id:'prop_order_id',name:'order_id' },{ id:'prop_amount',name:'amount' },{ id:'prop_currency',name:'currency' }]
 },{
 id:'sch_product_view',key:'product_view',name:'Browse product details',kind:'ATOMIC',description:'User enters product details page.Used to calculate conversion rate funnel and recommendation effect.',domain:'Product',usageCount:86,standardProperties:[{ id:'prop_sku_id',name:'sku_id' },{ id:'prop_category',name:'category' }]
 },{
 id:'sch_banner_click',key:'banner_click',name:'Banner Click',kind:'ATOMIC',description:'Universal Banner Component click event,Support home page,Reuse in multiple scenarios such as event pages.',domain:'Marketing',usageCount:240,standardProperties:[{ id:'prop_banner_id',name:'banner_id' },{ id:'prop_target_url',name:'target_url' }]
 },{
 id:'sch_add_cart',key:'add_to_cart',name:'add to cart',kind:'ATOMIC',description:'User clicks add to cart button,record SKU and quantity information.',domain:'Trade',usageCount:52,standardProperties:[{ id:'prop_sku_id',name:'sku_id' },{ id:'prop_qty',name:'quantity' }]
 },{
 id:'sch_app_launch',key:'app_launch',name:'App start',kind:'ATOMIC',description:'App cold start or wake from background,Used to analyze startup time and crash rate.',domain:'Tech',usageCount:912,standardProperties:[]
 },{
 id:'sch_growth_combo',key:'growth_combo',name:'marketing mix events',kind:'COMPOSITE',description:'Combined events:Advertising exposure + Click + Place an order,Supports multi-touch attribution analysis.',domain:'Growth',usageCount:32,standardProperties:[]
 }]

export const TRACKING_POINTS:TrackingPoint[] = [{
 id:'tp_web_pay',schemaId:'sch_trade_pay',schemaName:'Order payment successful',viewPath:'/checkout/success',platform:'Web',triggerDescription:'Triggered after the payment gateway bounce page is loaded..',status:'implemented',contextProperties:[{ id:'ctx_load',name:'page_load_time' }]
 },{
 id:'tp_app_pay',schemaId:'sch_trade_pay',schemaName:'Order payment successful',viewPath:'OrderSuccessViewController',platform:'App',triggerDescription:'App terminal payment SDK Callback successful.',status:'tested',contextProperties:[{ id:'ctx_network',name:'network_type' },{ id:'ctx_app_ver',name:'app_version' }]
 },{
 id:'tp_web_view',schemaId:'sch_product_view',schemaName:'Browse product details',viewPath:'/product/:sku',platform:'Web',triggerDescription:'Triggered when the home page of the details page is visible.',status:'implemented',contextProperties:[{ id:'ctx_ref',name:'referrer' }]
 },{
 id:'tp_banner_click',schemaId:'sch_banner_click',schemaName:'Banner Click',viewPath:'/home',platform:'Web',triggerDescription:'User clicks Banner area.',status:'planned',contextProperties:[{ id:'ctx_position',name:'position' },{ id:'ctx_ab',name:'ab_group' }]
 }]
