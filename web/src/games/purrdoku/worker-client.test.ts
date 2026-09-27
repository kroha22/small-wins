import { describe, it, expect, vi, afterEach } from 'vitest';
import { requestHint, matchesRequest } from './worker-client';
import { payloadSchema, engine } from './model';
import levels from './levels.json';
const level = payloadSchema.parse(levels[0]!.payload);
const request = {
  used: 0,
  requestId: 'req',
  attemptId: 'attempt',
  revision: 3,
  contentVersion: 1,
  level,
  state: engine.initial(level),
};
class FakeWorker {
  static latest: FakeWorker;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  terminate = vi.fn();
  postMessage = vi.fn();
  constructor() {
    FakeWorker.latest = this;
  }
}
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
describe('Solver worker lifecycle', () => {
  it('requires all identity fields, including content version', () => {
    for (const field of ['requestId', 'attemptId', 'revision', 'contentVersion'])
      expect(
        matchesRequest(request, { ...request, [field]: 'stale', status: 'ok', text: 'Hint' }),
      ).toBe(false);
  });
  it('terminates on deadline without delivering or charging a hint', () => {
    vi.useFakeTimers();
    vi.stubGlobal('Worker', FakeWorker);
    const receive = vi.fn(),
      failed = vi.fn();
    requestHint(request, receive, failed);
    vi.advanceTimersByTime(1000);
    expect(failed).toHaveBeenCalledOnce();
    expect(receive).not.toHaveBeenCalled();
    expect(FakeWorker.latest.terminate).toHaveBeenCalledOnce();
  });
  it('ignores stale messages and messages after cancel', () => {
    vi.useFakeTimers();
    vi.stubGlobal('Worker', FakeWorker);
    const receive = vi.fn(),
      failed = vi.fn();
    const cancel = requestHint(request, receive, failed);
    FakeWorker.latest.onmessage?.({
      data: { ...request, revision: 1, status: 'ok', text: 'old' },
    } as MessageEvent);
    cancel();
    FakeWorker.latest.onmessage?.({
      data: { ...request, status: 'ok', text: 'late' },
    } as MessageEvent);
    vi.runAllTimers();
    expect(receive).not.toHaveBeenCalled();
    expect(failed).not.toHaveBeenCalled();
  });
});
