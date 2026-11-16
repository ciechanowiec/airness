// The demo content a fresh site starts with: a small journal about the movies, in three categories and seven
// posts, each written in Markdown, which the seed converts to the rich text the editor stores. The posts speak of
// cinema in general; the only films they name are silent comedies of the 1920s.

/** A category the seed creates. */
export interface SeedCategory {
  readonly slug: string
  readonly title: string
  readonly description: string
}

/** A post the seed creates, with its cover from `scripts/seed/images`. */
export interface SeedPost {
  readonly slug: string
  readonly title: string
  readonly excerpt: string
  readonly category: string
  readonly publishedAt: string
  readonly featured: boolean
  readonly image: string
  readonly imageAlt: string
  readonly markdown: string
}

export const SEED_CATEGORIES: readonly SeedCategory[] = [
  {
    slug: 'essays',
    title: 'Essays',
    description: 'Longer pieces on why films work on us the way they do.',
  },
  {
    slug: 'lists',
    title: 'Lists',
    description: 'Short, practical lists for better evenings at the movies, at the cinema or at home.',
  },
  {
    slug: 'history',
    title: 'History',
    description: 'Where the movies came from, and the places and habits that grew up around them.',
  },
]

const CINEMA: SeedPost = {
  slug: 'why-the-cinema-still-feels-like-magic',
  title: 'Why the cinema still feels like magic',
  excerpt:
    'A dark room, a beam of light and a crowd of strangers holding their breath together. Nothing at home is quite the same.',
  category: 'essays',
  publishedAt: '2026-03-10T09:00:00.000Z',
  featured: true,
  image: 'cinema',
  imageAlt: 'An empty cinema with rows of red seats and a beam of projector light crossing the dark room',
  markdown: `Every screen in the house can show a film now, and yet people still buy a ticket, switch off their phones and sit in the dark with a few hundred strangers. It is worth asking why.

## The room does half the work

A cinema is built to remove everything except the film. The lights go down, the walls disappear and the screen becomes the only bright thing in the world. At home the kettle, the window and the phone all compete for attention. In the cinema they simply are not there.

## Watching with strangers

Laughter spreads through a full room. So does silence: the moment when a whole audience stops rustling at once is one of the small wonders of going out to the movies. You notice what the people around you notice, and a comedy is twice as funny for it.

## A promise you keep

Buying a ticket is a small commitment. You cannot pause, so you stay with the story even when it asks for patience, and patient films often give the most back.

> The cinema is the one place where we agree, for two hours, to look at the same thing together.

None of this means home viewing is lesser. It means the trip is still worth making, now and then, for the films that deserve the dark.`,
}

const OPENING: SeedPost = {
  slug: 'the-art-of-the-opening-scene',
  title: 'The art of the opening scene',
  excerpt:
    'The first few minutes of a film teach you how to watch the rest of it. The best openings do it without you noticing.',
  category: 'essays',
  publishedAt: '2026-03-08T09:00:00.000Z',
  featured: false,
  image: 'opening',
  imageAlt: "A film clapperboard beside a folding director's chair with a megaphone on its seat",
  markdown: `An opening scene has more jobs than any other. It sets the tone, introduces the world and makes a quiet promise about the kind of story to come, usually before a single name has appeared on screen.

## Three things a good opening does

- **It sets the rhythm.** A slow, wide shot asks you to settle in. A burst of fast cuts tells you to keep up.
- **It shows a world.** Before we meet anyone, we learn where we are and what is normal there, so we notice when it changes.
- **It asks a question.** Something small is out of place, and wanting to know why carries us into the second scene.

## Watching for it

Next time you sit down to a film, pay close attention to the first five minutes, then ask yourself at the end whether they told you what was coming. Surprisingly often, the whole film is hidden in plain sight right at the start.`,
}

const SCORE: SeedPost = {
  slug: 'how-a-film-score-tells-you-what-to-feel',
  title: 'How a film score tells you what to feel',
  excerpt:
    'Music arrives in a film before we have decided how to feel, and it quietly decides for us. Here is how it does it.',
  category: 'essays',
  publishedAt: '2026-03-06T09:00:00.000Z',
  featured: false,
  image: 'score',
  imageAlt: "A conductor's baton and sheet music flowing together with a curling strip of film",
  markdown: `Try watching a tense scene with the sound off. Most of the tension leaves the room, and what is left looks like people standing in a corridor. The score was doing more work than we noticed.

## Telling us where to look

A single held note can make an ordinary door look dangerous. A warm swell of strings tells us that two people who have not spoken yet will matter to each other. Music points, and we follow without being asked.

## Themes that come back

Many scores give a character or a place a short melody of its own. When it returns, changed into a minor key or slowed right down, we feel the change in the story before anyone says a word.

## Knowing when to stop

Some of the most powerful moments in film are silent. After a long stretch of music, a sudden quiet feels enormous, and a good composer knows exactly when to leave it.`,
}

const FILM_NIGHT: SeedPost = {
  slug: 'hosting-a-film-night-at-home',
  title: 'Hosting a film night at home',
  excerpt:
    'A few simple choices turn an ordinary evening on the sofa into a proper night at the movies for friends and family.',
  category: 'lists',
  publishedAt: '2026-03-04T09:00:00.000Z',
  featured: false,
  image: 'film-night',
  imageAlt: 'A cosy living room with a projector beaming onto the wall and a bowl of popcorn on the sofa',
  markdown: `You do not need a projector to host a good film night, though it helps. What you need is a plan, and a few small rituals borrowed from the cinema.

## Before the guests arrive

1. **Pick the film in advance.** Twenty minutes of scrolling is the fastest way to lose an evening. Choose two options and let the guests vote.
2. **Make it dark.** Close the curtains and switch off every light you can. A dim lamp behind the sofa is enough to find the popcorn.
3. **Set the sound.** Turn it up a little louder than usual. Films are mixed for big rooms.

## During the film

- Put the phones in a bowl by the door.
- Start on time, and keep the trailers if you like them.
- Pause once, at most, for a refill.

## Afterwards

Leave the credits running for a minute and let people talk. The conversation after the film is half the reason to watch it together.`,
}

const SILENT: SeedPost = {
  slug: 'five-silent-comedies-to-start-with',
  title: 'Five silent comedies to start with',
  excerpt:
    'A hundred years on, the great silent comedies are still some of the funniest films ever made, and they are easy to find.',
  category: 'history',
  publishedAt: '2026-03-02T09:00:00.000Z',
  featured: false,
  image: 'silent',
  imageAlt: 'A hand-cranked vintage film camera on a wooden tripod beside two film reels',
  markdown: `Silent films can feel like homework, but the comedies of the 1920s were made to make crowds laugh, and they still do. These five are a gentle way in.

## The list

- **The Kid** (1921). Charlie Chaplin's first feature mixes slapstick with real tenderness, and it is only an hour long.
- **Safety Last!** (1923). Harold Lloyd climbs the outside of a department store, and audiences have been gripping their seats ever since.
- **Sherlock Jr.** (1924). Buster Keaton plays a projectionist who walks into the film he is showing, a trick that still looks impossible.
- **The Gold Rush** (1925). Chaplin in the Klondike, with a dance of bread rolls that everyone remembers.
- **The General** (1926). Keaton chases a stolen locomotive across the country in one of the great action films of any decade.

## How to watch them

Watch them with music, as their first audiences did, and with company if you can. Silent comedy was made for a full room, and it is at its best when you can hear somebody else laugh.`,
}

const DRIVE_IN: SeedPost = {
  slug: 'drive-in-theatres-a-short-history',
  title: 'Drive-in theatres: a short history',
  excerpt:
    'Cars, a giant outdoor screen and a summer night. How the drive-in rose, nearly vanished and found new audiences.',
  category: 'history',
  publishedAt: '2026-02-27T09:00:00.000Z',
  featured: false,
  image: 'drive-in',
  imageAlt: 'Vintage cars facing a huge glowing outdoor cinema screen at dusk',
  markdown: `The first drive-in theatres opened in the early 1930s, when someone realized that a car was a comfortable, private seat that people brought with them for free.

## The golden years

After the war, families bought cars and moved to the suburbs, and drive-ins followed them. By the late 1950s there were thousands, many with playgrounds under the screen and snack bars the size of small restaurants. Children came in their pyjamas and fell asleep in the back seat before the second feature.

## The long decline

Land near growing towns became valuable, colour television arrived, and summer time made the evenings start later and later. One by one, the big screens came down.

## A second life

A few drive-ins never closed, and in recent years new ones have opened, often for a single season or a festival. The appeal is the same as it always was: a film under the open sky, with the people you came with and nobody else.`,
}

const BEST_SEAT: SeedPost = {
  slug: 'finding-the-best-seat-in-the-house',
  title: 'Finding the best seat in the house',
  excerpt:
    'Where to sit, when to arrive and what to bring: a short list of small habits that make a trip to the cinema better.',
  category: 'lists',
  publishedAt: '2026-02-24T09:00:00.000Z',
  featured: false,
  image: 'best-seat',
  imageAlt: 'A navy cinema seat beside a striped box of popcorn and two tickets',
  markdown: `A good film can be spoiled by a bad seat, and a fine one improved by a good evening around it. A few habits help.

## Where to sit

- **Two thirds of the way back.** The screen fills your view without making you turn your head, and the sound is usually mixed for the middle of the room.
- **In the centre of the row.** The picture is straighter and nobody squeezes past during the best scene.
- **Not under the balcony.** It can muffle the sound and cut off the top of the screen.

## When to arrive

Early enough to choose a seat, late enough to skip most of the advertisements. Ten minutes after the listed time is often right.

## What to bring

A jumper, because cinemas are cold. Something quiet to eat. And, if you are seeing a film you already love, a friend who has never seen it.`,
}

/** Every post the seed creates, the newest first. */
export const SEED_POSTS: readonly SeedPost[] = [CINEMA, OPENING, SCORE, FILM_NIGHT, SILENT, DRIVE_IN, BEST_SEAT]
