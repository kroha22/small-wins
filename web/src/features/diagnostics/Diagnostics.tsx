import { clearDiagnostics, diagnosticsJson, useDiagnosticCount } from './log';

export function Diagnostics() {
  const count = useDiagnosticCount();
  function download() {
    const url = URL.createObjectURL(new Blob([diagnosticsJson()], { type: 'application/json' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'small-wins-diagnostics.json';
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return (
    <section className="settings-diagnostics" aria-label="Local diagnostics">
      <h3>Something jumped?</h3>
      <p>
        The last 200 Untangle moves and drag cancellations stay in this tab’s memory. Nothing is
        sent anywhere. Download before reloading if you want to report a problem.
      </p>
      <p>{count} events recorded</p>
      <div className="diagnostic-actions">
        <button className="text-button" disabled={!count} onClick={download}>
          Download diagnostics
        </button>
        <button className="text-button" disabled={!count} onClick={clearDiagnostics}>
          Clear diagnostics
        </button>
      </div>
    </section>
  );
}
