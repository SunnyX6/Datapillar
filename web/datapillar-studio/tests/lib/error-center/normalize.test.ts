import { describe,expect,it } from 'vitest'
import { normalizeApiPayloadError } from '@/api/errorCenter'
import type { ErrorResponse } from '@/api/types/api'

describe('error-center normalize',() => {
 it('Extract from response headers requestId and traceId',() => {
 const payload:ErrorResponse = {
 code:401,type:'UNAUTHORIZED',message:'The system has not yet completed initialization'
 }

 const error = normalizeApiPayloadError(payload,{
 module:'api/client',status:401,requestUrl:'/api/studio/setup/status',method:'GET',isCoreRequest:true,headers:{
 'x-request-id':'req-123','x-trace-id':'trace-456'
 }
 })

 expect(error.requestId).toBe('req-123')
 expect(error.traceId).toBe('trace-456')
 })

 it('The module field contains source pages and technical modules',() => {
 const payload:ErrorResponse = {
 code:503,type:'SERVICE_UNAVAILABLE',message:'System initialization data is not ready'
 }

 const error = normalizeApiPayloadError(payload,{
 module:'api/client',route:'/projects',status:503,requestUrl:'/api/studio/setup/status',method:'GET',isCoreRequest:true
 })

 expect(error.module).toBe('/projects.api/client')
 })
})
