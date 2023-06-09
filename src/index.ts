import { registerPlugin } from '@capacitor/core';

import type { MultiContactSelectPlugin } from './definitions';

const MultiContactSelect = registerPlugin<MultiContactSelectPlugin>('MultiContactSelect', {
  web: () => import('./web').then(m => new m.MultiContactSelectWeb()),
});

export * from './definitions';
export { MultiContactSelect };
