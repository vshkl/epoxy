package com.airbnb.epoxy;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This class represent an item in a {@link android.view.ViewGroup} and it is
 * being reused with multiple model via the update method. There is 1:1 relationship between an
 * EpoxyVisibilityItem and a child within the {@link android.view.ViewGroup}.
 *
 * It contains the logic to compute the visibility state of an item. It will also invoke the
 * visibility callbacks on {@link com.airbnb.epoxy.EpoxyViewHolder}
 *
 * This class should remain non-public and is intended to be used by {@link EpoxyVisibilityTracker}
 * only.
 */
class EpoxyVisibilityItem {

  private static final int NOT_NOTIFIED = -1;

  private final Rect localVisibleRect = new Rect();

  private int adapterPosition = RecyclerView.NO_POSITION;

  @Px
  private int height;
  @Px
  private int width;

  @Px
  private int visibleHeight;
  @Px
  private int visibleWidth;

  @Px
  private int viewportHeight;
  @Px
  private int viewportWidth;

  private boolean partiallyVisible = false;
  private boolean fullyVisible = false;
  private boolean visible = false;
  private boolean focusedVisible = false;
  private int viewVisibility = View.GONE;

  /** Store last value for de-duping */
  private int lastVisibleHeightNotified = NOT_NOTIFIED;
  private int lastVisibleWidthNotified = NOT_NOTIFIED;
  private int lastVisibilityNotified = NOT_NOTIFIED;

  EpoxyVisibilityItem() {
  }

  EpoxyVisibilityItem(int adapterPosition) {
    reset(adapterPosition);
  }

  /**
   * Update the visibility item according the current layout.
   *
   * @param view        the current {@link com.airbnb.epoxy.EpoxyViewHolder}'s itemView
   * @param parent      the {@link android.view.ViewGroup}
   * @return true if the view has been measured
   */
  boolean update(@NonNull View view, @NonNull ViewGroup parent, boolean detachEvent) {
    // Clear the rect before calling getLocalVisibleRect
    localVisibleRect.setEmpty();
    boolean viewDrawn = view.getLocalVisibleRect(localVisibleRect) && !detachEvent;
    height = view.getHeight();
    width = view.getWidth();
    viewportHeight = parent.getHeight();
    viewportWidth = parent.getWidth();
    visibleHeight = viewDrawn ? localVisibleRect.height() : 0;
    visibleWidth = viewDrawn ? localVisibleRect.width() : 0;
    viewVisibility = view.getVisibility();
    return height > 0 && width > 0;
  }

  int getAdapterPosition() {
    return adapterPosition;
  }

  void reset(int newAdapterPosition) {
    fullyVisible = false;
    visible = false;
    focusedVisible = false;
    adapterPosition = newAdapterPosition;
    lastVisibleHeightNotified = NOT_NOTIFIED;
    lastVisibleWidthNotified = NOT_NOTIFIED;
    lastVisibilityNotified = NOT_NOTIFIED;
  }

  void handleVisible(@NonNull EpoxyViewHolder epoxyHolder, boolean detachEvent) {
    boolean previousVisible = visible;
    visible = !detachEvent && isVisible();
    if (visible != previousVisible) {
      if (visible) {
        epoxyHolder.visibilityStateChanged(VisibilityState.VISIBLE);
      } else {
        epoxyHolder.visibilityStateChanged(VisibilityState.INVISIBLE);
      }
    }
  }

  void handleFocus(EpoxyViewHolder epoxyHolder, boolean detachEvent) {
    boolean previousFocusedVisible = focusedVisible;
    focusedVisible = !detachEvent && isInFocusVisible();
    if (focusedVisible != previousFocusedVisible) {
      if (focusedVisible) {
        epoxyHolder.visibilityStateChanged(VisibilityState.FOCUSED_VISIBLE);
      } else {
        epoxyHolder.visibilityStateChanged(VisibilityState.UNFOCUSED_VISIBLE);
      }
    }
  }

  void handlePartialImpressionVisible(EpoxyViewHolder epoxyHolder, boolean detachEvent,
      @IntRange(from = 0, to = 100) int thresholdPercentage) {
    boolean previousPartiallyVisible = partiallyVisible;
    partiallyVisible = !detachEvent && isPartiallyVisible(thresholdPercentage);
    if (partiallyVisible != previousPartiallyVisible) {
      if (partiallyVisible) {
        epoxyHolder.visibilityStateChanged(VisibilityState.PARTIAL_IMPRESSION_VISIBLE);
      } else {
        epoxyHolder.visibilityStateChanged(VisibilityState.PARTIAL_IMPRESSION_INVISIBLE);
      }
    }
  }

  void handleFullImpressionVisible(EpoxyViewHolder epoxyHolder, boolean detachEvent) {
    boolean previousFullyVisible = fullyVisible;
    fullyVisible = !detachEvent && isFullyVisible();
    if (fullyVisible != previousFullyVisible) {
      if (fullyVisible) {
        epoxyHolder.visibilityStateChanged(VisibilityState.FULL_IMPRESSION_VISIBLE);
      }
    }
  }

  boolean handleChanged(EpoxyViewHolder epoxyHolder, boolean visibilityChangedEnabled) {
    boolean changed = false;
    if (visibleHeight != lastVisibleHeightNotified || visibleWidth != lastVisibleWidthNotified
        || viewVisibility != lastVisibilityNotified) {
      if (visibilityChangedEnabled) {
        if (viewVisibility == View.GONE) {
          epoxyHolder.visibilityChanged(0f, 0f, 0, 0);
        } else {
          epoxyHolder.visibilityChanged(
              100.f / height * visibleHeight,
              100.f / width * visibleWidth,
              visibleHeight, visibleWidth
          );
        }
      }
      lastVisibleHeightNotified = visibleHeight;
      lastVisibleWidthNotified = visibleWidth;
      lastVisibilityNotified = viewVisibility;
      changed = true;
    }
    return changed;
  }

  private boolean isVisible() {
    return viewVisibility == View.VISIBLE && visibleHeight > 0 && visibleWidth > 0;
  }

  private boolean isInFocusVisible() {
    final int halfViewportArea = viewportHeight * viewportWidth / 2;
    final int totalArea = height * width;
    final int visibleArea = visibleHeight * visibleWidth;
    // The model has entered the focused range either if it is larger than half of the viewport
    // and it occupies at least half of the viewport or if it is smaller than half of the viewport
    // and it is fully visible.
    return viewVisibility == View.VISIBLE &&
        ((totalArea >= halfViewportArea)
            ? (visibleArea >= halfViewportArea)
            : totalArea == visibleArea);
  }

  private boolean isPartiallyVisible(@IntRange(from = 0, to = 100) int thresholdPercentage) {
    // special case 0%: trigger as soon as some pixels are one the screen
    if (thresholdPercentage == 0) return isVisible();

    final int totalArea = height * width;
    final int visibleArea = visibleHeight * visibleWidth;
    final float visibleAreaPercentage = (visibleArea / (float) totalArea) * 100;

    return viewVisibility == View.VISIBLE && visibleAreaPercentage >= thresholdPercentage;
  }

  private boolean isFullyVisible() {
    return viewVisibility == View.VISIBLE && visibleHeight == height && visibleWidth == width;
  }

  void shiftBy(int offsetPosition) {
    adapterPosition += offsetPosition;
  }
}
