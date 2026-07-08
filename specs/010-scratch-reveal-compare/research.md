# Research: Scratch-to-Reveal Compare Mode

## Decision 1: A persistent alpha-mask `ImageBitmap`, erased via `BlendMode.DstOut` radial-gradient stamps

**Decision**: A single `ImageBitmap` (sized to the overlay's display bounds, not the source
photo's full resolution — display bounds are what's actually drawn on screen, typically well under
1500×1500px even on large phones, so no downsampling concern), created fully opaque. Each drag
stamps soft circles into it: a `Paint` with `blendMode = BlendMode.DstOut` and a
`RadialGradientShader` (opaque center → fully transparent edge), drawn onto the mask's own
`Canvas` at each interpolated point along the stroke. `DstOut` subtracts the *source's* alpha from
the *destination* — so a soft-edged stamp only ever reduces the mask's opacity, never increases it.

**Rationale**:
- `DstOut` with a radial-gradient source makes "erase, soft-edged, never re-cover" (FR-004, FR-006)
  fall out of the blend math itself — no separate "already revealed" bookkeeping needed, unlike
  feature 011's grid where eligibility had to be tracked explicitly. Once a pixel's mask alpha is
  driven toward 0, nothing in this design can push it back up; re-touching an already-erased area
  is a genuine no-op (FR edge case: "already-revealed area... remains revealed").
- Frequent stamping (Decision 2) with a radial-gradient falloff already produces a continuous,
  soft-edged trail without needing a *second*, separate blur pass over the whole mask — considered
  and rejected as unnecessary complexity for this baseline (see Alternatives).
- A persistent, incrementally-drawn-into bitmap (not replaying the full stroke history every
  frame) is what keeps this responsive — matches `plan.md`'s Performance Goals and this codebase's
  general instinct to keep expensive work off the hot path.

**Alternatives considered**:
- *Blur the entire accumulated mask layer as a second pass* (discussed during the original
  brainstorm, before this spec existed): rejected for this baseline — frequent radial-gradient
  stamping already reads as soft/continuous; an extra full-layer blur pass adds real cost (a
  render-effect over a potentially large layer, on every frame during a drag) for a marginal
  visual difference that hasn't been shown necessary. Can be revisited after live testing if the
  stamped-only edge doesn't look smooth enough in practice.
- *Track "revealed regions" as a data structure (e.g., a set of erased rectangles/paths) instead of
  a bitmap*: rejected — far more complex to get soft edges and overlap-accumulation right than
  letting the GPU-composited alpha channel do it, and this codebase has no precedent for that kind
  of geometry bookkeeping.

## Decision 2: Interpolate stamp positions along each drag segment — this is the one piece with a real unit test

**Decision**: `detectDragGestures`'s `onDrag` callback fires with discrete position deltas; fast
finger movement can produce large jumps between callbacks, which would leave gaps in the stamped
trail if each callback only stamped once at its endpoint. Instead, a small pure function computes
evenly-spaced points between the previous and current touch position (spacing smaller than the
brush radius, e.g. `radius / 2`), and a stamp is drawn at each interpolated point, not just the
segment's endpoint.

**Rationale**: This is a standard freehand-drawing technique, and — unlike everything else in this
feature — it's a pure function (two points + a spacing value in, a list of points out) with no
Canvas/Compose dependency, so it's the one piece of this feature that's genuinely, meaningfully
unit-testable per Constitution Principle III. Extracting it as its own function (rather than
inlining the math into the drag-gesture callback) is what makes that testability possible.

**Alternatives considered**:
- *Stamp only at each `onDrag` callback's endpoint, no interpolation*: rejected — produces visible
  gaps ("dotted line" strokes) on fast drags, which reads as broken rather than tactile — directly
  undermines the "soft, hand-drawn" feel FR-003/FR-004 are about.

## Decision 3: Hoist mode, mask, and brush-size state to `CompareOverlay` — not inside either mode's own composable

**Decision**: `CompareOverlay` gains `var mode by remember { mutableStateOf(CompareMode.Slider) }`
(FR-010: slider stays the default), plus the scratch mode's mask `ImageBitmap` and brush-size
`Float`, all `remember`-ed at the `CompareOverlay` level — exactly the same level the slider's own
`progress: Float` is already hoisted to (`CompareOverlay.kt:46`) — and passed down into whichever
mode composable is currently rendered.

**Rationale**: If the mask/brush-size state instead lived inside the scratch composable's own
`remember` block, switching to the slider and back would fully dispose and recreate that
composable (a conditional `if (mode == Scratch) ScratchRevealCompare(...) else
BeforeAfterSlider(...)` structurally removes whichever branch isn't current from composition),
losing all scratch progress — directly violating FR-009 ("switching between modes... MUST NOT
reset... scratch progress"). Hoisting to the parent that never itself leaves composition while
Compare is open is the standard Compose state-hoisting fix for exactly this class of bug, and it's
already the pattern this same file uses for the slider's `progress`.
- This is also what makes FR-007 (full reset on close) require zero explicit reset code: closing
  Compare disposes `CompareOverlay` itself, which disposes everything hoisted to it, mask included.

**Alternatives considered**:
- *`rememberSaveable` for the mask state (to survive process death/rotation)*: rejected — spec
  Assumptions explicitly scope this to session-local, ephemeral state ("brush size does not need
  to be remembered across app restarts"); `rememberSaveable` would also require the mask
  `ImageBitmap` itself to be serializable, which it isn't, without real extra complexity for a
  requirement that was explicitly never asked for.

## Decision 4: The instructional hint reuses the slider's existing pill pattern exactly

**Decision**: FR-011's hint uses the same `Surface`/`Text` treatment already at
`CompareOverlay.kt:146-159` for the slider's "Before / After" pill (same position, same
background/shape/typography), just with different text (a new string resource) and shown only
while scratch mode is active.

**Rationale**: Direct visual consistency between the two modes' chrome, and zero new UI pattern to
design — reuses an existing, already-styled composable structure verbatim. Matches the
Clarifications session's resolution: persistent while the mode is active, not a "show once and
fade" treatment (which would need extra "have I shown this before" state this feature doesn't
otherwise need).

**Alternatives considered**: See spec.md Clarifications — the "show once then fade" alternative
was already rejected there in favor of persistent, for the same no-extra-state reasoning.
