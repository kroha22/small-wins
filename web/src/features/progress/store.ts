import { create } from 'zustand';
import { z } from 'zod';
import { readRecord, writeRecord, clearRecords } from './storage';
const completionSchema = z.object({ moves: z.number().int().nonnegative(), assisted: z.boolean() });
const progressSchema = z.record(z.string(), completionSchema);
const settingsSchema = z.object({ reduceMotion: z.boolean() });
type Completion = z.infer<typeof completionSchema>;
type Settings = z.infer<typeof settingsSchema>;
export const useProgress = create<{
  completed: Record<string, Completion>;
  settings: Settings;
  complete: (key: string, result: Completion) => void;
  setSettings: (s: Settings) => void;
  clear: () => void;
}>((set, get) => ({
  completed: progressSchema.safeParse(readRecord('progress')).data ?? {},
  settings: settingsSchema.safeParse(readRecord('settings')).data ?? { reduceMotion: false },
  complete: (key, result) => {
    if (get().completed[key]) return;
    const completed = { ...get().completed, [key]: result };
    writeRecord('progress', completed);
    set({ completed });
  },
  setSettings: (settings) => {
    writeRecord('settings', settings);
    set({ settings });
  },
  clear: () => {
    clearRecords();
    set({ completed: {}, settings: { reduceMotion: false } });
  },
}));
export const completionKey = (gameId: string, levelId: string, version: number) =>
  `${gameId}/${levelId}/${version}`;
