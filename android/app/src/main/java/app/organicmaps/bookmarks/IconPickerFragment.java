package app.organicmaps.bookmarks;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import app.organicmaps.R;
import app.organicmaps.sdk.bookmarks.data.Icon;

/**
 * Bottom-sheet grid of bookmark icons for category-level icon selection.
 * Returns the icon index (0 = None, ...) to the parent fragment via
 * {@link OnIconSelectedListener}.
 */
public class IconPickerFragment extends BottomSheetDialogFragment
{
  /** Receives the selected icon index. */
  public interface OnIconSelectedListener
  {
    void onIconSelected(int iconIndex);
  }

  private static final int GRID_COLUMNS = 5;

  private RecyclerView mGrid;
  private IconAdapter mAdapter;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_icon_picker, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    mGrid = view.findViewById(R.id.icon_grid);
    mGrid.setLayoutManager(new GridLayoutManager(requireContext(), GRID_COLUMNS));

    final int iconCount = Icon.getIconCount();
    mAdapter = new IconAdapter(iconCount);
    mGrid.setAdapter(mAdapter);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    final View sheet = requireView().getParent() instanceof View ? (View) requireView().getParent() : null;
    if (sheet != null)
    {
      BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
      behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
      behavior.setSkipCollapsed(true);
    }
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog)
  {
    super.onDismiss(dialog);
  }

  private void onIconClicked(View view)
  {
    final int iconIndex = (int) view.getTag();
    final OnIconSelectedListener listener;
    if (getParentFragment() instanceof OnIconSelectedListener)
      listener = (OnIconSelectedListener) getParentFragment();
    else if (getActivity() instanceof OnIconSelectedListener)
      listener = (OnIconSelectedListener) getActivity();
    else
      listener = null;

    if (listener != null)
      listener.onIconSelected(iconIndex);
    dismiss();
  }

  /** Simple adapter that displays bookmark icons in a grid. */
  private class IconAdapter extends RecyclerView.Adapter<IconAdapter.ViewHolder>
  {
    private final int mCount;

    IconAdapter(int count)
    {
      mCount = count;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      final ImageView iv = new ImageView(parent.getContext());
      final int size = parent.getResources().getDimensionPixelSize(R.dimen.bookmark_icon_size);
      final int margin = parent.getResources().getDimensionPixelSize(R.dimen.margin_half);
      final RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size + margin * 2, size + margin * 2);
      lp.setMargins(margin, margin, margin, margin);
      iv.setLayoutParams(lp);
      iv.setScaleType(ImageView.ScaleType.CENTER);
      iv.setBackgroundResource(R.drawable.bg_clickable_card);
      iv.setOnClickListener(IconPickerFragment.this::onIconClicked);
      return new ViewHolder(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position)
    {
      holder.itemView.setTag(position);
      ((ImageView) holder.itemView).setImageResource(Icon.getDrawableResId(position));
    }

    @Override
    public int getItemCount()
    {
      return mCount;
    }

    static class ViewHolder extends RecyclerView.ViewHolder
    {
      ViewHolder(@NonNull View itemView)
      {
        super(itemView);
      }
    }
  }
}
