import { CatToken } from './CatToken';
import { Carrot, Flag, Rabbit } from 'lucide-react';
import { type GameId } from '../lib/engine';
export function Preview({ gameId }: { gameId: GameId }) {
  if (gameId === 'purrdoku')
    return (
      <div className="cat-preview" aria-hidden="true">
        {Array.from({ length: 9 }, (_, i) => (
          <span key={i}>
            {i === 0 ? (
              <CatToken id="ginger" mark={1} />
            ) : i === 4 ? (
              <CatToken id="black" mark={2} />
            ) : i === 8 ? (
              <CatToken id="white" mark={3} />
            ) : (
              <i />
            )}
          </span>
        ))}
        <span className="preview-sticker">
          a spot for
          <br />
          everyone.
        </span>
      </div>
    );
  if (gameId === 'pipes')
    return (
      <svg className="game-preview pipe-preview" viewBox="0 0 300 175" aria-hidden="true">
        <g stroke="#839DAA" opacity=".25" strokeWidth="1">
          {[65, 115, 165, 215].map((x) => (
            <path key={x} d={`M${x} 15v150`} />
          ))}
          {[15, 65, 115, 165].map((y) => (
            <path key={y} d={`M65 ${y}h150`} />
          ))}
        </g>
        <path
          d="M90 40h50v100h50V90h50"
          fill="none"
          stroke="#263B48"
          strokeWidth="24"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <path
          d="M90 40h50v100h50V90h50"
          fill="none"
          stroke="#537CAB"
          strokeWidth="17"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <circle cx="90" cy="40" r="12" fill="#F7F4ED" stroke="#263B48" strokeWidth="3" />
        <circle cx="240" cy="90" r="5" fill="#F7F4ED" />
      </svg>
    );
  if (gameId === 'untangle')
    return (
      <svg className="game-preview" viewBox="0 0 300 175" aria-hidden="true">
        <path
          d="M73 41 233 115 133 25 105 143 73 41 201 42 105 143"
          fill="none"
          stroke="#824D60"
          strokeWidth="3"
          strokeLinejoin="round"
        />
        {[
          [73, 41],
          [233, 115],
          [133, 25],
          [105, 143],
          [201, 42],
        ].map(([x, y], i) => (
          <g key={i}>
            <circle cx={x} cy={y} r="13" fill="#FAF4EB" stroke="#463039" strokeWidth="2" />
            <circle cx={x} cy={y} r="3" fill="#463039" />
          </g>
        ))}
      </svg>
    );
  if (gameId === 'shikaku')
    return (
      <svg className="game-preview shikaku-preview" viewBox="0 0 300 175" aria-hidden="true">
        <rect x="54" y="10" width="64" height="78" rx="8" fill="#F0B36B" />
        <rect x="122" y="10" width="124" height="40" rx="8" fill="#8FC8E5" />
        <rect x="122" y="54" width="64" height="76" rx="8" fill="#E2A8C1" />
        <rect x="190" y="54" width="56" height="76" rx="8" fill="#9ED0A5" />
        <rect x="54" y="92" width="64" height="38" rx="8" fill="#F4D27C" />
        <g fill="#263B48" fontSize="20" fontWeight="700" textAnchor="middle">
          <text x="86" y="56">2</text>
          <text x="184" y="38">2</text>
          <text x="154" y="101">4</text>
          <text x="218" y="101">2</text>
        </g>
        <path d="M54 10h192v120H54z" fill="none" stroke="#263B48" strokeWidth="2" opacity=".35" />
      </svg>
    );
  if (gameId === 'wildlife-survey')
    return <div className="wildlife-preview" aria-hidden="true"><img src="/wildlife-survey-habitats.png" alt="" /><span>look a little closer</span></div>;
  return (
    <svg className="game-preview" viewBox="0 0 300 175" aria-hidden="true">
      {Array.from({ length: 20 }, (_, i) => (
        <rect
          key={i}
          x={51 + (i % 5) * 39}
          y={10 + Math.floor(i / 5) * 39}
          width="33"
          height="33"
          rx="6"
          fill="#FAF9EF"
          opacity=".6"
        />
      ))}
      <path
        d="M68 26v78h78V65h39v78h39"
        fill="none"
        stroke="#3D6757"
        strokeWidth="7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {[
        [68, 26],
        [146, 104],
        [185, 65],
        [224, 143],
      ].map(([x, y], i) => (
        <g key={i}>
          <circle
            cx={x}
            cy={y}
            r="12"
            fill={i === 3 ? '#3D6757' : '#F7F4ED'}
            stroke="#3D6757"
            strokeWidth="2"
          />
          {i === 0 ? (
            <Rabbit x={x! - 10} y={y! - 10} width={20} height={20} stroke="#3D6757" />
          ) : i === 3 ? (
            <Flag x={x! - 8} y={y! - 8} width={16} height={16} stroke="white" />
          ) : (
            <Carrot className="carrot-icon" x={x! - 10} y={y! - 10} width={20} height={20} />
          )}
        </g>
      ))}
    </svg>
  );
}
