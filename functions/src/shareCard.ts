import satori from 'satori';
import { Resvg } from '@resvg/resvg-js';
import * as fs from 'fs';
import * as path from 'path';

const CARD_W = 1200;
const CARD_H = 630;

// Colors mirroring the app palette
const BG = '#0e0e10';
const SURFACE = '#1a1a1f';
const GOLD = '#f5c542';
const SILVER = '#c0c0c8';
const BRONZE = '#cd7f32';
const TEXT = '#fafafa';
const MUTED = '#b8b8c0';

let _fontRegular: Buffer | null = null;
let _fontBold: Buffer | null = null;
let _launcherIconDataUri: string | null = null;

function fonts(): { regular: Buffer; bold: Buffer } {
  if (!_fontRegular) {
    const dir = path.join(__dirname, '..', 'assets');
    _fontRegular = fs.readFileSync(path.join(dir, 'Roboto-Regular.ttf'));
    _fontBold = fs.readFileSync(path.join(dir, 'Roboto-Bold.ttf'));
  }
  return { regular: _fontRegular!, bold: _fontBold! };
}

function launcherIconDataUri(): string {
  if (!_launcherIconDataUri) {
    const buf = fs.readFileSync(path.join(__dirname, '..', 'assets', 'launcher-icon.png'));
    _launcherIconDataUri = `data:image/png;base64,${buf.toString('base64')}`;
  }
  return _launcherIconDataUri;
}

export interface PodiumEntry {
  rank: number;
  displayName: string;
  avatarSeed: string;
  points: number;
}

export interface ShareCardData {
  title: string;
  playerCount: number;
  podium: PodiumEntry[]; // sorted rank asc, max 3
}

/** Deterministic hue from a seed string */
function seedHue(seed: string): number {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) & 0xffff;
  return h % 360;
}

function avatarColor(seed: string): string {
  const hue = seedHue(seed);
  return `hsl(${hue},50%,45%)`;
}

function initial(name: string): string {
  return (name.trim()[0] ?? '?').toUpperCase();
}

function plinthColor(rank: number): string {
  return rank === 1 ? GOLD : rank === 2 ? SILVER : BRONZE;
}

/** Drawn rank badge — colored circle with bold rank number. No emoji = no tofu. */
function rankBadge(rank: number, size: number) {
  const color = plinthColor(rank);
  const fontSize = Math.floor(size * 0.48);
  return {
    type: 'div',
    props: {
      style: {
        width: size,
        height: size,
        borderRadius: '50%',
        background: color,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize,
        fontWeight: 700,
        color: rank === 2 ? '#111' : '#fff',
        flexShrink: 0,
      },
      children: String(rank),
    },
  };
}

/** Height of plinth in px (1st tallest, 3rd shortest) */
function plinthH(rank: number): number {
  return rank === 1 ? 160 : rank === 2 ? 115 : 88;
}

/** Build satori vnode tree for the card */
function buildVNode(data: ShareCardData) {
  const { title, playerCount, podium } = data;

  // Order: 2nd (left), 1st (center), 3rd (right)
  const ordered = [
    podium.find((e) => e.rank === 2) ?? null,
    podium.find((e) => e.rank === 1) ?? null,
    podium.find((e) => e.rank === 3) ?? null,
  ];

  const truncTitle = title.length > 58 ? title.slice(0, 56) + '…' : title;
  const playerLabel = `${playerCount} player${playerCount === 1 ? '' : 's'}`;

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        flexDirection: 'column',
        width: CARD_W,
        height: CARD_H,
        background: BG,
        fontFamily: 'Roboto',
        color: TEXT,
        padding: '48px 64px 40px',
        boxSizing: 'border-box',
      },
      children: [
        // Header: title + launcher icon
        {
          type: 'div',
          props: {
            style: { display: 'flex', flexDirection: 'column', gap: '8px' },
            children: [
              {
                type: 'div',
                props: {
                  style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
                  children: [
                    {
                      type: 'div',
                      props: {
                        style: { fontSize: 28, fontWeight: 700, color: TEXT, maxWidth: 860 },
                        children: truncTitle,
                      },
                    },
                    {
                      type: 'img',
                      props: {
                        src: launcherIconDataUri(),
                        width: 56,
                        height: 56,
                        style: { borderRadius: 12, flexShrink: 0 },
                      },
                    },
                  ],
                },
              },
              {
                type: 'div',
                props: {
                  style: { display: 'flex', alignItems: 'center', gap: '8px' },
                  children: [
                    rankBadge(1, 22),
                    {
                      type: 'div',
                      props: {
                        style: { fontSize: 18, color: MUTED },
                        children: `Results — ${playerLabel}`,
                      },
                    },
                  ],
                },
              },
            ],
          },
        },

        // Podium
        {
          type: 'div',
          props: {
            style: {
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'flex-end',
              justifyContent: 'center',
              gap: '24px',
              flex: 1,
              paddingTop: '32px',
            },
            children: ordered.map((entry) => {
              if (!entry) return { type: 'div', props: { style: { width: 260 }, children: '' } };
              return buildPodiumSlot(entry);
            }),
          },
        },
      ],
    },
  };
}

function buildPodiumSlot(entry: PodiumEntry) {
  const ph = plinthH(entry.rank);
  const color = plinthColor(entry.rank);
  const avatarBg = avatarColor(entry.avatarSeed || entry.displayName);
  const avatarSize = entry.rank === 1 ? 72 : 56;
  const nameStr = entry.displayName.length > 14
    ? entry.displayName.slice(0, 13) + '…'
    : entry.displayName;
  const isFirst = entry.rank === 1;

  return {
    type: 'div',
    props: {
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        width: 260,
      },
      children: [
        // Avatar info above plinth
        {
          type: 'div',
          props: {
            style: {
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '6px',
              marginBottom: '12px',
            },
            children: [
              // Gold ring / spacer for non-1st to keep alignment
              isFirst
                ? { type: 'div', props: { style: { height: 28 }, children: '' } }
                : { type: 'div', props: { style: { height: 28 }, children: '' } },
              // Avatar circle
              {
                type: 'div',
                props: {
                  style: {
                    width: avatarSize,
                    height: avatarSize,
                    borderRadius: '50%',
                    background: avatarBg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: Math.floor(avatarSize * 0.44),
                    fontWeight: 700,
                    color: '#fff',
                    border: isFirst ? `3px solid ${GOLD}` : 'none',
                  },
                  children: initial(entry.displayName),
                },
              },
              // Name
              {
                type: 'div',
                props: {
                  style: {
                    fontSize: isFirst ? 20 : 17,
                    fontWeight: 700,
                    color: TEXT,
                    textAlign: 'center',
                  },
                  children: nameStr,
                },
              },
              // Points
              {
                type: 'div',
                props: {
                  style: { fontSize: isFirst ? 18 : 15, color: MUTED },
                  children: `${entry.points} pts`,
                },
              },
            ],
          },
        },
        // Plinth with drawn rank badge
        {
          type: 'div',
          props: {
            style: {
              width: '100%',
              height: ph,
              background: SURFACE,
              borderTop: `3px solid ${color}`,
              borderRadius: '4px 4px 0 0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            },
            children: rankBadge(entry.rank, 44),
          },
        },
      ],
    },
  };
}

export async function renderChallengeCard(data: ShareCardData): Promise<Buffer> {
  const { regular, bold } = fonts();

  const svg = await satori(buildVNode(data) as Parameters<typeof satori>[0], {
    width: CARD_W,
    height: CARD_H,
    fonts: [
      { name: 'Roboto', data: regular, weight: 400, style: 'normal' },
      { name: 'Roboto', data: bold, weight: 700, style: 'normal' },
    ],
  });

  const resvg = new Resvg(svg, { fitTo: { mode: 'width', value: CARD_W } });
  return Buffer.from(resvg.render().asPng());
}
