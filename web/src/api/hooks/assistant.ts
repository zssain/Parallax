import { useMutation, useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type { AssistantStatus, ChatResponse, ToolView } from '../types'

export function useAssistantStatus() {
  return useQuery({
    queryKey: keys.assistantStatus,
    queryFn: () => apiFetch<AssistantStatus>('/api/v1/assistant/status'),
    retry: false,
  })
}

export function useAssistantTools() {
  return useQuery({
    queryKey: keys.assistantTools,
    queryFn: () => apiFetch<ToolView[]>('/api/v1/assistant/tools'),
    retry: false,
  })
}

export function useAssistantChat() {
  return useMutation({
    mutationFn: (vars: { conversationId?: string; message: string }) =>
      apiFetch<ChatResponse>('/api/v1/assistant/chat', { method: 'POST', body: vars }),
  })
}
