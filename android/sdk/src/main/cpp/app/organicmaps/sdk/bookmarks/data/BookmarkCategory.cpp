#include "BookmarkCategory.hpp"

#include "app/organicmaps/sdk/Framework.hpp"
#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "drape/color.hpp"

#include "kml/types.hpp"

#include <sstream>

namespace
{
// Mirrors the anonymous GetBookmarkIconType() in map/bookmark.cpp.
std::string GetIconTypeName(kml::BookmarkIcon icon)
{
  switch (icon)
  {
  case kml::BookmarkIcon::None: return "default";
  case kml::BookmarkIcon::Hotel: return "hotel";
  case kml::BookmarkIcon::Animals: return "animals";
  case kml::BookmarkIcon::Buddhism: return "buddhism";
  case kml::BookmarkIcon::Building: return "building";
  case kml::BookmarkIcon::Christianity: return "christianity";
  case kml::BookmarkIcon::Entertainment: return "entertainment";
  case kml::BookmarkIcon::Exchange: return "exchange";
  case kml::BookmarkIcon::Food: return "restaurant";
  case kml::BookmarkIcon::Gas: return "gas";
  case kml::BookmarkIcon::Judaism: return "judaism";
  case kml::BookmarkIcon::Medicine: return "medicine";
  case kml::BookmarkIcon::Mountain: return "mountain";
  case kml::BookmarkIcon::Museum: return "museum";
  case kml::BookmarkIcon::Islam: return "islam";
  case kml::BookmarkIcon::Park: return "park";
  case kml::BookmarkIcon::Parking: return "parking";
  case kml::BookmarkIcon::Shop: return "shop";
  case kml::BookmarkIcon::Sights: return "sights";
  case kml::BookmarkIcon::Swim: return "swim";
  case kml::BookmarkIcon::Water: return "water";
  case kml::BookmarkIcon::Bar: return "bar";
  case kml::BookmarkIcon::Transport: return "transport";
  case kml::BookmarkIcon::Viewpoint: return "viewpoint";
  case kml::BookmarkIcon::Sport: return "sport";
  case kml::BookmarkIcon::Pub: return "pub";
  case kml::BookmarkIcon::Art: return "art";
  case kml::BookmarkIcon::Bank: return "bank";
  case kml::BookmarkIcon::Cafe: return "cafe";
  case kml::BookmarkIcon::Pharmacy: return "pharmacy";
  case kml::BookmarkIcon::Stadium: return "stadium";
  case kml::BookmarkIcon::Theatre: return "theatre";
  case kml::BookmarkIcon::Information: return "information";
  case kml::BookmarkIcon::ChargingStation: return "charging_station";
  case kml::BookmarkIcon::BicycleParking: return "bicycle_parking";
  case kml::BookmarkIcon::BicycleParkingCovered: return "bicycle_parking_covered";
  case kml::BookmarkIcon::BicycleRental: return "bicycle_rental";
  case kml::BookmarkIcon::FastFood: return "fast_food";
  case kml::BookmarkIcon::Airport: return "airport";
  case kml::BookmarkIcon::Count: return {};
  }
  return {};
}

std::string BuildIconSymbolString(uint16_t iconIndex)
{
  // None = clear custom icon, bookmarks fall back to their own m_icon enum.
  if (iconIndex >= static_cast<uint16_t>(kml::BookmarkIcon::Count) ||
      iconIndex == static_cast<uint16_t>(kml::BookmarkIcon::None))
    return {};

  auto const typeName = GetIconTypeName(static_cast<kml::BookmarkIcon>(iconIndex));
  // Zooms 1 and 8 use generic defaults (only default has xs/s variants in the atlas).
  // Zoom 14 uses the specific icon overlay.
  std::ostringstream oss;
  oss << "1,bookmark-default-xs"
      << ";8,bookmark-default-s"
      << ";14,bookmark-" << typeName << "-m";
  return oss.str();
}
}  // namespace

namespace
{
inline jclass getBookmarkCategoryClass(JNIEnv * env)
{
  static jclass g_bookmarkCategoryClass =
      jni::GetGlobalClassRef(env, "app/organicmaps/sdk/bookmarks/data/BookmarkCategory");
  return g_bookmarkCategoryClass;
}
}  // namespace

jobject ToJavaBookmarkCategory(JNIEnv * env, kml::MarkGroupId id)
{
  // clang-format off
  static jmethodID g_bookmarkCategoryConstructor = jni::GetConstructorID(env, getBookmarkCategoryClass(env),
    "("
    "J"                   // id
    "Ljava/lang/String;"  // name
    "Ljava/lang/String;"  // annotation
    "Ljava/lang/String;"  // desc
    "I"                   // tracksCount
    "I"                   // bookmarksCount
    "Z"                   // isVisible
    ")V"
  );
  // clang-format on

  auto const & manager = frm()->GetBookmarkManager();
  auto const & data = manager.GetCategoryData(id);

  auto const tracksCount = manager.GetTrackIds(data.m_id).size();
  auto const bookmarksCount = manager.GetUserMarkIds(data.m_id).size();
  auto const isVisible = manager.IsVisible(data.m_id);
  auto const preferBookmarkStr = GetPreferredBookmarkStr(data.m_name);
  auto const annotation = GetPreferredBookmarkStr(data.m_annotation);
  auto const description = GetPreferredBookmarkStr(data.m_description);

  jni::TScopedLocalRef preferBookmarkStrRef(env, jni::ToJavaString(env, preferBookmarkStr));
  jni::TScopedLocalRef annotationRef(env, jni::ToJavaString(env, annotation));
  jni::TScopedLocalRef descriptionRef(env, jni::ToJavaString(env, description));

  // clang-format off
  return env->NewObject(getBookmarkCategoryClass(env), g_bookmarkCategoryConstructor,
    static_cast<jlong>(data.m_id),
    preferBookmarkStrRef.get(),
    annotationRef.get(),
    descriptionRef.get(),
    static_cast<jint>(tracksCount),
    static_cast<jint>(bookmarksCount),
    static_cast<jboolean>(isVisible)
  );
  // clang-format on
}

jobjectArray ToJavaBookmarkCategories(JNIEnv * env, kml::GroupIdCollection const & ids)
{
  return jni::ToJavaArray(env, getBookmarkCategoryClass(env), ids, std::bind(&ToJavaBookmarkCategory, _1, _2));
}

extern "C"
{
JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetName(JNIEnv * env, jclass, jlong catId,
                                                                                      jstring name)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryName(static_cast<kml::MarkGroupId>(catId),
                                                               jni::ToNativeString(env, name));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetDescription(JNIEnv * env, jclass,
                                                                                             jlong catId, jstring desc)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryDescription(static_cast<kml::MarkGroupId>(catId),
                                                                      jni::ToNativeString(env, desc));
}

JNIEXPORT jboolean Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeIsVisible(JNIEnv *, jclass,
                                                                                            jlong catId)
{
  return static_cast<jboolean>(frm()->GetBookmarkManager().IsVisible(static_cast<kml::MarkGroupId>(catId)));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetVisibility(JNIEnv *, jclass,
                                                                                            jlong catId,
                                                                                            jboolean isVisible)
{
  frm()->GetBookmarkManager().GetEditSession().SetIsVisible(static_cast<kml::MarkGroupId>(catId), isVisible);
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetTags(JNIEnv * env, jclass, jlong catId,
                                                                                      jobjectArray tagsIds)
{
  auto const size = env->GetArrayLength(tagsIds);
  std::vector<std::string> categoryTags;
  categoryTags.reserve(static_cast<size_t>(size));
  for (auto i = 0; i < size; i++)
  {
    jni::TScopedLocalRef const item(env, env->GetObjectArrayElement(tagsIds, i));
    categoryTags.push_back(jni::ToNativeString(env, static_cast<jstring>(item.get())));
  }

  frm()->GetBookmarkManager().GetEditSession().SetCategoryTags(static_cast<kml::MarkGroupId>(catId), categoryTags);
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetAccessRules(JNIEnv *, jclass,
                                                                                             jlong catId,
                                                                                             jint accessRules)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryAccessRules(static_cast<kml::MarkGroupId>(catId),
                                                                      static_cast<kml::AccessRules>(accessRules));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCustomProperty(JNIEnv * env, jclass,
                                                                                                jlong catId,
                                                                                                jstring key,
                                                                                                jstring value)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryCustomProperty(
      static_cast<kml::MarkGroupId>(catId), jni::ToNativeString(env, key), jni::ToNativeString(env, value));
}

JNIEXPORT jboolean Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeIsEmpty(JNIEnv *, jclass, jlong catId)
{
  return static_cast<jboolean>(frm()->GetBookmarkManager().IsCategoryEmpty(static_cast<kml::MarkGroupId>(catId)));
}

JNIEXPORT jlong Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetBookmarkIdByPosition(
    JNIEnv *, jclass, jlong catId, jint positionInCategory)
{
  auto const & ids = frm()->GetBookmarkManager().GetUserMarkIds(static_cast<kml::MarkGroupId>(catId));
  if (positionInCategory >= static_cast<jlong>(ids.size()))
    return static_cast<jlong>(kml::kInvalidMarkId);
  auto it = ids.begin();
  std::advance(it, positionInCategory);
  return static_cast<jlong>(*it);
}

JNIEXPORT jlong Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetTrackIdByPosition(
    JNIEnv *, jclass, jlong catId, jint positionInCategory)
{
  auto const & ids = frm()->GetBookmarkManager().GetTrackIds(static_cast<kml::MarkGroupId>(catId));
  if (positionInCategory >= static_cast<jlong>(ids.size()))
    return static_cast<jlong>(kml::kInvalidTrackId);
  auto it = ids.begin();
  std::advance(it, positionInCategory);
  return static_cast<jlong>(*it);
}

JNIEXPORT jboolean JNICALL
Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeHasLastSortingType(JNIEnv *, jclass, jlong catId)
{
  auto const & bm = frm()->GetBookmarkManager();
  BookmarkManager::SortingType type;
  return static_cast<jboolean>(bm.GetLastSortingType(static_cast<kml::MarkGroupId>(catId), type));
}

JNIEXPORT jint Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetLastSortingType(JNIEnv *, jclass,
                                                                                                 jlong catId)
{
  auto const & bm = frm()->GetBookmarkManager();
  BookmarkManager::SortingType type;
  auto const hasType = bm.GetLastSortingType(static_cast<kml::MarkGroupId>(catId), type);
  ASSERT(hasType, ());
  UNUSED_VALUE(hasType);
  return static_cast<jint>(type);
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetLastSortingType(JNIEnv *, jclass,
                                                                                                 jlong catId, jint type)
{
  auto & bm = frm()->GetBookmarkManager();
  bm.SetLastSortingType(static_cast<kml::MarkGroupId>(catId), static_cast<BookmarkManager::SortingType>(type));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeResetLastSortingType(JNIEnv *, jclass,
                                                                                                   jlong catId)
{
  auto & bm = frm()->GetBookmarkManager();
  bm.ResetLastSortingType(static_cast<kml::MarkGroupId>(catId));
}

JNIEXPORT jintArray Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetAvailableSortingTypes(
    JNIEnv * env, jclass, jlong catId, jboolean hasMyPosition)
{
  auto const & bm = frm()->GetBookmarkManager();
  auto const types =
      bm.GetAvailableSortingTypes(static_cast<kml::MarkGroupId>(catId), static_cast<bool>(hasMyPosition));
  int const size = static_cast<int>(types.size());
  jintArray jTypes = env->NewIntArray(size);
  jint * arr = env->GetIntArrayElements(jTypes, 0);
  for (int i = 0; i < size; ++i)
    arr[i] = static_cast<int>(types[i]);
  env->ReleaseIntArrayElements(jTypes, arr, 0);

  return jTypes;
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCategoryBookmarksColor(JNIEnv *,
                                                                                                        jclass,
                                                                                                        jlong catId,
                                                                                                        jint color)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryBookmarksColor(
      static_cast<kml::MarkGroupId>(catId), dp::Color::FromARGB(static_cast<uint32_t>(color)));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCategoryTracksCustomColor(JNIEnv *,
                                                                                                           jclass,
                                                                                                           jlong catId,
                                                                                                           jint color)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryTracksColor(
      static_cast<kml::MarkGroupId>(catId), dp::Color::FromARGB(static_cast<uint32_t>(color)));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCategoryBookmarksIcon(
    JNIEnv * env, jclass, jlong catId, jstring icon)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryBookmarksIcon(
      static_cast<kml::MarkGroupId>(catId), jni::ToNativeString(env, icon));
}

JNIEXPORT jstring Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetCategoryBookmarksIcon(
    JNIEnv * env, jclass, jlong catId)
{
  return jni::ToJavaString(env, frm()->GetBookmarkManager().GetCategoryBookmarksIcon(
                                    static_cast<kml::MarkGroupId>(catId)));
}

JNIEXPORT jstring Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetBookmarkIconSymbolString(
    JNIEnv * env, jclass, jint iconIndex)
{
  return jni::ToJavaString(env, BuildIconSymbolString(static_cast<uint16_t>(iconIndex)));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCategoryBookmarksIconData(
    JNIEnv * env, jclass, jlong catId, jstring data, jint width, jint height, jstring format)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryBookmarksIconData(
      static_cast<kml::MarkGroupId>(catId), jni::ToNativeString(env, data),
      static_cast<uint32_t>(width), static_cast<uint32_t>(height), jni::ToNativeString(env, format));
}

JNIEXPORT jstring Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetCategoryBookmarksIconData(
    JNIEnv * env, jclass, jlong catId)
{
  return jni::ToJavaString(env, frm()->GetBookmarkManager().GetCategoryBookmarksIconData(
                                    static_cast<kml::MarkGroupId>(catId)));
}

JNIEXPORT void Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeSetCategoryBookmarksIconMinZoom(
    JNIEnv * env, jclass, jlong catId, jint zoom)
{
  frm()->GetBookmarkManager().GetEditSession().SetCategoryBookmarksIconMinZoom(
      static_cast<kml::MarkGroupId>(catId), static_cast<int>(zoom));
}

JNIEXPORT jint Java_app_organicmaps_sdk_bookmarks_data_BookmarkCategory_nativeGetCategoryBookmarksIconMinZoom(
    JNIEnv * env, jclass, jlong catId)
{
  return static_cast<jint>(frm()->GetBookmarkManager().GetCategoryBookmarksIconMinZoom(
                                static_cast<kml::MarkGroupId>(catId)));
}
}  // extern "C"
