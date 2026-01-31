import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import { conf as sqlConf, language as sqlLanguage } from 'monaco-editor/esm/vs/basic-languages/sql/sql'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'

type WorkerCtor = new () => Worker

const EditorWorker: WorkerCtor = editorWorker

const getWorker = (_: unknown, _label: string) => {
  return new EditorWorker()
}

const globalScope = globalThis as typeof globalThis & {
  MonacoEnvironment?: {
    getWorker: (workerId: string, label: string) => Worker
  }
}

globalScope.MonacoEnvironment = { getWorker }
loader.config({ monaco })

const sqlLanguageId = 'sql'
const hasSql = monaco.languages.getLanguages().some((lang) => lang.id === sqlLanguageId)
if (!hasSql) {
  monaco.languages.register({ id: sqlLanguageId })
}
monaco.languages.setMonarchTokensProvider(sqlLanguageId, sqlLanguage)
monaco.languages.setLanguageConfiguration(sqlLanguageId, sqlConf)
