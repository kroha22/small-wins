import { Cat } from 'lucide-react';
import { type CatId } from '../../games/purrdoku/model';
const colours = { ginger: '#D97924', black: '#30343A', white: '#FFFFFF', gray: '#939AA3' };
export function CatToken({
  id,
  mark,
  small = false,
}: {
  id: CatId;
  mark: number;
  small?: boolean;
}) {
  return (
    <span className={`cat-token ${small ? 'cat-small' : ''}`} aria-hidden="true">
      <Cat fill={colours[id]} stroke={id === 'black' ? '#F7F4ED' : '#202526'} strokeWidth={1.7} />
      <span className="cat-mark">{mark}</span>
    </span>
  );
}
