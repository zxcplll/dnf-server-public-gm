import { reactive, readonly } from 'vue';

export type PlayerInspectorTab = 'profile' | 'currency' | 'contracts' | 'inventory' | 'mail' | 'guild' | 'security';

const state = reactive({
  visible: false,
  characNo: 0,
  initialTab: 'profile' as PlayerInspectorTab,
  source: '',
});

export const openPlayerInspector = (characNo: number, initialTab: PlayerInspectorTab = 'profile', source = '') => {
  if (!Number.isFinite(Number(characNo)) || Number(characNo) <= 0) return;
  state.characNo = Number(characNo);
  state.initialTab = initialTab;
  state.source = source;
  state.visible = true;
};

export const closePlayerInspector = () => {
  state.visible = false;
};

export const usePlayerInspector = () => ({
  state: readonly(state),
  openPlayerInspector,
  closePlayerInspector,
});
