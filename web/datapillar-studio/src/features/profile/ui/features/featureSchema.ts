export type FeatureNode = {
 id:string
 name:string
 description:string
 actions?: string[]
}

export type FeatureModule = FeatureNode & {
 children:FeatureNode[]
}

export const FEATURE_SCHEMA:FeatureModule[] = [{
 id:'module_governance',name:'data governance',description:'Unified governance strategy and data asset portal.',children:[{
 id:'feature_catalog',name:'data directory',description:'Unified data asset catalog and labeling system.',actions:['CATALOG:READ','CATALOG:MANAGE','CATALOG:TAG']
 },{
 id:'feature_lineage',name:'bloodline analysis',description:'Check upstream and downstream blood relationships.',actions:['LINEAGE:READ','LINEAGE:EXPORT']
 },{
 id:'feature_quality',name:'quality rules',description:'Configuration and audit quality rules.',actions:['QUALITY:READ','QUALITY:WRITE','QUALITY:ALERT']
 }]
 },{
 id:'module_build',name:'Development and release',description:'R&D workflow and release process.',children:[{
 id:'feature_workflow',name:'Workflow orchestration',description:'Visually orchestrate data tasks.',actions:['FLOW:READ','FLOW:DEPLOY','FLOW:EXECUTE']
 },{
 id:'feature_ide',name:'SQL IDE',description:'Online development and debugging.',actions:['IDE:READ','IDE:RUN','IDE:SHARE']
 }]
 },{
 id:'module_ai',name:'AI Ability',description:'Model and Intelligence Competence Center.',children:[{
 id:'feature_assistant',name:'AI Assisted repair',description:'Intelligent repair and generation.',actions:['AI:READ','AI:ASSIST','AI:CONFIG']
 },{
 id:'feature_models',name:'Model management',description:'Model configuration and evaluation.',actions:['MODEL:READ','MODEL:DEPLOY','MODEL:CHECK']
 }]
 }]
