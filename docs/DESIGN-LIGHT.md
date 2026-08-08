---
name: Kinetic Trail
colors:
  surface: '#f9f9ff'
  surface-dim: '#d3daea'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e7eefe'
  surface-container-high: '#e2e8f8'
  surface-container-highest: '#dce2f3'
  on-surface: '#151c27'
  on-surface-variant: '#3d4a42'
  inverse-surface: '#2a313d'
  inverse-on-surface: '#ebf1ff'
  outline: '#6d7a72'
  outline-variant: '#bccac0'
  surface-tint: '#006c4a'
  primary: '#006948'
  on-primary: '#ffffff'
  primary-container: '#00855d'
  on-primary-container: '#f5fff7'
  inverse-primary: '#68dba9'
  secondary: '#575e70'
  on-secondary: '#ffffff'
  secondary-container: '#d9dff5'
  on-secondary-container: '#5c6274'
  tertiary: '#825100'
  on-tertiary: '#ffffff'
  tertiary-container: '#a36700'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#85f8c4'
  primary-fixed-dim: '#68dba9'
  on-primary-fixed: '#002114'
  on-primary-fixed-variant: '#005137'
  secondary-fixed: '#dce2f7'
  secondary-fixed-dim: '#c0c6db'
  on-secondary-fixed: '#141b2b'
  on-secondary-fixed-variant: '#404758'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f9f9ff'
  on-background: '#151c27'
  surface-variant: '#dce2f3'
typography:
  display-lg:
    fontFamily: lexend
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: lexend
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: lexend
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-caps:
    fontFamily: jetbrainsMono
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  metric-xl:
    fontFamily: lexend
    fontSize: 64px
    fontWeight: '800'
    lineHeight: 64px
    letterSpacing: -0.04em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
---

## Brand & Style

The brand personality is high-performance, resilient, and outdoorsy. It targets serious trail runners and orienteers who require immediate clarity while in motion. The UI must evoke a sense of momentum and precision.

The design style is **Corporate / Modern** with a lean towards **High-Contrast / Bold**. It utilizes crisp lines and generous negative space to ensure that data remains the hero. The aesthetic is "technical gear"—functional, lightweight, and engineered for endurance. Elements are structured to feel like a high-end GPS watch interface translated to a mobile and web experience.

## Colors

The palette is anchored by "High-Performance Green," a vibrant shade that signals action and growth, reminiscent of lush trails and digital topographic markers. 

- **Primary (#059669):** Used for primary actions, progress indicators, and active states. It must maintain a high contrast against white backgrounds.
- **Secondary (#111827):** A deep obsidian used for high-level headings and backgrounds where maximum focus is required.
- **Tertiary (#F59E0B):** An "Amber Alert" color used sparingly for cautionary data, weather warnings, or elevation peaks.
- **Neutral (#6B7280):** A versatile slate used for secondary text and structural borders.

The background is kept to a pure white (#FFFFFF) or a very light cool gray (#F9FAFB) to ensure the green remains the most energetic element on the screen.

## Typography

Typography is optimized for glanceability. **Lexend** is chosen for its hyper-legibility and athletic, geometric feel, making it perfect for headers and large metrics. **Inter** provides a systematic and neutral base for long-form content or settings. **JetBrains Mono** is introduced for technical data points (lat/long, pace, splits) to give a precise, technical feel.

For "Metric" styles, use heavy weights (700-800) and tighter letter spacing to create a compact, high-impact look for mid-run data visualization.

## Layout & Spacing

The layout follows a **Fluid Grid** model with a 4px baseline rhythm. 

- **Mobile:** 4-column grid with 16px margins.
- **Tablet:** 8-column grid with 24px margins.
- **Desktop:** 12-column grid with a max-width of 1280px and 32px margins.

Vertical rhythm should be aggressive. Use `stack-lg` for separating distinct sections (e.g., Map vs. Stats) and `stack-sm` for grouping related metrics. Touch targets must be a minimum of 48x48px to accommodate shaky hands during physical activity.

## Elevation & Depth

This design system uses **Tonal Layers** and **Low-Contrast Outlines** rather than heavy shadows to maintain a "flat and fast" feel.

- **Level 0 (Base):** White background.
- **Level 1 (Cards/Containers):** Background #F9FAFB with a 1px border of #E5E7EB.
- **Level 2 (Active/Floating):** Use a subtle, highly diffused shadow (0px 4px 20px rgba(0,0,0,0.05)) only for elements that require temporary focus, like bottom sheets or floating action buttons.

Depth is primarily communicated through color shifts (e.g., a slightly darker gray for a pressed state) rather than physical projection.

## Shapes

The shape language is **Soft (0.25rem)**. This slight rounding provides a modern touch without sacrificing the professional, rigorous feel of a performance tool. 

- **Buttons & Inputs:** 0.25rem (4px) corner radius.
- **Data Cards:** 0.5rem (8px) corner radius.
- **Status Tags/Chips:** Fully rounded (pill) to distinguish them from interactive buttons.

Large images or map containers should utilize the `rounded-lg` (8px) setting to frame the organic nature of the outdoors within the structured UI.

## Components

- **Buttons:** Primary buttons use the High-Performance Green background with white text. Use bold, uppercase labels in Lexend. The "Pressed" state should darken to #047857.
- **Inputs:** Use a 1px border (#D1D5DB). On focus, the border thickens to 2px and changes to the primary green.
- **Metrics Cards:** Large, bold Lexend numbers. Use JetBrains Mono for the unit label (e.g., "BPM" or "KM/H") placed either above or to the right of the value.
- **Chips:** Used for "Trail Type" or "Difficulty." Backgrounds should be low-opacity versions of the primary/secondary colors with high-contrast text.
- **Progress Bars:** Use a thick 8px track. The unfilled track should be #E5E7EB, and the filled portion should be the primary green.
- **Navigation:** Use a bottom bar on mobile with clear icons and JetBrains Mono labels for a technical, utility-first feel.