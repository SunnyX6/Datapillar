/**
 * scene change Hook
 *
 * Function:* 1.Cycle through different business scenarios
 * 2.Each scene contains:Enter text,Workflow configuration,Log data
 */

import { useState,useMemo } from 'react'
import { useLanguage } from '@/state'

/**
 * Workflow node configuration
 */
export interface WorkflowNode {
 id:string
 name:string
 description:string
 icon:string
 position:{ x:number;y:number }
 color:string
 step:number
}

/**
 * Workflow connection configuration
 */
export interface WorkflowEdge {
 id:string
 source:string
 target:string
 step:number
}

/**
 * Enter steps(Simulate real user input)
 */
export interface InputStep {
 text:string // currently displayed text
 duration:number // The duration of this step(ms)
}

/**
 * Scene configuration
 */
export interface Scenario {
 id:string
 input:string
 inputSteps:InputStep[] // Enter steps(Including typos,Delete)
 nodes:WorkflowNode[]
 edges:WorkflowEdge[]
 leftLogs:string[]
 rightLogs:string[]
}

/**
 * Chinese scene data
 */
const SCENARIOS_ZH:Scenario[] = [{
 id:'simple-sync',input:'Synchronize the order table and product table to the warehouse',inputSteps:[{ text:'Same',duration:50 },{ text:'sync',duration:50 },{ text:'sync library',duration:50 },{ text:'Sync inventory',duration:50 },{ text:'Synchronize inventory table',duration:50 },{ text:'Synchronize inventory tables and',duration:50 },{ text:'Synchronize inventory table and business',duration:50 },{ text:'Synchronize inventory tables and items',duration:50 },{ text:'Synchronize inventory table and product table',duration:50 },{ text:'Synchronize inventory table and product table to',duration:50 },{ text:'Synchronize inventory table and product table',duration:50 },{ text:'Synchronize inventory table and product table to data',duration:50 },{ text:'Synchronize inventory table and product table to database',duration:250 },// pause,I found that I made a typo
 { text:'Synchronize inventory table and product table to data',duration:80 },// rollback tail
 { text:'Synchronize inventory table and product table',duration:80 },{ text:'Synchronize the inventory table and product table to the warehouse',duration:80 },{ text:'Synchronize the library table and product table to the data warehouse',duration:80 },// Delete"save"
 { text:'Synchronize tables and product tables to the data warehouse',duration:80 },// Delete"Library"
 { text:'Synchronize the order form and product list to the warehouse',duration:60 },// Re-enter order
 { text:'Synchronize the order table and product table to the warehouse',duration:150 }],nodes:[{ id:'order-table',name:'order form',description:'MySQL',icon:'Database',position:{ x:50,y:100 },color:'blue',step:0 },{ id:'product-table',name:'Product list',description:'MySQL',icon:'Package',position:{ x:50,y:200 },color:'cyan',step:0 },{ id:'sync',name:'Data synchronization',description:'DataX',icon:'ArrowRightLeft',position:{ x:250,y:150 },color:'purple',step:1 },{ id:'warehouse',name:'data warehouse',description:'Hive ODS',icon:'Warehouse',position:{ x:450,y:150 },color:'green',step:2 }],edges:[{ id:'e1',source:'order-table',target:'sync',step:1 },{ id:'e2',source:'product-table',target:'sync',step:1 },{ id:'e3',source:'sync',target:'warehouse',step:2 }],leftLogs:["Analyze requirements:Synchronize two tables to the data warehouse...","Identify source table:orders (128Wan Xing),products (5.2Wan Xing)","Choose a sync tool:DataX Batch sync","Design a synchronization strategy:Full synchronization","generate DataX Configuration file...","Configure target table:ODSlayer,Partition by date","Workflow orchestration completed."],rightLogs:["start DataX Task:sync-to-ods...","Synchronize order table:orders -> ods.ods_orders","Progress:128million / 128million (100%)","Synchronize product table:products -> ods.ods_products","Progress:5.2million / 5.2million (100%)","Verify data integrity:Pass","Update metadata information...","Synchronization task executed successfully."]
 },{
 id:'complex-join',input:'Associate the order table and product table to output to the order wide table,Summary sales statistics',inputSteps:[{ text:'close',duration:50 },{ text:'association',duration:50 },{ text:'Related customers',duration:50 },{ text:'Associated customers',duration:50 },{ text:'Related customer table',duration:50 },{ text:'Relate the customer table and',duration:50 },{ text:'Associate customer table with business',duration:50 },{ text:'Associate customer table and product',duration:50 },{ text:'Link customer table and product table',duration:50 },{ text:'Link customer table and product table',duration:50 },{ text:'Link customer table and product table output',duration:50 },{ text:'Associate the customer table and product table and output them to the order',duration:50 },{ text:'Associate the customer table and product table to output to the order',duration:50 },{ text:'Associate the customer table and product table to output to the order table',duration:220 },// pause
 { text:'Associate the customer table and product table to output to the order',duration:80 },{ text:'Associate the customer table and product table to output to the order',duration:80 },{ text:'Output the association table and product table to the order',duration:80 },{ text:'Associate the order table and product table to output to the order',duration:60 },{ text:'Associate the order table and product table to output to the order',duration:60 },{ text:'The associated order table and product table are output to the order width',duration:60 },{ text:'Associate the order table and product table to output to the order wide table,',duration:60 },{ text:'Associate the order table and product table to output to the order wide table,Summary sales statistics',duration:150 }],nodes:[{ id:'order-source',name:'order form',description:'ODS',icon:'Database',position:{ x:50,y:100 },color:'blue',step:0 },{ id:'product-source',name:'Product list',description:'ODS',icon:'Package',position:{ x:50,y:200 },color:'cyan',step:0 },{ id:'join',name:'JOIN association',description:'Spark SQL',icon:'Link',position:{ x:220,y:150 },color:'purple',step:1 },{ id:'wide-table',name:'Order wide table',description:'DWD',icon:'Table',position:{ x:370,y:150 },color:'amber',step:2 },{ id:'aggregation',name:'Summary statistics',description:'DWS',icon:'BarChart3',position:{ x:510,y:150 },color:'orange',step:3 }],edges:[{ id:'e1',source:'order-source',target:'join',step:1 },{ id:'e2',source:'product-source',target:'join',step:1 },{ id:'e3',source:'join',target:'wide-table',step:2 },{ id:'e4',source:'wide-table',target:'aggregation',step:3 }],leftLogs:["Analyze requirements:Build order wide table + sales summary...","design JOIN logic:orders LEFT JOIN products ON product_id","Wide table field design:Order information + Product details (35fields)","Summary indicator design:by date,Category statistics sales","Select calculation engine:Spark SQL","generate SQL script..."," > DWDlayer:Order wide table"," > DWSlayer:sales summary table","Workflow orchestration completed."],rightLogs:["start Spark Task:build-order-wide...","read ODS layer data:orders (128million),products (5.2million)","execute LEFT JOIN association...","Write to wide table:dwd.dwd_order_wide (128Wan Xing)","Start summary calculation..."," > by date dimension:365records"," > By category dimension:128records","Write summary table:dws.dws_sales_summary","Task execution successful,Time consuming 3points28seconds."]
 }]

/**
 * English scene data
 */
const SCENARIOS_EN:Scenario[] = [{
 id:'simple-sync',input:'Sync order and product tables to warehouse',inputSteps:[{ text:'S',duration:50 },{ text:'Sy',duration:50 },{ text:'Syn',duration:50 },{ text:'Sync',duration:50 },{ text:'Sync ',duration:50 },{ text:'Sync i',duration:50 },{ text:'Sync in',duration:50 },{ text:'Sync inv',duration:50 },{ text:'Sync inve',duration:50 },{ text:'Sync inven',duration:50 },{ text:'Sync invent',duration:50 },{ text:'Sync invento',duration:50 },{ text:'Sync inventor',duration:50 },{ text:'Sync inventory',duration:50 },{ text:'Sync inventory ',duration:50 },{ text:'Sync inventory a',duration:50 },{ text:'Sync inventory an',duration:50 },{ text:'Sync inventory and',duration:50 },{ text:'Sync inventory and ',duration:50 },{ text:'Sync inventory and p',duration:50 },{ text:'Sync inventory and pr',duration:50 },{ text:'Sync inventory and pro',duration:50 },{ text:'Sync inventory and prod',duration:50 },{ text:'Sync inventory and produ',duration:50 },{ text:'Sync inventory and produc',duration:50 },{ text:'Sync inventory and product',duration:50 },{ text:'Sync inventory and product ',duration:50 },{ text:'Sync inventory and product t',duration:50 },{ text:'Sync inventory and product ta',duration:50 },{ text:'Sync inventory and product tab',duration:50 },{ text:'Sync inventory and product tabl',duration:50 },{ text:'Sync inventory and product table',duration:50 },{ text:'Sync inventory and product tables',duration:50 },{ text:'Sync inventory and product tables ',duration:50 },{ text:'Sync inventory and product tables t',duration:50 },{ text:'Sync inventory and product tables to',duration:50 },{ text:'Sync inventory and product tables to ',duration:50 },{ text:'Sync inventory and product tables to w',duration:50 },{ text:'Sync inventory and product tables to wa',duration:50 },{ text:'Sync inventory and product tables to war',duration:50 },{ text:'Sync inventory and product tables to ware',duration:50 },{ text:'Sync inventory and product tables to wareh',duration:50 },{ text:'Sync inventory and product tables to wareho',duration:50 },{ text:'Sync inventory and product tables to warehou',duration:50 },{ text:'Sync inventory and product tables to warehous',duration:50 },{ text:'Sync inventory and product tables to warehouse',duration:250 },{ text:'Sync nventory and product tables to warehouse',duration:80 },{ text:'Sync ventory and product tables to warehouse',duration:80 },{ text:'Sync entory and product tables to warehouse',duration:80 },{ text:'Sync ntory and product tables to warehouse',duration:80 },{ text:'Sync tory and product tables to warehouse',duration:80 },{ text:'Sync ory and product tables to warehouse',duration:80 },{ text:'Sync ry and product tables to warehouse',duration:80 },{ text:'Sync y and product tables to warehouse',duration:80 },{ text:'Sync and product tables to warehouse',duration:80 },{ text:'Sync o and product tables to warehouse',duration:60 },{ text:'Sync or and product tables to warehouse',duration:60 },{ text:'Sync ord and product tables to warehouse',duration:60 },{ text:'Sync orde and product tables to warehouse',duration:60 },{ text:'Sync order and product tables to warehouse',duration:150 }],nodes:[{ id:'order-table',name:'Orders',description:'MySQL',icon:'Database',position:{ x:50,y:100 },color:'blue',step:0 },{ id:'product-table',name:'Products',description:'MySQL',icon:'Package',position:{ x:50,y:200 },color:'cyan',step:0 },{ id:'sync',name:'Data Sync',description:'DataX',icon:'ArrowRightLeft',position:{ x:250,y:150 },color:'purple',step:1 },{ id:'warehouse',name:'Warehouse',description:'Hive ODS',icon:'Warehouse',position:{ x:450,y:150 },color:'green',step:2 }],edges:[{ id:'e1',source:'order-table',target:'sync',step:1 },{ id:'e2',source:'product-table',target:'sync',step:1 },{ id:'e3',source:'sync',target:'warehouse',step:2 }],leftLogs:["Analyzing requirement:sync two tables to warehouse...","Identifying sources:orders (1.28M rows),products (52K rows)","Selecting sync tool:DataX batch sync","Designing sync strategy:full load","Generating DataX config file...","Configuring target:ODS layer,partitioned by date","Workflow orchestration completed."],rightLogs:["Starting DataX task:sync-to-ods...","Syncing orders:orders -> ods.ods_orders","Progress:1.28M / 1.28M (100%)","Syncing products:products -> ods.ods_products","Progress:52K / 52K (100%)","Validating data integrity:passed","Updating metadata...","Sync task completed successfully."]
 },{
 id:'complex-join',input:'Join orders and products into wide table,aggregate sales',inputSteps:[{ text:'J',duration:50 },{ text:'Jo',duration:50 },{ text:'Joi',duration:50 },{ text:'Join',duration:50 },{ text:'Join ',duration:50 },{ text:'Join o',duration:50 },{ text:'Join or',duration:50 },{ text:'Join ord',duration:50 },{ text:'Join orde',duration:50 },{ text:'Join order',duration:50 },{ text:'Join orders',duration:50 },{ text:'Join orders ',duration:50 },{ text:'Join orders a',duration:50 },{ text:'Join orders an',duration:50 },{ text:'Join orders and',duration:50 },{ text:'Join orders and ',duration:50 },{ text:'Join orders and p',duration:50 },{ text:'Join orders and pr',duration:50 },{ text:'Join orders and pro',duration:50 },{ text:'Join orders and prod',duration:50 },{ text:'Join orders and produ',duration:50 },{ text:'Join orders and produc',duration:50 },{ text:'Join orders and product',duration:50 },{ text:'Join orders and products',duration:50 },{ text:'Join orders and products ',duration:50 },{ text:'Join orders and products i',duration:50 },{ text:'Join orders and products in',duration:50 },{ text:'Join orders and products int',duration:50 },{ text:'Join orders and products into',duration:50 },{ text:'Join orders and products into ',duration:50 },{ text:'Join orders and products into w',duration:50 },{ text:'Join orders and products into wi',duration:50 },{ text:'Join orders and products into wid',duration:50 },{ text:'Join orders and products into wide',duration:50 },{ text:'Join orders and products into wide ',duration:50 },{ text:'Join orders and products into wide t',duration:50 },{ text:'Join orders and products into wide ta',duration:50 },{ text:'Join orders and products into wide tab',duration:50 },{ text:'Join orders and products into wide tabl',duration:50 },{ text:'Join orders and products into wide table',duration:50 },{ text:'Join orders and products into wide table,',duration:50 },{ text:'Join orders and products into wide table,',duration:50 },{ text:'Join orders and products into wide table,a',duration:50 },{ text:'Join orders and products into wide table,ag',duration:50 },{ text:'Join orders and products into wide table,agg',duration:50 },{ text:'Join orders and products into wide table,aggr',duration:50 },{ text:'Join orders and products into wide table,aggre',duration:50 },{ text:'Join orders and products into wide table,aggreg',duration:50 },{ text:'Join orders and products into wide table,aggrega',duration:50 },{ text:'Join orders and products into wide table,aggregat',duration:50 },{ text:'Join orders and products into wide table,aggregate',duration:50 },{ text:'Join orders and products into wide table,aggregate ',duration:50 },{ text:'Join orders and products into wide table,aggregate s',duration:50 },{ text:'Join orders and products into wide table,aggregate sa',duration:50 },{ text:'Join orders and products into wide table,aggregate sal',duration:50 },{ text:'Join orders and products into wide table,aggregate sale',duration:50 },{ text:'Join orders and products into wide table,aggregate sales',duration:150 }],nodes:[{ id:'order-source',name:'Orders',description:'ODS',icon:'Database',position:{ x:50,y:100 },color:'blue',step:0 },{ id:'product-source',name:'Products',description:'ODS',icon:'Package',position:{ x:50,y:200 },color:'cyan',step:0 },{ id:'join',name:'JOIN',description:'Spark SQL',icon:'Link',position:{ x:220,y:150 },color:'purple',step:1 },{ id:'wide-table',name:'Order Wide',description:'DWD',icon:'Table',position:{ x:370,y:150 },color:'amber',step:2 },{ id:'aggregation',name:'Aggregation',description:'DWS',icon:'BarChart3',position:{ x:510,y:150 },color:'orange',step:3 }],edges:[{ id:'e1',source:'order-source',target:'join',step:1 },{ id:'e2',source:'product-source',target:'join',step:1 },{ id:'e3',source:'join',target:'wide-table',step:2 },{ id:'e4',source:'wide-table',target:'aggregation',step:3 }],leftLogs:["Analyzing requirement:build order wide table + sales aggregation...","Designing JOIN logic:orders LEFT JOIN products ON product_id","Wide table schema:order info + product details (35 fields)","Aggregation metrics:sales by date,category","Selecting compute engine:Spark SQL","Generating SQL scripts..."," > DWD layer:order wide table"," > DWS layer:sales summary","Workflow orchestration completed."],rightLogs:["Starting Spark task:build-order-wide...","Reading ODS data:orders (1.28M),products (52K)","Executing LEFT JOIN...","Writing wide table:dwd.dwd_order_wide (1.28M rows)","Starting aggregation..."," > By date dimension:365 records"," > By category dimension:128 records","Writing summary:dws.dws_sales_summary","Task completed successfully,elapsed 3m28s."]
 }]

/**
 * scene change Hook
 * @returns Current scene configuration and switching functions
 */
export function useScenario():{ scenario:Scenario;nextScenario:() => void } {
 const language = useLanguage()
 const [currentIndex,setCurrentIndex] = useState(0)

 // Select scene data based on language,use useMemo Avoid duplicate creation
 const SCENARIOS = useMemo(() => {
 return language === 'zh-CN'?SCENARIOS_ZH:SCENARIOS_EN
 },[language])

 // Reset scene index when language switch
 const [prevLanguage,setPrevLanguage] = useState(language)
 if (prevLanguage!== language) {
 setPrevLanguage(language)
 setCurrentIndex(0)
 }

 const nextScenario = () => {
 setCurrentIndex((prev) => (prev + 1) % SCENARIOS.length)
 }

 // use useMemo Cache the current scene object,Avoid unnecessary re-rendering
 const scenario = useMemo(() => SCENARIOS[currentIndex],[SCENARIOS,currentIndex])

 return {
 scenario,nextScenario
 }
}
