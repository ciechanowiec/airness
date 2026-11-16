import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import type { PostArticle } from '@/lib/views'
import { ArticleBody } from './article-body'

type Content = PostArticle['content']
type Node = Content['root']['children'][number]

const text = (value: string, format: number = 0): Node => ({
  type: 'text',
  version: 1,
  text: value,
  format,
  detail: 0,
  mode: 'normal',
  style: '',
})

const BOLD: number = 1
const ITALIC: number = 2

const block = (type: string, children: readonly Node[], extra: Readonly<Record<string, unknown>> = {}): Node => ({
  type,
  version: 1,
  children: [...children],
  direction: 'ltr',
  format: '',
  indent: 0,
  ...extra,
})

const paragraph = (...children: readonly Node[]): Node => block('paragraph', children, { textFormat: 0 })

const CONTENT: Content = {
  root: {
    type: 'root',
    version: 1,
    direction: 'ltr',
    format: '',
    indent: 0,
    children: [
      paragraph(
        text('A cinema is built to remove everything except the film. The lights go down and the screen becomes '),
        text('the only bright thing', BOLD),
        text(' in the world.'),
      ),
      block('heading', [text('Watching with strangers')], { tag: 'h2' }),
      paragraph(
        text('Laughter spreads through a full room, and so does '),
        text('silence', ITALIC),
        text(', the moment a whole audience stops rustling at once.'),
      ),
      block(
        'list',
        [
          block('listitem', [text('Sit two thirds of the way back')], { value: 1 }),
          block('listitem', [text('Arrive ten minutes after the listed time')], { value: 2 }),
          block('listitem', [text('Bring a jumper, because cinemas are cold')], { value: 3 }),
        ],
        { listType: 'bullet', start: 1, tag: 'ul' },
      ),
      block('quote', [text('The cinema is the one place where we agree to look at the same thing together.')]),
      paragraph(
        text('Read more in '),
        block('link', [text('the list of every post')], {
          fields: { linkType: 'custom', url: '/posts', newTab: false },
          id: 'link-to-posts',
        }),
        text('.'),
      ),
    ],
  },
}

const meta: Meta<typeof ArticleBody> = {
  component: ArticleBody,
  args: { content: CONTENT },
}

export default meta

type Story = StoryObj<typeof ArticleBody>

export const HeadingsListsAndLinks: Story = {}
