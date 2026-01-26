import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

type WorkerCtor = new () => Worker

const workerMap: Record<string, WorkerCtor> = {
  json: jsonWorker,
  css: cssWorker,
  scss: cssWorker,
  less: cssWorker,
  html: htmlWorker,
  handlebars: htmlWorker,
  razor: htmlWorker,
  typescript: tsWorker,
  javascript: tsWorker
}

const getWorker = (_: unknown, label: string) => {
  const WorkerCtor = workerMap[label] ?? editorWorker
  return new WorkerCtor()
}

const globalScope = globalThis as typeof globalThis & {
  MonacoEnvironment?: {
    getWorker: (workerId: string, label: string) => Worker
  }
}

globalScope.MonacoEnvironment = { getWorker }
loader.config({ monaco })
