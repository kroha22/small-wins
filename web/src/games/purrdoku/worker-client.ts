import { type Request, type Reply } from './solver.worker';
export function matchesRequest(request: Request, reply: Reply) {
  return (
    request.requestId === reply.requestId &&
    request.attemptId === reply.attemptId &&
    request.revision === reply.revision &&
    request.contentVersion === reply.contentVersion
  );
}
export function requestHint(request: Request, receive: (reply: Reply) => void, failed: () => void) {
  let worker: Worker;
  let done = false;
  const timer: { id?: ReturnType<typeof setTimeout> } = {};
  const cancel = () => {
    done = true;
    clearTimeout(timer.id);
    worker?.terminate();
  };
  try {
    worker = new Worker(new URL('./solver.worker.ts', import.meta.url), { type: 'module' });
  } catch {
    failed();
    return () => {};
  }
  worker.onmessage = (event: MessageEvent<Reply>) => {
    if (done || !matchesRequest(request, event.data)) return;
    cancel();
    receive(event.data);
  };
  worker.onerror = () => {
    if (!done) {
      cancel();
      failed();
    }
  };
  timer.id = setTimeout(() => {
    if (!done) {
      cancel();
      failed();
    }
  }, 1000);
  worker.postMessage(request);
  return cancel;
}
