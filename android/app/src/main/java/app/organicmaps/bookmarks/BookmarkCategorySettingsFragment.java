package app.organicmaps.bookmarks;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmToolbarFragment;
import app.organicmaps.sdk.bookmarks.data.BookmarkCategory;
import app.organicmaps.sdk.bookmarks.data.BookmarkManager;
import app.organicmaps.sdk.bookmarks.data.DataChangedListener;
import app.organicmaps.util.InputUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.widget.colorpicker.ColorPickerFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Objects;

public class BookmarkCategorySettingsFragment
    extends BaseMwmToolbarFragment implements ColorPickerFragment.OnColorChangeListener,
                                               IconPickerFragment.OnIconSelectedListener
{
  private static final int TEXT_LENGTH_LIMIT = 60;
  private static final String EXTRA_PICKING_TRACKS_COLOR = "picking_tracks_color";

  // The category color picker is shared between bookmarks and tracks; remember which one is active.
  // Persisted in the instance state: the picker outlives a rotation or process recreation, and its
  // result must not be delivered to the wrong target.
  private boolean mPickingTracksColor;

  @NonNull
  private final DataChangedListener mCategoriesListener = this::onCategoriesChanged;

  private final ActivityResultLauncher<String> mPickImageLauncher =
      registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private BookmarkCategory mCategory;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextInputEditText mEditDescView;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextInputEditText mEditCategoryNameView;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mIconBookmarksBtn;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mCustomIconBtn;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mColorBookmarksBtn;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mColorTracksBtn;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mColorSectionDivider;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mColorSectionSpacer;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private View mCustomIconPreviewRow;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private ImageView mCustomIconPreviewImg;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextInputLayout mMinZoomInput;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private TextInputEditText mEditMinZoomView;

  @NonNull
  private final MenuProvider mMenuProvider = new MenuProvider() {
    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater)
    {
      menuInflater.inflate(R.menu.menu_done, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem)
    {
      if (menuItem.getItemId() == R.id.done)
      {
        onEditDoneClicked();
        return true;
      }
      return false;
    }
  };

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    final Bundle args = requireArguments();
    mCategory = Objects.requireNonNull(
        Utils.getParcelable(args, BookmarkCategorySettingsActivity.EXTRA_BOOKMARK_CATEGORY, BookmarkCategory.class));
    if (savedInstanceState != null)
      mPickingTracksColor = savedInstanceState.getBoolean(EXTRA_PICKING_TRACKS_COLOR, false);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState)
  {
    super.onSaveInstanceState(outState);
    outState.putBoolean(EXTRA_PICKING_TRACKS_COLOR, mPickingTracksColor);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    BookmarkManager.INSTANCE.addCategoriesUpdatesListener(mCategoriesListener);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    BookmarkManager.INSTANCE.removeCategoriesUpdatesListener(mCategoriesListener);
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    final View root = inflater.inflate(R.layout.fragment_bookmark_category_settings, container, false);
    requireActivity().addMenuProvider(mMenuProvider, getViewLifecycleOwner());
    initViews(root);
    return root;
  }

  private void initViews(@NonNull View root)
  {
    mEditCategoryNameView = root.findViewById(R.id.edit_list_name_view);
    TextInputLayout clearNameBtn = root.findViewById(R.id.edit_list_name_input);
    clearNameBtn.setEndIconOnClickListener(v -> clearAndFocus(mEditCategoryNameView));
    mEditCategoryNameView.setText(mCategory.getName());
    InputFilter[] f = {new InputFilter.LengthFilter(TEXT_LENGTH_LIMIT)};
    mEditCategoryNameView.setFilters(f);
    mEditCategoryNameView.requestFocus();
    mEditCategoryNameView.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
      {}

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
      {
        clearNameBtn.setEndIconVisible(charSequence.length() > 0);
      }

      @Override
      public void afterTextChanged(Editable editable)
      {}
    });
    mEditDescView = root.findViewById(R.id.edit_description);
    mEditDescView.setText(mCategory.getDescription());

    mIconBookmarksBtn = root.findViewById(R.id.icon_bookmarks_btn);
    mIconBookmarksBtn.setOnClickListener(v -> showBookmarkIconPicker());
    mCustomIconBtn = root.findViewById(R.id.custom_icon_btn);
    mCustomIconBtn.setOnClickListener(v -> pickCustomImage());
    mColorBookmarksBtn = root.findViewById(R.id.color_bookmarks_btn);
    mColorBookmarksBtn.setOnClickListener(v -> showBookmarkColorPicker());
    mColorTracksBtn = root.findViewById(R.id.color_tracks_btn);
    mColorTracksBtn.setOnClickListener(v -> showTrackColorPicker());
    mColorSectionDivider = root.findViewById(R.id.color_section_divider);
    mColorSectionSpacer = root.findViewById(R.id.color_section_spacer);
    mCustomIconPreviewRow = root.findViewById(R.id.custom_icon_preview_row);
    mCustomIconPreviewImg = root.findViewById(R.id.custom_icon_preview_img);
    mMinZoomInput = root.findViewById(R.id.custom_icon_min_zoom_input);
    mEditMinZoomView = root.findViewById(R.id.edit_min_zoom_view);
    mEditMinZoomView.setText(String.valueOf(mCategory.getCategoryBookmarksIconMinZoom()));

    updateCustomIconPreview();
    updateColorButtonsVisibility();
  }

  private void updateColorButtonsVisibility()
  {
    final BookmarkCategory category = BookmarkManager.INSTANCE.getCategoryById(mCategory.getId());
    final int bookmarksCount = category.getBookmarksCount();
    final int tracksCount = category.getTracksCount();

    mIconBookmarksBtn.setVisibility(bookmarksCount > 0 ? View.VISIBLE : View.GONE);
    mCustomIconBtn.setVisibility(bookmarksCount > 0 ? View.VISIBLE : View.GONE);
    mColorBookmarksBtn.setVisibility(bookmarksCount > 0 ? View.VISIBLE : View.GONE);
    mColorTracksBtn.setVisibility(tracksCount > 0 ? View.VISIBLE : View.GONE);

    final boolean hasContent = bookmarksCount > 0 || tracksCount > 0;
    mColorSectionDivider.setVisibility(hasContent ? View.VISIBLE : View.GONE);
    mColorSectionSpacer.setVisibility(hasContent ? View.VISIBLE : View.GONE);
  }

  private void onEditDoneClicked()
  {
    final String newCategoryName = getEditableCategoryName();
    if (!validateCategoryName(newCategoryName))
      return;

    if (isCategoryNameChanged())
      mCategory.setName(newCategoryName);

    if (isCategoryDescChanged())
      mCategory.setDescription(getEditableCategoryDesc());

    saveMinZoom();
    requireActivity().finish();
  }

  private void saveMinZoom()
  {
    if (mMinZoomInput.getVisibility() != View.VISIBLE)
      return;
    final String text = mEditMinZoomView.getEditableText().toString().trim();
    if (text.isEmpty())
      return;
    try
    {
      int zoom = Integer.parseInt(text);
      if (zoom < 1)
        zoom = 1;
      if (zoom > 20)
        zoom = 20;
      mCategory.setCategoryBookmarksIconMinZoom(zoom);
    }
    catch (NumberFormatException ignored)
    {
    }
  }

  private boolean isCategoryNameChanged()
  {
    String categoryName = getEditableCategoryName();
    return !TextUtils.equals(categoryName, mCategory.getName());
  }

  private boolean validateCategoryName(@Nullable String name)
  {
    if (TextUtils.isEmpty(name))
    {
      new MaterialAlertDialogBuilder(requireActivity(), R.style.MwmTheme_AlertDialog)
          .setTitle(R.string.bookmarks_error_title_empty_list_name)
          .setMessage(R.string.bookmarks_error_message_empty_list_name)
          .setPositiveButton(R.string.ok, null)
          .show();
      return false;
    }

    if (BookmarkManager.INSTANCE.isUsedCategoryName(name) && !TextUtils.equals(name, mCategory.getName()))
    {
      new MaterialAlertDialogBuilder(requireActivity(), R.style.MwmTheme_AlertDialog)
          .setTitle(R.string.bookmarks_error_title_list_name_already_taken)
          .setMessage(R.string.bookmarks_error_message_list_name_already_taken)
          .setPositiveButton(R.string.ok, null)
          .show();
      return false;
    }
    return true;
  }

  @NonNull
  private String getEditableCategoryName()
  {
    return mEditCategoryNameView.getEditableText().toString().trim();
  }

  @NonNull
  private String getEditableCategoryDesc()
  {
    return mEditDescView.getEditableText().toString().trim();
  }

  private boolean isCategoryDescChanged()
  {
    String categoryDesc = getEditableCategoryDesc();
    return !TextUtils.equals(mCategory.getDescription(), categoryDesc);
  }

  private void clearAndFocus(@NonNull TextView textView)
  {
    textView.getEditableText().clear();
    textView.requestFocus();
    InputUtils.showKeyboard(textView);
  }

  private void showBookmarkColorPicker()
  {
    mPickingTracksColor = false;
    ColorPickerFragment.show(getChildFragmentManager(), BookmarkManager.INSTANCE.getLastEditedColor());
  }

  private void showTrackColorPicker()
  {
    mPickingTracksColor = true;
    new ColorPickerFragment().show(getChildFragmentManager(), null);
  }

  private void showBookmarkIconPicker()
  {
    new IconPickerFragment().show(getChildFragmentManager(), null);
  }

  private void pickCustomImage()
  {
    mPickImageLauncher.launch("image/*");
  }

  private void updateCustomIconPreview()
  {
    final String data = mCategory.getCategoryBookmarksIconData();
    if (data == null)
    {
      mCustomIconPreviewRow.setVisibility(View.GONE);
      mMinZoomInput.setVisibility(View.GONE);
      return;
    }
    try
    {
      final byte[] rgba = Base64.decode(data, Base64.DEFAULT);
      final int pixelCount = rgba.length / 4;
      final int side = (int) Math.sqrt(pixelCount);
      if (side * side * 4 != rgba.length)
      {
        mCustomIconPreviewRow.setVisibility(View.GONE);
        mMinZoomInput.setVisibility(View.GONE);
        return;
      }
      // Convert RGBA bytes to ARGB ints for Bitmap.
      final int[] argb = new int[side * side];
      for (int i = 0; i < argb.length; i++)
      {
        final int offset = i * 4;
        final int r = rgba[offset] & 0xFF;
        final int g = rgba[offset + 1] & 0xFF;
        final int b = rgba[offset + 2] & 0xFF;
        final int a = rgba[offset + 3] & 0xFF;
        argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
      }
      final Bitmap bitmap = Bitmap.createBitmap(argb, side, side, Bitmap.Config.ARGB_8888);
      mCustomIconPreviewImg.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
      mCustomIconPreviewRow.setVisibility(View.VISIBLE);
      mMinZoomInput.setVisibility(View.VISIBLE);
      mEditMinZoomView.setText(String.valueOf(mCategory.getCategoryBookmarksIconMinZoom()));
    }
    catch (Exception e)
    {
      mCustomIconPreviewRow.setVisibility(View.GONE);
      mMinZoomInput.setVisibility(View.GONE);
    }
  }

  private void onImagePicked(@Nullable Uri uri)
  {
    if (uri == null)
      return;
    try
    {
      Bitmap bitmap = decodeSampledBitmap(uri, 32, 32);
      if (bitmap == null)
      {
        Toast.makeText(requireContext(), R.string.error_failed_to_load_image, Toast.LENGTH_SHORT).show();
        return;
      }
      final int width = bitmap.getWidth();
      final int height = bitmap.getHeight();
      final int[] pixels = new int[width * height];
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
      bitmap.recycle();

      // Convert ARGB ints to RGBA bytes.
      final byte[] rgba = new byte[width * height * 4];
      for (int i = 0; i < pixels.length; i++)
      {
        final int p = pixels[i];
        final int offset = i * 4;
        rgba[offset]     = (byte) ((p >> 16) & 0xFF);  // R
        rgba[offset + 1] = (byte) ((p >> 8) & 0xFF);   // G
        rgba[offset + 2] = (byte) (p & 0xFF);           // B
        rgba[offset + 3] = (byte) ((p >> 24) & 0xFF);   // A
      }

      final String base64 = Base64.encodeToString(rgba, Base64.NO_WRAP);
      mCategory.setCategoryBookmarksIconData(base64, width, height, "rgba");
      // Reset min zoom to default when a new image is picked.
      mCategory.setCategoryBookmarksIconMinZoom(14);
      updateCustomIconPreview();
      Toast.makeText(requireContext(),
                     getString(R.string.toast_custom_icon_set, width, height),
                     Toast.LENGTH_SHORT).show();
    }
    catch (Exception e)
    {
      Toast.makeText(requireContext(), R.string.error_failed_to_load_image, Toast.LENGTH_SHORT).show();
    }
  }

  /** Decodes an image URI to a bitmap no larger than maxWidth×maxHeight. */
  @Nullable
  private Bitmap decodeSampledBitmap(@NonNull Uri uri, int maxWidth, int maxHeight)
  {
    try (InputStream in = requireContext().getContentResolver().openInputStream(uri))
    {
      if (in == null)
        return null;

      // First decode bounds only.
      final BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, opts);

      // Calculate sample size.
      int sampleSize = 1;
      while (opts.outWidth / sampleSize > maxWidth || opts.outHeight / sampleSize > maxHeight)
        sampleSize *= 2;

      // Second decode with sample size.
      try (InputStream in2 = requireContext().getContentResolver().openInputStream(uri))
      {
        final BitmapFactory.Options opts2 = new BitmapFactory.Options();
        opts2.inSampleSize = sampleSize;
        return BitmapFactory.decodeStream(in2, null, opts2);
      }
    }
    catch (Exception e)
    {
      return null;
    }
  }

  @Override
  public void onIconSelected(int iconIndex)
  {
    final String iconString = BookmarkCategory.getBookmarkIconSymbolString(iconIndex);
    mCategory.setCategoryBookmarksIcon(iconString);
  }

  @Override
  public void onColorSet(@ColorInt int color)
  {
    if (mPickingTracksColor)
    {
      mCategory.setCategoryTracksCustomColor(color);
      Toast.makeText(requireContext(), R.string.toast_tracks_color_changed, Toast.LENGTH_SHORT).show();
    }
    else
    {
      mCategory.setCategoryBookmarksColor(color);
      Toast.makeText(requireContext(), R.string.toast_bookmarks_color_changed, Toast.LENGTH_SHORT).show();
    }
  }

  private void onCategoriesChanged()
  {
    if (getView() != null)
      updateColorButtonsVisibility();
  }
}
