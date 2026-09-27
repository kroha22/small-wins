import { solverHint, type Payload, type State } from './model';
export type Request = {
  requestId: string;
  attemptId: string;
  revision: number;
  contentVersion: number;
  level: Payload;
  state: State;
  used: number;
};
export type Reply = Omit<Request, 'level' | 'state' | 'used'> & ReturnType<typeof solverHint>;
self.onmessage = (event: MessageEvent<Request>) => {
  const { level, state, used, ...identity } = event.data;
  self.postMessage({ ...identity, ...solverHint(level, state, used) });
};
