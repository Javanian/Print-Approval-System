# Design system

The frontend uses one global stylesheet, `frontend/src/styles.css`, with tokens on `:root`. Class
names are stable because the Playwright suite and the demo capture script select by role and text,
not by style.

## Direction

A print shop sells finished paper, so the interface stays quiet and lets the artwork lead. Cards
are white on a faint violet canvas, the preview sits on a dotted proof table, and one violet action
color marks the next step. Headings are set in Inter; one editorial line on the sign-in page uses
Playfair Display italic.

## Color

| Role | Token | Value | Notes |
| --- | --- | --- | --- |
| Text | `--ink` | `#1E1B2E` | 15.5:1 on the canvas |
| Secondary text | `--muted` | `#5B5670` | 7.0:1 on white |
| Canvas | `--canvas` | `#F7F5FC` | |
| Card | `--paper` | `#FFFFFF` | |
| Border | `--line` | `#E7E3F1` | |
| Action | `--violet` | `#6D28D9` | white text 7.1:1 |
| Action hover | `--violet-hover` | `#5B21B6` | |
| Highlight, focus ring | `--amber` | `#F59E0B` | never used for text |
| Approved | `--green` on `--green-tint` | `#047857` / `#E9F8F1` | |
| Pending | `--amber-ink` on `--amber-tint` | `#8A4B05` / `#FFF6E5` | |
| Revision | `--red` on `--red-tint` | `#B42318` / `#FDECEB` | |

Status badges always show the state as text with a dot, so color is never the only signal.

## Type

| Use | Font |
| --- | --- |
| UI text and headings | Inter (variable) |
| Editorial accent on sign-in | Playfair Display 600 italic |
| Hashes and links | system monospace |

Both fonts ship with the build through `@fontsource`, because the content security policy allows
`'self'` only.

## Shape and depth

- Radius 16px on cards, 12px on buttons, 10px on inputs, full pill on badges.
- Shadow: a hairline plus a wide, faint ambient shadow; job cards lift 2px on hover.
- Focus: a 3px amber outline on every interactive element.

## Layout

- Job list is a responsive card grid: as many 300px columns as fit, one column on phones.
- Customer review: preview and decision side by side on desktop, stacked on phones.
- The print stylesheet hides navigation and forms and keeps the approval record on one page.

## Motion

Hover and press feedback take 150ms. `prefers-reduced-motion` turns transitions off.
