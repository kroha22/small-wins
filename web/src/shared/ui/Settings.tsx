import { useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { Settings2, X } from 'lucide-react';
import { useNavigate } from '@tanstack/react-router';
import { useProgress } from '../../features/progress/store';
import { Diagnostics } from '../../features/diagnostics/Diagnostics';
import { clearPlaytestRecords, playtestJson, setPlaytestEnabled, usePlaytestSnapshot } from '../../features/playtest/log';
export function Settings() {
  const [open, setOpen] = useState(false),
    [confirm, setConfirm] = useState(false);
  const playtest = usePlaytestSnapshot();
  const settings = useProgress((s) => s.settings),
    setSettings = useProgress((s) => s.setSettings),
    clear = useProgress((s) => s.clear);
  const navigate = useNavigate();
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(value) => {
        setOpen(value);
        setConfirm(false);
      }}
    >
      <Dialog.Trigger aria-label="Settings" className="settings-trigger">
        <Settings2 size={18} />
        <span>Settings</span>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay" />
        <Dialog.Content className="dialog-content">
          <Dialog.Title>A little more comfortable.</Dialog.Title>
          <Dialog.Description>
            Your puzzles stay on this device. No account needed.
          </Dialog.Description>
          <label className="setting-row">
            <span>
              <strong>Less movement</strong>
              <small>Keep transitions gentle and simple.</small>
            </span>
            <input
              type="checkbox"
              checked={settings.reduceMotion}
              onChange={(event) => setSettings({ reduceMotion: event.target.checked })}
            />
          </label>
          <p className="muted text-sm">
            We also respect your device’s reduced motion preference. Cat number marks are always
            visible.
          </p>
          <Diagnostics />
          <section className="settings-diagnostics" aria-label="Internal playtest">
            <h3>Internal playtest</h3>
            <p>Collect level, time, moves, hints and restarts locally for a balance check.</p>
            <label className="setting-row">
              <span><strong>Record playtest</strong><small>Nothing leaves this device.</small></span>
              <input type="checkbox" checked={playtest.enabled} onChange={(event) => setPlaytestEnabled(event.target.checked)} />
            </label>
            <p>{playtest.count} attempts recorded</p>
            <div className="diagnostic-actions">
              <button className="text-button" disabled={!playtest.count} onClick={() => {
                const url = URL.createObjectURL(new Blob([playtestJson()], { type: 'application/json' }));
                const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'small-wins-playtest.json'; anchor.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
              }}>Download playtest</button>
              <button className="text-button" disabled={!playtest.count} onClick={clearPlaytestRecords}>Clear playtest</button>
            </div>
          </section>
          <div className="settings-danger">
            <h3>A fresh notebook</h3>
            <p>Clear completed puzzles, settings and your last attempt on this device.</p>
            {confirm ? (
              <div className="confirm-clear">
                <p>Clear all local progress? This cannot be undone.</p>
                <button
                  className="button danger"
                  onClick={() => {
                    clear();
                    clearPlaytestRecords();
                    setOpen(false);
                    void navigate({ to: '/' });
                  }}
                >
                  Yes, clear everything
                </button>
                <button className="text-button" onClick={() => setConfirm(false)}>
                  Keep my progress
                </button>
              </div>
            ) : (
              <button className="text-button" onClick={() => setConfirm(true)}>
                Clear local progress
              </button>
            )}
          </div>
          <Dialog.Close className="dialog-close" aria-label="Close settings">
            <X size={20} />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
