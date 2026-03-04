/**
 * Scene status management Hook
 *
 * Function:* 1.Manage the lifecycle state of a scene
 * 2.State driven animation,Avoid delayed stacking
 * 3.Realize real user interaction process simulation
 * 4.use useReducer Avoid cascading rendering
 */

import { useReducer,useEffect,useLayoutEffect,useMemo,useRef } from 'react'
import { useFrameScheduler } from '../useFrameScheduler'
import type { Scenario } from './useScenario'

const getCommonPrefixLength = (a:string,b:string) => {
 const minLen = Math.min(a.length,b.length)
 let i = 0
 while (i < minLen && a[i] === b[i]) {
 i += 1
 }
 return i
}

const getCommonSuffixLength = (a:string,b:string) => {
 const minLen = Math.min(a.length,b.length)
 let i = 0
 while (i < minLen && a[a.length - 1 - i] === b[b.length - 1 - i]) {
 i += 1
 }
 return i
}

const calculateCaretPosition = (prevText:string,nextText:string,prevCaret:number) => {
 if (prevText === nextText) {
 return Math.min(prevCaret,nextText.length)
 }

 const prefixLen = getCommonPrefixLength(prevText,nextText)
 const suffixLen = getCommonSuffixLength(prevText.slice(prefixLen),nextText.slice(prefixLen))

 if (nextText.length < prevText.length) {
 return prefixLen
 }

 return Math.max(prefixLen,nextText.length - suffixLen)
}

/**
 * Scene status
 */
export enum ScenarioPhase {
 TYPING_INPUT = 'typing_input',// The user is typing
 AGENT_ANALYZING = 'agent_analyzing',// Agent Analyzing
 BUILDING = 'building',// Build is executing
 COMPLETED = 'completed',// Complete
 WAITING = 'waiting' // Waiting for the next scene
}

/**
 * Scene status management results
 */
export interface ScenarioState {
 phase:ScenarioPhase
 inputProgress:number // Enter progress 0-100
 currentInputText:string // Currently displayed input text
 caretPosition:number // Current cursor position
 currentStepIndex:number // Current input step index
 leftLogProgress:number // Log progress on the left 0-100
 rightLogProgress:number // Log progress on the right 0-100
 activeNodeIndex:number // currently active node index
 isInputComplete:boolean // Is the input completed?
 isLeftLogComplete:boolean // Is the log on the left completed?
 isRightLogComplete:boolean // Is the log on the right completed?
}

interface NormalizedInputStep {
 text:string
 duration:number
 caretPosition:number
}

/**
 * Scene status Action
 */
type ScenarioAction =
 | { type:'RESET' }
 | { type:'SET_INPUT_TEXT';text:string;caretPosition:number }
 | { type:'NEXT_INPUT_STEP';progress:number }
 | { type:'COMPLETE_INPUT' }
 | { type:'START_ANALYZING' }
 | { type:'UPDATE_LEFT_LOG';progress:number }
 | { type:'ACTIVATE_NODE';index:number }
 | { type:'START_BUILDING' }
 | { type:'UPDATE_RIGHT_LOG';progress:number }
 | { type:'COMPLETE' }
 | { type:'WAITING' }

/**
 * initial state
 */
const initialState:ScenarioState = {
 phase:ScenarioPhase.TYPING_INPUT,inputProgress:0,currentInputText:'',caretPosition:0,currentStepIndex:0,leftLogProgress:0,rightLogProgress:0,activeNodeIndex:-1,isInputComplete:false,isLeftLogComplete:false,isRightLogComplete:false
}

/**
 * Status Reducer
 */
function scenarioReducer(state:ScenarioState,action:ScenarioAction):ScenarioState {
 switch (action.type) {
 case 'RESET':return {...initialState }

 case 'SET_INPUT_TEXT':return {...state,currentInputText:action.text,caretPosition:action.caretPosition }

 case 'NEXT_INPUT_STEP':return {...state,currentStepIndex:state.currentStepIndex + 1,inputProgress:action.progress
 }

 case 'COMPLETE_INPUT':return {...state,inputProgress:100,isInputComplete:true
 }

 case 'START_ANALYZING':return {...state,phase:ScenarioPhase.AGENT_ANALYZING
 }

 case 'UPDATE_LEFT_LOG':return {...state,leftLogProgress:action.progress,isLeftLogComplete:action.progress >= 100
 }

 case 'ACTIVATE_NODE':return {...state,activeNodeIndex:action.index
 }

 case 'START_BUILDING':return {...state,phase:ScenarioPhase.BUILDING
 }

 case 'UPDATE_RIGHT_LOG':return {...state,rightLogProgress:action.progress,isRightLogComplete:action.progress >= 100
 }

 case 'COMPLETE':return {...state,phase:ScenarioPhase.COMPLETED
 }

 case 'WAITING':return {...state,phase:ScenarioPhase.WAITING
 }

 default:return state
 }
}

interface UseScenarioStateOptions {
 isActive?: boolean
}

/**
 * Scene status management Hook
 * @param scenario current scene
 * @param onComplete Scene completion callback
 * @returns Scene status
 */
export function useScenarioState(scenario:Scenario,onComplete:() => void,options?: UseScenarioStateOptions):ScenarioState {
 const [state,dispatch] = useReducer(scenarioReducer,initialState)
 const isActive = options?.isActive?? true
 const { setFrameTimeout,setFrameInterval,clearFrameTask,clearAllTasks } = useFrameScheduler(isActive)

 const normalizedInputSteps = useMemo(() => {
 const steps = scenario.inputSteps
 if (!steps.length) return [] as NormalizedInputStep[]

 const accelFactor = 0.7
 const minDuration = 20
 const result:NormalizedInputStep[] = []
 let lastCaret = 0

 for (let i = 0;i < steps.length;i++) {
 const current = steps[i]
 const prevText = i === 0?'':steps[i - 1].text
 const currentText = current.text

 if (prevText === currentText) {
 const duration = Math.max(minDuration,Math.floor(current.duration * accelFactor))
 const caretPosition = Math.min(lastCaret,currentText.length)
 result.push({ text:currentText,duration,caretPosition })
 lastCaret = caretPosition
 continue
 }

 const prefixLen = getCommonPrefixLength(prevText,currentText)
 const suffixLen = getCommonSuffixLength(prevText.slice(prefixLen),currentText.slice(prefixLen))

 const prevMiddleEnd = prevText.length - suffixLen
 const prevMiddleLength = Math.max(0,prevMiddleEnd - prefixLen)
 const currMiddle = currentText.slice(prefixLen,currentText.length - suffixLen)

 const totalSteps = Math.max(1,prevMiddleLength + currMiddle.length)
 const baseDuration = Math.max(minDuration,Math.floor((current.duration * accelFactor) / totalSteps))

 let evolvingText = prevText
 let caretPosition = Math.min(prevText.length,prefixLen + prevMiddleLength)
 let lastEntryIndex = -1

 if (prevMiddleLength > 0) {
 let deleteCaret = caretPosition
 for (let j = 0;j < prevMiddleLength;j++) {
 const deleteIndex = deleteCaret - 1
 evolvingText =
 evolvingText.slice(0,deleteIndex) + evolvingText.slice(deleteIndex + 1)
 deleteCaret -= 1
 result.push({ text:evolvingText,duration:baseDuration,caretPosition:deleteCaret })
 lastEntryIndex = result.length - 1
 }
 caretPosition = Math.max(prefixLen,caretPosition - prevMiddleLength)
 }

 const insertStartIndex = result.length
 if (currMiddle.length > 0) {
 let insertCaret = caretPosition
 for (let j = 0;j < currMiddle.length;j++) {
 const insertPosition = insertCaret
 evolvingText =
 evolvingText.slice(0,insertPosition) +
 currMiddle[j] +
 evolvingText.slice(insertPosition)
 insertCaret += 1
 result.push({ text:evolvingText,duration:baseDuration,caretPosition:insertCaret })
 lastEntryIndex = result.length - 1
 }
 caretPosition = insertCaret
 }

 if (currMiddle.length > 0 && lastEntryIndex >= insertStartIndex) {
 const adjustedCaret = Math.min(currentText.length,caretPosition + suffixLen)
 const entry = result[lastEntryIndex]
 result[lastEntryIndex] = {...entry,caretPosition:adjustedCaret
 }
 lastCaret = adjustedCaret
 } else if (prevMiddleLength > 0 && lastEntryIndex >= 0) {
 lastCaret = Math.max(prefixLen,caretPosition)
 const entry = result[lastEntryIndex]
 result[lastEntryIndex] = {...entry,caretPosition:lastCaret
 }
 } else {
 lastCaret = Math.min(currentText.length,caretPosition + suffixLen)
 }
 }

 return result
 },[scenario.inputSteps])

 const scenarioRef = useRef(scenario)
 const leftLogLengthRef = useRef(scenario.leftLogs.join('').length)
 const rightLogLengthRef = useRef(scenario.rightLogs.join('').length)
 const completionHandledRef = useRef(false)
 const completionTimerRef = useRef<number | null>(null)

 // Monitor scene changes,reset state(use useLayoutEffect Avoid briefly displaying the old language when switching languages)
 useLayoutEffect(() => {
 // Cant just use scenario.id judge:The same scene is in both Chinese and English sets of data id Same,But the content is different.// When switching languages,you need to input/Log/Nodes and other states are reset together,Otherwise it will appear"The page has been switched to the new language,But the input box is still in the old language"inconsistency.
 if (scenarioRef.current!== scenario) {
 scenarioRef.current = scenario
 leftLogLengthRef.current = scenario.leftLogs.join('').length
 rightLogLengthRef.current = scenario.rightLogs.join('').length

 // Immediately clear the frame task of the previous scene,Avoid language inconsistency caused by old tasks continuing to write state/flashing.clearAllTasks()
 completionTimerRef.current = null
 completionHandledRef.current = false

 // use reducer Reset all states at once
 dispatch({ type:'RESET' })
 }
 },[scenario,clearAllTasks])

 // stage1:user input(follow inputSteps Show step by step)
 useEffect(() => {
 if (state.phase!== ScenarioPhase.TYPING_INPUT) return
 if (state.currentStepIndex >= normalizedInputSteps.length) return

 const currentStep = normalizedInputSteps[state.currentStepIndex]
 const caretPosition = currentStep.caretPosition?? calculateCaretPosition(state.currentInputText,currentStep.text,state.caretPosition)

 dispatch({ type:'SET_INPUT_TEXT',text:currentStep.text,caretPosition })

 let analyzingTimerId:number | null = null
 const typingTimerId = setFrameTimeout(() => {
 const nextIndex = state.currentStepIndex + 1
 if (nextIndex >= normalizedInputSteps.length) {
 dispatch({ type:'COMPLETE_INPUT' })
 analyzingTimerId = setFrameTimeout(() => dispatch({ type:'START_ANALYZING' }),300)
 } else {
 dispatch({
 type:'NEXT_INPUT_STEP',progress:(nextIndex / normalizedInputSteps.length) * 100
 })
 }
 },currentStep.duration)

 return () => {
 clearFrameTask(typingTimerId)
 if (analyzingTimerId!== null) {
 clearFrameTask(analyzingTimerId)
 }
 }
 },[state.phase,state.currentStepIndex,normalizedInputSteps,state.currentInputText,state.caretPosition,setFrameTimeout,clearFrameTask])

 // stage2:Agent analysis(Log on the left + Node real-time activation)
 useEffect(() => {
 if (state.phase!== ScenarioPhase.AGENT_ANALYZING) return

 const typeSpeed = 12 // 12ms/character
 const totalChars = Math.max(leftLogLengthRef.current,1)
 const increment = 100 / totalChars
 const nodesCount = scenario.nodes.length

 let progress = state.leftLogProgress
 let currentNodeIndex = state.activeNodeIndex?? -1
 let buildTimerId:number | null = null

 const logTimerId = setFrameInterval(() => {
 progress = Math.min(100,progress + increment)
 dispatch({ type:'UPDATE_LEFT_LOG',progress })

 if (nodesCount > 0) {
 const ratio = progress / 100
 const desiredIndex = Math.max(-1,Math.min(nodesCount - 1,Math.ceil(ratio * nodesCount) - 1))

 if (desiredIndex!== currentNodeIndex) {
 currentNodeIndex = desiredIndex
 if (currentNodeIndex >= 0) {
 dispatch({ type:'ACTIVATE_NODE',index:currentNodeIndex })
 }
 }
 }

 if (progress >= 100) {
 clearFrameTask(logTimerId)

 if (nodesCount > 0 && currentNodeIndex < nodesCount - 1) {
 dispatch({ type:'ACTIVATE_NODE',index:nodesCount - 1 })
 }

 buildTimerId = setFrameTimeout(() => dispatch({ type:'START_BUILDING' }),300)
 }
 },typeSpeed)

 return () => {
 clearFrameTask(logTimerId)
 if (buildTimerId) clearFrameTask(buildTimerId)
 }
 },[state.phase,scenario.nodes.length,state.leftLogProgress,state.activeNodeIndex,setFrameInterval,clearFrameTask,setFrameTimeout])

 // stage3:Build execution(Only output the right log)
 useEffect(() => {
 if (state.phase!== ScenarioPhase.BUILDING) return

 const typeSpeed = 8 // 8ms/character
 const totalChars = Math.max(rightLogLengthRef.current,1)

 let completionTimerId:number | null = null
 const logTimerId = setFrameInterval(() => {
 const next = state.rightLogProgress + (100 / totalChars)
 if (next >= 100) {
 clearFrameTask(logTimerId)
 dispatch({ type:'UPDATE_RIGHT_LOG',progress:100 })
 completionTimerId = setFrameTimeout(() => dispatch({ type:'COMPLETE' }),500)
 } else {
 dispatch({ type:'UPDATE_RIGHT_LOG',progress:next })
 }
 },typeSpeed)

 return () => {
 clearFrameTask(logTimerId)
 if (completionTimerId) {
 clearFrameTask(completionTimerId)
 }
 }
 },[state.phase,state.rightLogProgress,setFrameInterval,clearFrameTask,setFrameTimeout])

 // stage4:Complete - Switch to the next scene immediately
 useEffect(() => {
 if (state.phase === ScenarioPhase.COMPLETED) {
 if (!completionHandledRef.current) {
 completionHandledRef.current = true
 completionTimerRef.current = setFrameTimeout(() => {
 completionTimerRef.current = null
 dispatch({ type:'WAITING' })
 onComplete()
 },200) // micro pause,Make sure to switch immediately after the log on the right is completed
 }
 } else {
 completionHandledRef.current = false
 if (completionTimerRef.current) {
 clearFrameTask(completionTimerRef.current)
 completionTimerRef.current = null
 }
 }
 },[state.phase,onComplete,setFrameTimeout,clearFrameTask])

 return state
}
