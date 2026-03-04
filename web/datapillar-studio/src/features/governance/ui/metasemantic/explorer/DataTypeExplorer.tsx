import { useState,useMemo } from 'react'
import { ArrowLeft,Hash,Database,Type,Calendar,Check,Braces,Binary,Clock,ToggleLeft,FileCode,Layers,List,Map,Fingerprint } from 'lucide-react'
import { iconSizeToken,panelWidthClassMap } from '@/design-tokens/dimensions'
import { DEFAULT_LENGTH,DEFAULT_MAX_LENGTH,getMaxLengthForType } from '@/utils/dataType'
import type { DataTypeItem } from '@/features/governance/ui/metasemantic/types'

/** Data type grouping - Based on Gravitino Types.java */
const DATA_TYPE_GROUPS = [{ id:'INTEGRAL',label:'INTEGRAL' },{ id:'FRACTION',label:'FRACTION' },{ id:'STRING',label:'STRING' },{ id:'DATETIME',label:'DATETIME' },{ id:'COMPLEX',label:'COMPLEX' }] as const

/** Preset data types - Based on Gravitino Types.java */
const DATA_TYPES:DataTypeItem[] = [// integer type (IntegralType)
 {
 id:'byte',name:'BYTE',label:'Byte',category:'INTEGRAL',icon:'hash',description:'8bit signed integer,Value range -128 Arrive 127,support unsigned mode.'
 },{
 id:'short',name:'SHORT',label:'Short',category:'INTEGRAL',icon:'hash',description:'16bit signed integer,Value range -32,768 Arrive 32,767,support unsigned mode.'
 },{
 id:'integer',name:'INTEGER',label:'Integer',category:'INTEGRAL',icon:'hash',description:'32bit signed integer,Value range -2,147,483,648 Arrive 2,147,483,647,support unsigned mode.'
 },{
 id:'long',name:'LONG',label:'Long',category:'INTEGRAL',icon:'database',description:'64bit signed integer,The value range is very large,Primary key suitable for large amounts of data,Scenarios such as timestamps.'
 },// Decimal type (FractionType)
 {
 id:'float',name:'FLOAT',label:'Float',category:'FRACTION',icon:'braces',description:'Single precision floating point number,Suitable for numerical calculation scenarios that do not require high accuracy.'
 },{
 id:'double',name:'DOUBLE',label:'Double',category:'FRACTION',icon:'braces',description:'Double precision floating point number,Suitable for scientific calculations and numerical scenarios requiring higher precision.'
 },{
 id:'decimal',name:'DECIMAL',label:'Decimal',category:'FRACTION',icon:'braces',description:'Fixed-point high-precision numerical value,by definition Precision (1-38) and Scale To meet demanding business computing scenarios.',badge:'PRECISION ARCHITECT',hasPrecision:true,hasScale:true,maxPrecision:38,maxScale:18
 },// string type (PrimitiveType)
 {
 id:'string',name:'STRING',label:'String',category:'STRING',icon:'type',description:'Unlimited string type,Equivalent to varchar(MAX),The specific length is determined by the bottom layer catalog decide.'
 },{
 id:'varchar',name:'VARCHAR',label:'VarChar',category:'STRING',icon:'type',description:'variable length string,The maximum length needs to be specified,Suitable for text data with a known upper limit of length.',hasLength:true,maxLength:getMaxLengthForType('VARCHAR')
 },{
 id:'fixedchar',name:'FIXEDCHAR',label:'Char',category:'STRING',icon:'type',description:'fixed length string,Need to specify length,Fill in the missing parts with spaces.',hasLength:true,maxLength:getMaxLengthForType('FIXEDCHAR')
 },{
 id:'binary',name:'BINARY',label:'Binary',category:'STRING',icon:'binary',description:'binary data type,Suitable for storing pictures,Binary content such as files.'
 },{
 id:'uuid',name:'UUID',label:'UUID',category:'STRING',icon:'fingerprint',description:'universally unique identifier,128Bit,Suitable for distributed systems onlyIDgenerate.'
 },{
 id:'boolean',name:'BOOLEAN',label:'Boolean',category:'STRING',icon:'toggle',description:'Boolean type,only true and false two values,Suitable for switches,Signs and other scenes.'
 },// datetime type (DateTimeType)
 {
 id:'date',name:'DATE',label:'Date',category:'DATETIME',icon:'calendar',description:'date type,The format is YYYY-MM-DD,Does not contain time information.'
 },{
 id:'time',name:'TIME',label:'Time',category:'DATETIME',icon:'clock',description:'time type,The format is HH:MM:SS,Optional precision 0-9,Does not contain date information.'
 },{
 id:'timestamp',name:'TIMESTAMP',label:'Timestamp',category:'DATETIME',icon:'calendar',description:'timestamp type,Contains date and time,Optional time zone and precision 0-9.'
 },// complex type (ComplexType)
 {
 id:'struct',name:'STRUCT',label:'Struct',category:'COMPLEX',icon:'layers',description:'Structure type,Contains multiple named fields,Each field can be of any type.'
 },{
 id:'list',name:'LIST',label:'List',category:'COMPLEX',icon:'list',description:'list type,Contains multiple elements of the same type,Elements can be empty or non-empty.'
 },{
 id:'map',name:'MAP',label:'Map',category:'COMPLEX',icon:'map',description:'Mapping type,key value pair structure,Keys and values can be of any type.'
 }]

/** icon map */
const ICON_MAP:Record<string,React.ElementType> = {
 hash:Hash,database:Database,braces:Braces,type:Type,calendar:Calendar,binary:Binary,clock:Clock,toggle:ToggleLeft,code:FileCode,layers:Layers,list:List,map:Map,fingerprint:Fingerprint
}

interface DataTypeExplorerProps {
 onBack:() => void
 selectedType:DataTypeItem | null
 onSelectType:(type:DataTypeItem | null) => void
}

export function DataTypeExplorer({ onBack,selectedType,onSelectType }:DataTypeExplorerProps) {
 const [precision,setPrecision] = useState(18)
 const [scale,setScale] = useState(2)
 const [length,setLength] = useState(DEFAULT_LENGTH)

 // Organize data types into groups
 const groupedTypes = useMemo(() => {
 return DATA_TYPE_GROUPS.map((group) => ({...group,types:DATA_TYPES.filter((t) => t.category === group.id)
 }))
 },[])

 // Selected by default Decimal
 const currentType = selectedType || DATA_TYPES.find((t) => t.id === 'decimal')!
 const IconComponent = ICON_MAP[currentType.icon] || Hash
 const lengthMax = currentType.maxLength?? DEFAULT_MAX_LENGTH

 return (<div className="flex h-full w-full">
 {/* left type list */}
 <div className={`${panelWidthClassMap.wide} border-r border-slate-200 dark:border-slate-800 flex flex-col bg-slate-50/50 dark:bg-slate-900/50`}>
 {/* head */}
 <div className="p-4 border-b border-slate-200 dark:border-slate-800">
 <button
 onClick={onBack}
 className="flex items-center gap-1.5 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 mb-3 transition-colors"
 >
 <ArrowLeft size={iconSizeToken.small} />
 <span className="text-micro font-medium">Return</span>
 </button>
 <div className="flex items-center gap-2.5">
 <div className="w-10 h-10 rounded-lg bg-slate-800 dark:bg-slate-700 flex items-center justify-center text-white">
 <Database size={iconSizeToken.medium} />
 </div>
 <div>
 <h1 className="text-body font-semibold text-slate-900 dark:text-slate-100">TYPE LIBRARY</h1>
 <p className="text-micro text-slate-400 uppercase tracking-wider">METADATA STANDARD</p>
 </div>
 </div>
 </div>

 {/* Type list */}
 <div className="flex-1 overflow-auto p-3 space-y-4">
 {groupedTypes.map((group) => (<div key={group.id}>
 <h3 className="text-micro font-medium text-slate-400 uppercase tracking-wider mb-2 px-2">{group.label}</h3>
 <div className="space-y-0.5">
 {group.types.map((type) => {
 const TypeIcon = ICON_MAP[type.icon] || Hash
 const isSelected = currentType.id === type.id
 return (<button
 key={type.id}
 onClick={() => {
 if (type.id!== currentType.id) {
 setLength(DEFAULT_LENGTH)
 }
 onSelectType(type)
 }}
 className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg transition-all duration-200 ${
 isSelected?'bg-white dark:bg-slate-800 shadow-sm border border-slate-200 dark:border-slate-700':'hover:bg-white dark:hover:bg-slate-800 hover:shadow-sm hover:border hover:border-slate-200 dark:hover:border-slate-700 border border-transparent'
 }`}
 >
 <div
 className={`w-8 h-8 rounded-md flex items-center justify-center transition-colors duration-200 ${
 isSelected?'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400':'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 group-hover:bg-slate-200 dark:group-hover:bg-slate-700'
 }`}
 >
 <TypeIcon size={iconSizeToken.small} />
 </div>
 <span className={`text-body-sm font-medium ${isSelected?'text-slate-900 dark:text-slate-100':'text-slate-600 dark:text-slate-400'}`}>{type.label}</span>
 {isSelected && (<div className="ml-auto">
 <Check size={iconSizeToken.small} className="text-blue-600 dark:text-blue-400" />
 </div>)}
 </button>)
 })}
 </div>
 </div>))}
 </div>
 </div>

 {/* Details on the right */}
 <div className="flex-1 overflow-auto p-6">
 <div className="max-w-2xl">
 {/* label */}
 {currentType.badge && (<div className="inline-flex px-3 py-1 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 text-micro font-medium uppercase tracking-wider mb-3">
 {currentType.badge}
 </div>)}

 {/* Title */}
 <h1 className="text-heading font-semibold text-slate-900 dark:text-slate-100 mb-1.5">
 {currentType.label}{' '}
 {currentType.hasPrecision && <span className="font-light text-slate-400 dark:text-slate-500">Precision</span>}
 {currentType.hasLength && <span className="font-light text-slate-400 dark:text-slate-500">Length</span>}
 </h1>

 {/* Description */}
 <p className="text-body-sm text-slate-600 dark:text-slate-400 mb-6 leading-relaxed">{currentType.description}</p>

 {/* Decimal Exclusive visualization */}
 {currentType.hasPrecision && (<>
 {/* Storage mask visualization */}
 <div className="bg-slate-900 dark:bg-slate-800 rounded-xl p-6 mb-6">
 <div className="text-micro font-medium text-slate-400 uppercase tracking-widest mb-4">LIVE STORAGE MASK</div>
 <div className="flex justify-center mb-6">
 <div className="flex gap-0.5">
 {Array.from({ length:precision }).map((_,i) => (<div
 key={i}
 className={`w-3 h-8 ${i >= precision - scale?'bg-amber-500':'bg-white'} ${i === 0?'rounded-l':''} ${i === precision - 1?'rounded-r':''}`}
 />))}
 </div>
 </div>
 <div className="flex justify-center gap-12">
 <div>
 <div className="text-micro font-medium text-slate-500 uppercase tracking-wider mb-0.5">TOTAL PRECISION</div>
 <div className="flex items-baseline gap-1.5">
 <span className="text-heading font-semibold text-white">{precision}</span>
 <span className="text-micro text-slate-500 uppercase">DIGITS</span>
 </div>
 </div>
 <div className="w-px bg-slate-700" />
 <div>
 <div className="text-micro font-medium text-amber-500 uppercase tracking-wider mb-0.5">DECIMAL SCALE</div>
 <div className="flex items-baseline gap-1.5">
 <span className="text-heading font-semibold text-amber-500">{scale}</span>
 <span className="text-micro text-slate-500 uppercase">POINTS</span>
 </div>
 </div>
 </div>
 </div>

 {/* Configure slider */}
 <div className="space-y-4">
 {/* Precision */}
 <div className="flex items-center gap-4">
 <div className="flex items-center gap-1.5 w-28">
 <div className="w-2.5 h-2.5 rounded-full bg-slate-900 dark:bg-white" />
 <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">PRECISION</span>
 </div>
 <div className="flex-1">
 <input
 type="range"
 min={1}
 max={currentType.maxPrecision || 38}
 value={precision}
 onChange={(e) => {
 const newPrecision = Number(e.target.value)
 setPrecision(newPrecision)
 if (scale > newPrecision) setScale(newPrecision)
 }}
 className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-slate-900 dark:accent-white"
 />
 </div>
 <div className="flex items-center gap-1.5 w-20 justify-end">
 <span className="text-body font-semibold text-blue-600 dark:text-blue-400">{precision}</span>
 <span className="text-micro text-slate-400">/ {currentType.maxPrecision || 38}</span>
 </div>
 </div>

 {/* Scale */}
 <div className="flex items-center gap-4">
 <div className="flex items-center gap-1.5 w-28">
 <div className="w-2.5 h-2.5 rounded-full bg-amber-500" />
 <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">SCALE</span>
 </div>
 <div className="flex-1">
 <input
 type="range"
 min={0}
 max={precision}
 value={scale}
 onChange={(e) => setScale(Number(e.target.value))}
 className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
 />
 </div>
 <div className="flex items-center gap-1.5 w-20 justify-end">
 <span className="text-body font-semibold text-amber-600 dark:text-amber-400">{scale}</span>
 <span className="text-micro text-slate-400">/ {precision}</span>
 </div>
 </div>
 </div>
 </>)}

 {/* VARCHAR/CHAR length visualization */}
 {currentType.hasLength && (<>
 {/* length visualization */}
 <div className="bg-slate-900 dark:bg-slate-800 rounded-xl p-6 mb-6">
 <div className="text-micro font-medium text-slate-400 uppercase tracking-widest mb-4">LENGTH CONFIGURATION</div>
 <div className="flex justify-center mb-6">
 <div className="flex gap-0.5">
 {Array.from({ length:Math.min(length,32) }).map((_,i) => (<div
 key={i}
 className={`w-2.5 h-8 bg-blue-500 ${i === 0?'rounded-l':''} ${i === Math.min(length,32) - 1?'rounded-r':''}`}
 />))}
 {length > 32 && <span className="text-slate-400 ml-2 self-center text-caption">+{length - 32}</span>}
 </div>
 </div>
 <div className="flex justify-center">
 <div>
 <div className="text-micro font-medium text-blue-500 uppercase tracking-wider mb-0.5">MAX LENGTH</div>
 <div className="flex items-baseline gap-1.5 justify-center">
 <span className="text-heading font-semibold text-blue-500">{length}</span>
 <span className="text-micro text-slate-500 uppercase">CHARS</span>
 </div>
 </div>
 </div>
 </div>

 {/* length slider */}
 <div className="flex items-center gap-4">
 <div className="flex items-center gap-1.5 w-28">
 <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />
 <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">LENGTH</span>
 </div>
 <div className="flex-1">
 <input
 type="range"
 min={1}
 max={lengthMax}
 value={length}
 onChange={(e) => setLength(Number(e.target.value))}
 className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
 />
 </div>
 <div className="flex items-center gap-1.5 w-24 justify-end">
 <span className="text-body font-semibold text-blue-600 dark:text-blue-400">{length}</span>
 <span className="text-micro text-slate-400">/ {lengthMax}</span>
 </div>
 </div>
 </>)}

 {/* Simple display of basic types */}
 {!currentType.hasPrecision &&!currentType.hasLength && (<div className="bg-slate-100 dark:bg-slate-800 rounded-xl p-6">
 <div className="flex items-center justify-center">
 <div className="w-16 h-16 rounded-xl bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
 <IconComponent size={32} className="text-slate-500 dark:text-slate-400" />
 </div>
 </div>
 <div className="text-center mt-4">
 <div className="text-micro font-medium text-slate-400 uppercase tracking-wider">DATA TYPE</div>
 <div className="text-heading font-semibold text-slate-900 dark:text-slate-100 mt-0.5">{currentType.name}</div>
 </div>
 </div>)}
 </div>
 </div>
 </div>)
}
