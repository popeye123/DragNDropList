/*
 * Copyright 2012 Terlici Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terlici.dragndroplist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.WrapperListAdapter;

public class DragNDropListView extends ListView {

    public interface OnItemDragNDropListener {
        void onItemDrag(DragNDropListView parent, View view, int position, long id);

        void onItemDrop(DragNDropListView parent, View view, int startPosition, int endPosition, long id);
    }

    private boolean mDragMode;
    private boolean isDraggingEnabled = true;
    private WindowManager mWm;
    private int mStartPosition = INVALID_POSITION;
    private int mDragPointOffset; // Used to adjust drag view location
    private int mDragHandler = 0;
    private View mDragItem;
    private ImageView mDragView;
    private OnItemDragNDropListener mDragNDropListener;

    private void init() {
        mWm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
    }

    public DragNDropListView(Context context) {
        super(context);
        init();
    }

    public DragNDropListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DragNDropListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setOnItemDragNDropListener(OnItemDragNDropListener listener) {
        mDragNDropListener = listener;
    }

    public void setDragNDropAdapter(DragNDropAdapter adapter) {
        mDragHandler = adapter.getDragHandler();
        setAdapter(adapter);
    }

    /**
     * Check if the given motion event was inside a handler view.
     *
     * @param ev the motion event
     * @return true if it is a dragging move, false otherwise.
     */
    public boolean isDrag(MotionEvent ev) {
        if (mDragMode) return true;
        if (mDragHandler == 0) return false;

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        int startPosition = pointToPosition(x, y);

        if (startPosition == INVALID_POSITION) return false;

        int childPosition = startPosition - getFirstVisiblePosition();
        View parent = getChildAt(childPosition);
        View handler = parent.findViewById(mDragHandler);

        if (handler == null) return false;

        int top = parent.getTop() + handler.getTop();
        int bottom = top + handler.getHeight();
        int left = parent.getLeft() + handler.getLeft();
        int right = left + handler.getWidth();

        return left <= x && x <= right && top <= y && y <= bottom;
    }

    /**
     * Checks whether or not a dragging action is active.
     *
     * @return true if a dragging action is active
     */
    public boolean isDragging() {
        return mDragMode;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();

        if (action == MotionEvent.ACTION_DOWN && isDrag(ev)) {
            mDragMode = true;
        }

        if (!mDragMode || !isDraggingEnabled) {
            return super.onTouchEvent(ev);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mStartPosition = pointToPosition(x, y);

                if (mStartPosition != INVALID_POSITION) {
                    int viewIndex = mStartPosition - getFirstVisiblePosition();
                    mDragPointOffset = y - getChildAt(viewIndex).getTop();
                    mDragPointOffset -= ((int) ev.getRawY()) - y;

                    startDrag(viewIndex, y);
                    drag(0, y);
                }

                break;
            case MotionEvent.ACTION_MOVE:
                drag(0, y);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            default:
                mDragMode = false;

                if (mStartPosition != INVALID_POSITION) {
                    // check if the position is a header/footer
                    int actualPosition = pointToPosition(x, y);

                    if (actualPosition > (getCount() - getFooterViewsCount()) - 1) {
                        actualPosition = INVALID_POSITION;
                    }

                    stopDrag(actualPosition);
                }
                break;
        }

        return true;
    }

    /**
     * Prepare the drag view.
     *
     * @param viewIndex the index from top of actually visible items
     * @param y         the y coordinate of the MotionEvent that starts the drag action
     */
    private void startDrag(int viewIndex, int y) {
        mDragItem = getChildAt(viewIndex);

        if (mDragItem == null) {
            return;
        }

        long id = getItemIdAtPosition(mStartPosition);

        if (mDragNDropListener != null) {
            mDragNDropListener.onItemDrag(this, mDragItem, mStartPosition, id);
        }

        Adapter adapter = getAdapter();
        DragNDropAdapter dndAdapter;

        // if exists a footer/header we have our adapter wrapped
        if (adapter instanceof WrapperListAdapter) {
            dndAdapter = (DragNDropAdapter) ((WrapperListAdapter) adapter).getWrappedAdapter();
        } else {
            dndAdapter = (DragNDropAdapter) adapter;
        }

        dndAdapter.onItemDrag(this, mDragItem, mStartPosition, id);

        mDragItem.setDrawingCacheEnabled(true);

        // Create a copy of the drawing cache so that it does not get recycled
        // by the framework when the list tries to clean up memory
        Bitmap bitmap = Bitmap.createBitmap(mDragItem.getDrawingCache());

        WindowManager.LayoutParams mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP;
        mWindowParams.x = 0;
        mWindowParams.y = y - mDragPointOffset;

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;

        Context context = getContext();
        ImageView v = new ImageView(context);
        v.setImageBitmap(bitmap);

        mWm.addView(v, mWindowParams);
        mDragView = v;

        mDragItem.setVisibility(View.INVISIBLE);
        mDragItem.invalidate(); // We have not changed anything else.
    }

    /**
     * Release all dragging resources.
     *
     * @param endPosition the position in the adapter of the dropped item
     */
    private void stopDrag(int endPosition) {
        if (mDragView == null) {
            return;
        }

        if (endPosition != INVALID_POSITION) {
            long id = getItemIdAtPosition(mStartPosition);

            if (mDragNDropListener != null) {
                mDragNDropListener.onItemDrop(this, mDragItem, mStartPosition, endPosition, id);
            }

            Adapter adapter = getAdapter();
            DragNDropAdapter dndAdapter;

            // if exists a footer/header we have our adapter wrapped
            if (adapter instanceof WrapperListAdapter) {
                dndAdapter = (DragNDropAdapter) ((WrapperListAdapter) adapter).getWrappedAdapter();
            } else {
                dndAdapter = (DragNDropAdapter) adapter;
            }

            dndAdapter.onItemDrop(this, mDragItem, mStartPosition, endPosition, id);
        }

        mDragView.setVisibility(GONE);
        mWm.removeView(mDragView);

        mDragView.setImageDrawable(null);
        mDragView = null;

        mDragItem.setDrawingCacheEnabled(false);
        mDragItem.destroyDrawingCache();

        mDragItem.setVisibility(View.VISIBLE);

        mStartPosition = INVALID_POSITION;
        mDragItem = null;

        invalidateViews(); // We have changed the adapter data, so change everything
    }

    /**
     * Move the drag view.
     *
     * @param x x coordinate of the motion event that triggered the drag update
     * @param y y coordinate of the motion event that triggered the drag update
     */
    private void drag(int x, int y) {
        if (mDragView == null) {
            return;
        }

        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) mDragView.getLayoutParams();
        layoutParams.x = x;
        layoutParams.y = y - mDragPointOffset;
        int top = getTop();
        int bottom = getBottom();
        double beginScrollOffset = getHeight() / 3;

        mWm.updateViewLayout(mDragView, layoutParams);

        if (y < top + beginScrollOffset) {
            int distance = (int) (100 - (Math.abs(top - y) / beginScrollOffset) * 100);
            smoothScrollBy(-distance, 7);
        } else if (y > bottom - beginScrollOffset) {
            int distance = (int) (100 - (Math.abs(bottom - y) / beginScrollOffset) * 100);
            smoothScrollBy(distance, 7);
        }
    }

    /**
     * Enables or disables dragging.
     *
     * @param draggingEnabled true to enable dragging, false to disable
     */
    public void setDraggingEnabled(boolean draggingEnabled) {
        this.isDraggingEnabled = draggingEnabled;
    }
}
