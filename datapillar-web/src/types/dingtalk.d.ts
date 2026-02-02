interface Window {
  DTFrameLogin?: (
    options: { id: string; width?: number; height?: number },
    params: Record<string, unknown>,
    onSuccess?: (result: { authCode?: string; state?: string; redirectUrl?: string }) => void,
    onError?: (error: string) => void
  ) => void
}
