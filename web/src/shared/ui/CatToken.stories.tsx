import { type Meta, type StoryObj } from '@storybook/react-vite';
import { CatToken } from './CatToken';
const meta = {
  title: 'Play/Cat token',
  component: CatToken,
  args: { id: 'ginger', mark: 1 },
  argTypes: { id: { control: 'select', options: ['ginger', 'black', 'white', 'gray'] } },
} satisfies Meta<typeof CatToken>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Ginger: Story = {};
export const Black: Story = { args: { id: 'black', mark: 2 } };
export const White: Story = { args: { id: 'white', mark: 3 } };
export const Gray: Story = { args: { id: 'gray', mark: 4 } };
export const Small: Story = { args: { small: true } };
