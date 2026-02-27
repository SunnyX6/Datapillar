import { create } from 'zustand'
import type { PermissionTab } from '../utils/permissionTypes'

interface PermissionUiState {
  selectedRoleId: string
  activeTab: PermissionTab
  isAddModalOpen: boolean
  setSelectedRoleId: (roleId: string) => void
  setActiveTab: (tab: PermissionTab) => void
  setAddModalOpen: (open: boolean) => void
  reset: () => void
}

const DEFAULT_UI_STATE = {
  selectedRoleId: '',
  activeTab: 'members' as PermissionTab,
  isAddModalOpen: false,
}

export const usePermissionUiStore = create<PermissionUiState>((set) => ({
  ...DEFAULT_UI_STATE,
  setSelectedRoleId: (roleId) => set({ selectedRoleId: roleId }),
  setActiveTab: (tab) => set({ activeTab: tab }),
  setAddModalOpen: (open) => set({ isAddModalOpen: open }),
  reset: () => set(DEFAULT_UI_STATE),
}))
