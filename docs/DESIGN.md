---
name: High-Performance Nature
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#bccac0'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#87948b'
  outline-variant: '#3d4a42'
  surface-tint: '#68dba9'
  primary: '#68dba9'
  on-primary: '#003825'
  primary-container: '#25a475'
  on-primary-container: '#00311f'
  inverse-primary: '#006c4a'
  secondary: '#45dfa4'
  on-secondary: '#003825'
  secondary-container: '#00bd85'
  on-secondary-container: '#00452e'
  tertiary: '#4edea3'
  on-tertiary: '#003824'
  tertiary-container: '#00a572'
  on-tertiary-container: '#00311f'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#85f8c4'
  primary-fixed-dim: '#68dba9'
  on-primary-fixed: '#002114'
  on-primary-fixed-variant: '#005137'
  secondary-fixed: '#68fcbf'
  secondary-fixed-dim: '#45dfa4'
  on-secondary-fixed: '#002114'
  on-secondary-fixed-variant: '#005137'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#2d3449'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Sora
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  headline-md:
    fontFamily: Sora
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  title-lg:
    fontFamily: Sora
    fontSize: 20px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Sora
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Sora
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Sora
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Sora
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.05em
  label-md:
    fontFamily: Sora
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 40px
  xl: 64px
  gutter: 24px
  margin-mobile: 16px
  margin-desktop: 48px
---

## Brand & Style
The design system is built upon a philosophy of "Vibrant Athleticism," merging the disciplined structure of high-performance sports technology with the organic vitality of the natural world. It is designed for users who seek peak efficiency, clarity, and a sense of fresh air in their digital experiences. 

The aesthetic style is **Corporate Modern with subtle Glassmorphic accents**. This combination ensures a professional, reliable foundation while introducing layers of light and transparency that evoke an "aerated" and modern feel. Visuals should feel precise, energetic, and clean, emphasizing movement through generous whitespace and purposeful transitions.

## Colors
This design system utilizes a palette centered around an athletic **Forest Green**, remapped to provide high visibility against deep, cool neutrals. 

- **Primary (#059669):** A high-chroma Forest Green used for primary actions, branding, and active states.
- **Secondary (#34D399):** A lighter Mint Green used for accents, highlights, and status indicators that require visibility without the weight of the primary color.
- **Tertiary (#10B981):** An Emerald shade used for semantic "success" states and supplementary data visualization.
- **Neutral (#0F172A):** A deep Slate used for backgrounds to create a "Midnight Forest" canvas, allowing the green accents to vibrate with energy.

Surface colors follow a tonal nesting logic: the base background is the darkest neutral, while cards and overlays use slightly lighter variations of Slate to create depth.

## Typography
**Sora** is the exclusive typeface for the design system. Its geometric structure and wide aperture provide excellent legibility and a high-tech, athletic appearance.

- **Headlines:** Use Bold (700) or Semi-Bold (600) weights with slightly tightened letter-spacing to create an impactful, editorial feel.
- **Body Text:** Standardized on Regular (400) weight for maximum readability. Use Body-MD for general interface text and Body-LG for long-form content.
- **Labels:** Always set in Semi-Bold (600) or Bold (700) with increased letter-spacing and uppercase styling where appropriate to denote hierarchy in small UI elements.

## Layout & Spacing
The layout follows a **fluid 12-column grid** on desktop and a **4-column grid** on mobile. The spacing rhythm is based on a factor of 8px, ensuring mathematical harmony across all components.

- **Desktop:** 12 columns, 24px gutters, 48px side margins. 
- **Tablet:** 8 columns, 20px gutters, 32px side margins.
- **Mobile:** 4 columns, 16px gutters, 16px side margins.

Content should lean toward a "center-focused" alignment for readability, with high-performance dashboards utilizing the full width of the grid. Components should use the `md` (24px) spacing unit for internal padding to maintain a sense of openness and breathability.

## Elevation & Depth
Depth in the design system is communicated through **Tonal Layering** and **Ambient Shadows** with a subtle forest-green tint.

1.  **Base:** The primary background layer (Neutral #0F172A).
2.  **Surface:** Elevated containers (Cards, Modals) use a slightly lighter slate (#1E293B) to stand out.
3.  **Glass:** Floating elements (Navigation bars, Dropdowns) utilize a backdrop-blur effect (20px) with a 10% opacity white overlay to create a frosted glass look.
4.  **Shadows:** Use extra-diffused shadows for high-elevation components. Shadows should have a hex value of #000000 at 30% opacity, but with a slight green-tinted glow (#059669 at 5% opacity) on primary action buttons to make them appear "energized."

## Shapes
The design system employs a **Rounded** shape language (Level 2). This softens the technical precision of the Sora typeface and the dark UI, making the interface feel more approachable and organic.

- **Standard Elements (Buttons, Inputs):** 0.5rem (8px) corner radius.
- **Large Containers (Cards, Sections):** 1rem (16px) corner radius.
- **Feature Elements (Modals, Hero Cards):** 1.5rem (24px) corner radius.

Iconography should follow this rounded logic, avoiding sharp points in favor of slightly curved terminals.

## Components
- **Buttons:** Primary buttons use the Forest Green (#059669) background with white Sora Bold text. Secondary buttons are "Ghost" style with a 1px border of #34D399 and a subtle green hover tint.
- **Chips:** Small, rounded containers with a #1E293B background and #34D399 text, used for tags or filtering.
- **Input Fields:** Dark backgrounds (#1E293B) with a subtle 1px border (#334155). On focus, the border transitions to Forest Green with a soft outer glow.
- **Cards:** Utilize the "Surface" color with a 1px low-contrast outline. Interactive cards should slightly lift (elevate) and increase shadow density on hover.
- **Lists:** Clean, horizontal dividers with 8px of padding between items. Use the Primary Green for icons within lists to guide the eye.
- **Checkboxes/Radios:** When active, these are filled with Forest Green and use a white checkmark/dot for maximum contrast and high-performance visibility.