import { Armchair, BedDouble, PanelsTopLeft, Sprout, Circle } from 'lucide-react';

export function HouseToken({ id, size = 24 }: { id: string; size?: number }) {
  if (id === 'bowl')
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M3 11h18l-3 9H6Z" />
        <circle cx="8" cy="8" r="1.5" />
        <circle cx="15" cy="8" r="1.5" />
        <circle cx="11.5" cy="4.5" r="1.5" />
      </svg>
    );
  const Icon = { armchair: Armchair, bed: BedDouble, window: PanelsTopLeft, plant: Sprout }[id] ?? Circle;
  return <Icon size={size} strokeWidth={1.7} aria-hidden="true" />;
}
