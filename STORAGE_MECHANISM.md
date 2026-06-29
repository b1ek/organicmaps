# Custom Bookmark Icon — Storage Mechanism

## Overview

Bookmark icons come in two modes, both stored in the generic key-value property bag
(`std::map<std::string, std::string>`) on categories and bookmarks.
No new C++ struct fields were added. KML/KMB serialization works automatically.

## Property keys

### Category-level (`CategoryData::m_properties`)

| Key | Value | Set by | Used for |
|-----|-------|--------|----------|
| `CustomBookmarkIcon` | `"zoom,name;zoom,name;..."` | `BookmarkCategory::SetCustomBookmarkIcon()` | Preset atlas icon |
| `CustomBookmarkIconData` | base64-encoded RGBA bytes | `BookmarkCategory::SetCustomBookmarkIconData()` | Custom uploaded image |
| `CustomBookmarkIconWidth` | `"<int>"` | `SetCustomBookmarkIconData()` | Custom image width |
| `CustomBookmarkIconHeight` | `"<int>"` | `SetCustomBookmarkIconData()` | Custom image height |
| `CustomBookmarkIconFormat` | `"rgba"` or `"alpha"` | `SetCustomBookmarkIconData()` | Custom image format |

### Bookmark-level (`BookmarkData::m_properties`)

| Key | Value | Set by | Used for |
|-----|-------|--------|----------|
| `CustomImage` | `"zoom,name;zoom,name;..."` | `Bookmark::SetCustomIcon()` | Preset atlas icon override |
| `CustomImageData` | base64-encoded RGBA bytes | `Bookmark::SetCustomIconData()` | Custom uploaded image override |
| `CustomImageWidth` | `"<int>"` | `SetCustomIconData()` | Custom image width |
| `CustomImageHeight` | `"<int>"` | `SetCustomIconData()` | Custom image height |
| `CustomImageFormat` | `"rgba"` or `"alpha"` | `SetCustomIconData()` | Custom image format |

**Note:** The category uses `CustomBookmark*` prefix, bookmarks use `Custom*` prefix.
This is intentional — category stores the authoritative value, bookmarks get a cascade copy.

## Preset icon format (`CustomBookmarkIcon` / `CustomImage`)

Format: `"<zoom>,<symbolName>;<zoom>,<symbolName>;..."`

Example: `"1,bookmark-default-xs;8,bookmark-default-s;14,bookmark-hotel-m"`

- Zoom levels 1 and 8 always use `bookmark-default-xs` and `bookmark-default-s`
  (only the generic default icon has xs/s variants in the atlas).
- Zoom level 14 uses the specific icon overlay: `bookmark-{type}-m`
  where `{type}` comes from `GetIconTypeName()` (see below).
- Empty string = clear custom icon → bookmarks fall back to their `m_icon` enum.

### Icon type name mapping

The function `GetIconTypeName(BookmarkIcon)` (in `BookmarkCategory.cpp` JNI,
mirroring anonymous `GetBookmarkIconType()` in `bookmark.cpp`) maps enum → string:

```
None → "default"
Hotel → "hotel"
Animals → "animals"
Buddhism → "buddhism"
Building → "building"
Christianity → "christianity"
Entertainment → "entertainment"
Exchange → "exchange"
Food → "restaurant"       (!! not "food")
Gas → "gas"
Judaism → "judaism"
Medicine → "medicine"
Mountain → "mountain"
Museum → "museum"
Islam → "islam"
Park → "park"
Parking → "parking"
Shop → "shop"
Sights → "sights"
Swim → "swim"
Water → "water"
Bar → "bar"
Transport → "transport"
Viewpoint → "viewpoint"
Sport → "sport"
Pub → "pub"
Art → "art"
Bank → "bank"
Cafe → "cafe"
Pharmacy → "pharmacy"
Stadium → "stadium"
Theatre → "theatre"
Information → "information"
ChargingStation → "charging_station"
BicycleParking → "bicycle_parking"
BicycleParkingCovered → "bicycle_parking_covered"
BicycleRental → "bicycle_rental"
FastFood → "fast_food"
Airport → "airport"
```

## Custom image data format (`CustomBookmarkIconData` / `CustomImageData`)

- Raw RGBA pixel bytes, base64-encoded.
- Pixel layout: R,G,B,A,R,G,B,A,... (row-major, top-left to bottom-right).
- Width/height stored in companion integer properties.
- Format is `"rgba"` (always 4 bytes per pixel) or `"alpha"` (single channel, TBD).
- Empty data string = clear custom image.
- The rendering code must base64-decode the data to get raw pixels.

### Java-side encoding (for reference)

```java
// ARGB ints → RGBA bytes
int[] pixels = new int[width * height];
bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
byte[] rgba = new byte[width * height * 4];
for (int i = 0; i < pixels.length; i++) {
    int p = pixels[i];
    int off = i * 4;
    rgba[off]   = (byte)((p >> 16) & 0xFF);  // R
    rgba[off+1] = (byte)((p >> 8) & 0xFF);   // G
    rgba[off+2] = (byte)(p & 0xFF);           // B
    rgba[off+3] = (byte)((p >> 24) & 0xFF);   // A
}
String base64 = Base64.encodeToString(rgba, Base64.NO_WRAP);
```

## Cascading behavior

When a category icon is set (preset or custom), all bookmarks in that category are updated:

```
EditSession::SetCategoryBookmarksIcon(groupId, icon)
  → category.SetCustomBookmarkIcon(icon)        // stores on category
  → for each bookmark in groupId:
      bookmark.SetCustomIcon(icon)              // stores on each bookmark

EditSession::SetCategoryBookmarksIconData(groupId, data, w, h, fmt)
  → category.SetCustomBookmarkIconData(...)     // stores on category
  → for each bookmark in groupId:
      bookmark.SetCustomIconData(...)           // stores on each bookmark
```

Mirrors `SetCategoryBookmarksColor()` pattern exactly.

## Rendering entry points

### Current rendering path

```
Bookmark::GetSymbolNames()
  → GetCustomSymbolNames()        // checks CustomImage / CustomImageData
    → if present: return custom SymbolNameZoomInfo
    → if absent: return nullptr
  → if custom absent: build SymbolNameZoomInfo from m_icon enum
```

`GetCustomSymbolNames()` (in `bookmark.cpp` line 161):
```cpp
auto it = m_data.m_properties.find("CustomImage");
if (it == m_data.m_properties.end())
    return nullptr;  // no custom icon, use default m_icon path
// Parse zoom,name;zoom,name;... pairs into SymbolNameZoomInfo
```

### What needs to change for custom image data rendering

`GetCustomSymbolNames()` currently only checks `CustomImage` (preset names).
It must also check `CustomImageData`:

```cpp
// Pseudocode for new GetCustomSymbolNames():
auto dataIt = m_data.m_properties.find("CustomImageData");
if (dataIt != m_data.m_properties.end() && !dataIt->second.empty()) {
    // Custom RGBA image is set.
    // Generate a unique symbol name, e.g. "custom-bookmark-<markId>"
    // Register the RGBA data with TextureManager under that name.
    // Return SymbolNameZoomInfo pointing to that name.
    return ...;
}
auto iconIt = m_data.m_properties.find("CustomImage");
if (iconIt != m_data.m_properties.end()) {
    // Preset icon symbol names.
    // Parse and return as currently done.
    return ...;
}
return nullptr;  // No custom icon, fall back to m_icon enum.
```

### TextureManager changes needed

Add a method to register runtime textures:

```cpp
// texture_manager.hpp
class TextureManager {
public:
  // Register a symbol backed by raw RGBA pixel data.
  // Called from the main thread; actual GPU upload happens in UpdateDynamicTextures.
  void RegisterRuntimeSymbol(std::string const & name,
                             uint8_t const * rgba, uint32_t width, uint32_t height);
private:
  // Pending runtime symbols waiting for GPU upload.
  std::map<std::string, RuntimeSymbolData> m_pendingRuntimeSymbols;
};
```

`GetSymbolRegionSafe()` should check runtime symbols before atlas textures.

The RGBA data can be decoded from the property when the bookmark is first rendered,
or pre-decoded and cached in a map keyed by symbol name.

### Key files

| File | Purpose |
|------|---------|
| `libs/map/bookmark.cpp` | `GetSymbolNames()`, `GetCustomSymbolNames()`, `SetCustomIcon()`, `SetCustomIconData()` |
| `libs/map/bookmark.hpp` | Declarations |
| `libs/drape/texture_manager.hpp` | `GetSymbolRegion()`, `GetSymbolRegionSafe()` |
| `libs/drape/texture_manager.cpp` | Atlas symbol lookup, texture lifecycle |
| `libs/drape/symbols_texture.hpp` | GPU texture holding atlas symbols |
| `libs/drape_frontend/user_mark_shapes.cpp` | `CacheUserMarks()` — builds render batches |
| `libs/drape_frontend/drape_engine.cpp` | Calls `GetSymbolNames()` (line 925) |

## Round-trip verification

The Android UI in `BookmarkCategorySettingsFragment` verifies storage by:
1. Picking image from gallery
2. Decoding to 32×32 RGBA → base64 → `setCategoryBookmarksIconData()`
3. Re-reading via `getCategoryBookmarksIconData()` → base64-decode → Bitmap → display thumbnail

The preview thumbnail confirms the data survives the JNI round-trip.
To verify .kmb persistence: close and reopen the category settings — if the preview
still shows, the data was serialized and deserialized correctly.

## Priority

If both `CustomImageData` and `CustomImage` are present on a bookmark,
`CustomImageData` takes priority (explicit upload beats named symbol).
The category `SetCategoryBookmarksIconData()` clears category-level
`CustomBookmarkIcon` before setting, so they are mutually exclusive at the category level.
