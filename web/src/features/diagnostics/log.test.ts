import { afterEach, expect, it } from 'vitest';
import { clearDiagnostics, diagnosticsJson, getDiagnostics, logDiagnostic } from './log';
afterEach(clearDiagnostics);
it('bounds the buffer and snapshots positions without mutating live game state', () => {
  const positions = { A: { x: 10, y: 20 } };
  for (let i = 0; i < 205; i++)
    logDiagnostic({
      event: 'move-accepted',
      levelId: 'level-4',
      contentVersion: 1,
      nodeId: 'A',
      input: 'drag',
      from: positions.A,
      to: { x: 50, y: 50 },
      positions,
    });
  positions.A.x = 99;
  const report = JSON.parse(diagnosticsJson());
  expect(report.events).toHaveLength(200);
  expect(report.events[0].positions.A.x).toBe(10);
  expect(report.events.at(-1).sequence - report.events[0].sequence).toBe(199);
  clearDiagnostics();
  expect(getDiagnostics()).toHaveLength(0);
  expect(positions.A.x).toBe(99);
});
