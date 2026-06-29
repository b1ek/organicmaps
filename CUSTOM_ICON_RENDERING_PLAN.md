# Custom Bookmark Category Icon — Rendering Plan

## Current rendering pipeline (for reference)

```
Bookmark::GetSymbolNames()                        // returns map<zoom, "bookmark-hotel-m">
  → DrapeEngine::GenerateMarkRenderInfo()         // stores in UserMarkRenderParams::m_symbolNames
  → CacheUserMarks()                              // user_mark_shapes.cpp
      → GetSymbolNameForZoomLevel()               // picks symbol name for current zoom
      → textures->GetSymbolRegion(symbolName, ..) // looks up UV rect in pre-baked atlas
      → builds quad vertices with:
          - texCoords.xy = main icon UV
          - texCoords.zw = background UV (or same if no bg)
          - maskColor.rgb = custom color tint (only applies to bg layer)
      → state.SetColorTexture(symbolRegion.GetTexture())  // binds the atlas texture
      → state.SetTextureIndex(symbolRegion.GetTextureIndex())
      → batcher.InsertListOfStrip(...)
```

The fragment shader (`user_mark.fsh.glsl`):
```glsl
vec4 color = texture(u_colorTex, v_texCoords.xy);   // main icon — retains original colors
vec4 bgColor = texture(u_colorTex, v_texCoords.zw) * vec4(v_maskColor.xyz, 1.0); // bg tinted by mask
vec4 finalColor = mix(color, mix(bgColor, color, color.a), bgColor.a);
finalColor.a = clamp(color.a + bgColor.a, 0.0, 1.0) * u_opacity * v_maskColor.w;
```

Key insight: the **color mask only affects the background layer**, not the main icon. A custom full-color 32x32 PNG would render in its original colors, with the category color optionally tinting a background circle/shape.

---

## The barrier: no runtime texture upload path

The pre-baked symbol atlas is built at compile time. There is no API to upload user pixels to GPU at runtime and get back a `SymbolRegion`.

We need exactly one new capability in `TextureManager`:

```cpp
// Registers a user-provided RGBA bitmap as a named symbol.
// Returns a SymbolRegion with UV rect + texture pointer for the rendering pipeline.
// The region remains valid until the symbol is unregistered or TextureManager is released.
SymbolRegion RegisterRuntimeSymbol(
    std::string const & symbolName,
    uint32_t width, uint32_t height,
    uint8_t const * rgbaPixels   // 4 bytes per pixel, row-major
);
void UnregisterRuntimeSymbol(std::string const & symbolName);
```

That's it. Once this exists, the rest of the pipeline works unchanged — `CacheUserMarks` calls `GetSymbolRegion`, gets the region, draws the quad.

---

## Texture allocation strategy

Three options for where the runtime pixels live on GPU:

### Option A: Dedicated texture per icon (simplest)
- One GPU texture per custom icon
- 32×32 RGBA = 4KB VRAM each
- Max ~16 categories with custom icons = 64KB VRAM
- Drawback: state switches between different user textures (but batcher groups by texture anyway)

### Option B: Shared user-texture atlas (efficient)
- Pre-allocate one texture sheet, e.g., 256×256 RGBA (256KB)
- Pack icons into it with a simple row allocator
- Single texture binding for all user icons → zero extra state switches
- Max 64 icons at 32×32
- Needs simple 2D bin-packing (row-based: allocate next free row)

```
256×256 atlas:
┌────────────────────────┐
│ icon1  │ icon2  │ ...  │  ← row 0-31
│ 32×32  │ 32×32  │      │
├────────┼────────┼──────┤
│ icon5  │ free   │ ...  │  ← row 32-63
│ 32×32  │        │      │
├────────┴────────┴──────┤
│ ...                    │
└────────────────────────┘
```

**Recommendation: Option A** for initial implementation (simplest, lowest risk). Migrate to Option B later if >16 custom icons become common.

---

## Data flow end-to-end

### Write path (user sets custom icon on category)

```
Android UI                          C++ core                          Drape/GPU
─────────                          ────────                          ─────────
1. User picks image
2. Decode to 32×32 RGBA
   (BitmapFactory.decode, 
    scale to 32px, copy pixels)
3. Base64-encode pixels        → CategoryData::
   Set custom icon string         m_properties["CustomImageData"]
                                  = "base64..." + metadata
                                → SetCategoryBookmarksIcon()
4. Cascade to bookmarks:       → For each bookmark:
   Set each bookmark's             m_properties["CustomImage"] 
   properties                      = "user-icon-<catId>-<zoom>"
                                → SaveBookmarks() → .kmb file

5. Trigger texture upload      → DrapeEngine::
                                  On category visible →
                                  decode base64, 
                                  call RegisterRuntimeSymbol(
                                    "user-icon-<catId>-s",
                                    32, 32, pixels)
                                → TextureManager::
                                  Create GL texture,
                                  glTexImage2D(32,32,RGBA),
                                  store SymbolRegion
                                → InvalidateUserMarks()
                                  redraw tile
```

### Read path (bookmark renders)

```
CacheUserMarks()                    TextureManager                GPU
───────────────                     ──────────────                ───
1. GetSymbolNameForZoomLevel() 
   → "user-icon-<catId>-m"

2. textures->GetSymbolRegion(      Lookup in runtime
   "user-icon-<catId>-m", region)  symbol table
                                   → found: return region
                                      (UV rect + texture ptr)

3. Build quad with                   (same as regular bookmark)
   texCoords = region.GetTexRect()
   maskColor = category color

4. state.SetColorTexture(            texture bound for draw
   region.GetTexture())

5. batcher.InsertListOfStrip(...)   Draw call with quad
```

The only difference from standard bookmarks: step 2 looks up in the runtime symbol table instead of the pre-baked atlas. Everything else is identical.

---

## Files to modify

### New file: `libs/drape/texture_manager_runtime.hpp/cpp`
- `RegisterRuntimeSymbol()` / `UnregisterRuntimeSymbol()`
- Thread-safe map of `symbolName → RuntimeSymbolData { Texture*, UV rect, size }`
- GPU texture allocation via `HWTextureAllocator`

### Modify: `libs/drape/texture_manager.hpp`
- Add public API: `RegisterRuntimeSymbol()`, `UnregisterRuntimeSymbol()`
- Private: map of runtime symbols, optional shared atlas texture

### Modify: `libs/drape_frontend/user_mark_shapes.cpp`
- No changes needed! `GetSymbolRegion` already calls the TextureManager. If the runtime symbol is registered there, it will be found.

### Modify: `libs/map/bookmark.hpp/cpp`
- Add `Bookmark::SetCustomImagePixels()` / `GetCustomImagePixels()` — stores/retrieves raw RGBA from properties
- `Bookmark::GetCustomSymbolNames()` already handles custom symbol names

### Modify: `libs/map/bookmark_manager.hpp/cpp`
- `SetCategoryBookmarksIcon()` — cascade: set icon name + image data on all bookmarks
- Trigger texture registration when category becomes visible

### Modify: `libs/drape_frontend/drape_engine.hpp/cpp`
- Add `RegisterUserSymbol()` / `UnregisterUserSymbol()` methods
- Called when category visibility changes

### Modify: `android/sdk/src/main/cpp/.../BookmarkCategory.cpp`
- JNI: `nativeSetCategoryCustomIcon(long catId, byte[] pixels, int width, int height)`

### Modify: `android/sdk/src/main/java/.../BookmarkCategory.java`
- Java: `setCategoryCustomIcon(Bitmap bitmap)`

### New/modify: Android UI
- Image picker in category settings
- Scale/crop to 32×32

---

## Color interaction with custom images

Current shader behavior:
- **Main icon layer** (texCoords.xy): rendered as-is, no color mask
- **Background layer** (texCoords.zw): tinted by `v_maskColor.rgb`

For a custom 32×32 image:
1. If it's a **full-color photo/icon**: the shader renders it unmodified — good
2. If the user also sets a **category color**: the color tints the background (if present), the icon stays original — acceptable
3. If we want the icon itself to be **tintable** (like a grayscale mask): the icon must be an alpha-only mask, and we'd need to modify the shader or set the color in the main layer. Simpler: let the app describe the icon as "mask" vs "full-color" and pick the draw path.

**Recommendation**: First version only supports full-color icons. The color picker in UI tints the background circle behind the icon, not the icon itself. This is the simpler path and less confusing for users.

---

## Platform-agnostic architecture

```
┌──────────────────────────────────────────────────────────┐
│ PLATFORM UI (Android/iOS/Qt)                             │
│  - Image picker                                          │
│  - Decode to raw RGBA bytes                              │
│  - Call C++ core                                         │
└──────────────┬───────────────────────────────────────────┘
               │ byte[] rgbaPixels
               ▼
┌──────────────────────────────────────────────────────────┐
│ C++ CORE (shared, all platforms)                          │
│  BookmarkManager::SetCategoryBookmarksIcon()              │
│   → stores image data in CategoryData::m_properties       │
│   → cascades to bookmark m_properties["CustomImage"]      │
│   → calls DrapeEngine::RegisterUserSymbol()               │
└──────────────┬───────────────────────────────────────────┘
               │ string name, uint8_t* rgba
               ▼
┌──────────────────────────────────────────────────────────┐
│ DRAPE RENDERING (shared, all platforms,                   │
│   OpenGL/Vulkan/Metal)                                    │
│  TextureManager::RegisterRuntimeSymbol()                  │
│   → GPU texture allocation                                │
│   → glTexImage2D / Metal texture upload                  │
│  CacheUserMarks() — unchanged                             │
│   → GetSymbolRegion() finds runtime entry                │
│   → draws quad with same shader                          │
└──────────────────────────────────────────────────────────┘
```

---

## Implementation phases

### Phase 1: Runtime texture upload (~200 lines)
- `RegisterRuntimeSymbol()` in TextureManager
- Storage in a simple `std::unordered_map<string, RuntimeSymbolData>`
- One dedicated texture per symbol (Option A)

### Phase 2: Core integration (~150 lines)
- `BookmarkCategory::SetCustomBookmarkIconData()`
- `BookmarkManager::SetCategoryBookmarksIcon()` — cascade to bookmarks
- `DrapeEngine` bridge methods
- Image data serialization in `.kmb` via properties

### Phase 3: JNI + Java SDK (~80 lines)
- Native methods for pixel array transfer
- Java API on `BookmarkCategory`

### Phase 4: Android UI (~200 lines)
- Image picker (gallery/camera)
- Crop/scale to 32×32
- Preview in category settings
- Call SDK API

### Phase 5: Texture cleanup + edge cases (~100 lines)
- Unregister on category delete
- Reload on bookmark file reload
- Handle RGB vs RGBA vs grayscale input
- Max size enforcement

---

## Storage format in .kmb/.kml

The custom icon data lives in `CategoryData::m_properties` as key-value strings:

| Key | Value | Example |
|-----|-------|---------|
| `CustomBookmarkIcon` | Symbol name pattern (for atlas lookup) | `"1,user-icon-cat42-xs;8,user-icon-cat42-s;14,user-icon-cat42-m"` |
| `CustomBookmarkIconData` | Base64-encoded RGBA pixels | `"iVBORw0KGgo..."` (base64 of 32×32×4 = 4096 bytes raw ≈ 5460 base64 chars) |
| `CustomBookmarkIconWidth` | Pixel width | `"32"` |
| `CustomBookmarkIconHeight` | Pixel height | `"32"` |

Each bookmark in the category gets:
| Key | Value |
|-----|-------|
| `CustomImage` | Same symbol name pattern as above |

This survives round-trip through KML (as `<ExtendedData>`), KMB binary, and GPX export.
