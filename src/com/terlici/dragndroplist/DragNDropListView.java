package com.terlici.dragndroplist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;

public class DragNDropListView extends ListView {
	
	public static interface OnItemDragNDropListener {
		public void onItemDrag(DragNDropListView parent, View view, int position, long id);
		public void onItemDrop(DragNDropListView parent, View view, int startPosition, int endPosition, long id);
	}
	
	boolean mDragMode;

	int mStartPosition = INVALID_POSITION;
	int mDragPointOffset; //Used to adjust drag view location
	int mDragHandler = 0;
	
	ImageView mDragView;
	
	OnItemDragNDropListener mDragNDropListener;
	
	public DragNDropListView(Context context) {
		super(context);
	}
	
	public DragNDropListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public DragNDropListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	public void setOnItemDragNDropListener(OnItemDragNDropListener listener) {
		mDragNDropListener = listener;
	}
	
	public void setDragNDropAdapter(DragNDropAdapter adapter) {
		mDragHandler = adapter.getDragHandler();
		setAdapter(adapter);
	}
	
	/**
	 * If the motion event was inside a handler view.
	 *  
	 * @param ev
	 * @return true if it is a dragging move, false otherwise.
	 */
	public boolean isDrag(MotionEvent ev) {
		if (mDragMode) return true;
		if (mDragHandler == 0) return false;
		
		int x = (int)ev.getX();
		int y = (int)ev.getY();
		
		int startposition = pointToPosition(x,y);
		
		if (startposition == INVALID_POSITION) return false;
		
		int itemposition = startposition - getFirstVisiblePosition();
		View parent = getChildAt(itemposition);
		View handler = parent.findViewById(mDragHandler);
		
		if (handler == null) return false;
		
		int top = parent.getTop() + handler.getTop();
		int bottom = top + handler.getHeight();
		int left = parent.getLeft() + handler.getLeft();
		int right = left + handler.getWidth();
		
		return left <= x && x <= right && top <= y && y <= bottom;
	}
	
	public boolean isDragging() {
		return mDragMode;
	}
	
	public View getDragView() {
		return mDragView;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		final int x = (int)ev.getX();
		final int y = (int)ev.getY();
		
		
		if (action == MotionEvent.ACTION_DOWN && isDrag(ev)) {
			mDragMode = true;
		}

		if (!mDragMode) 
			return super.onTouchEvent(ev);

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mStartPosition = pointToPosition(x, y);
				
				if (mStartPosition != INVALID_POSITION) {
					int itemPosition = mStartPosition - getFirstVisiblePosition();
                    mDragPointOffset = y - getChildAt(itemPosition).getTop();
                    mDragPointOffset -= ((int)ev.getRawY()) - y;
                    
					startDrag(itemPosition, y);
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
				
				if (mStartPosition != INVALID_POSITION)
					stopDrag(mStartPosition - getFirstVisiblePosition(), pointToPosition(x,y));
				break;
		}
		
		return true;
	}
	
	/**
	 * Prepare the drag view.
	 * 
	 * @param itemIndex
	 * @param y
	 */
	private void startDrag(int itemIndex, int y) {
		View item = getChildAt(itemIndex);
		
		if (item == null) return;

		long id = getItemIdAtPosition(mStartPosition);
		if (mDragNDropListener != null)
        	mDragNDropListener.onItemDrag(this, item, mStartPosition, id);
        
        ((DragNDropAdapter)getAdapter()).onItemDrag(this, item, mStartPosition, id);

		item.setDrawingCacheEnabled(true);
		
        // Create a copy of the drawing cache so that it does not get recycled
        // by the framework when the list tries to clean up memory
        Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
        
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

        WindowManager mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(v, mWindowParams);
        mDragView = v;
        
        item.setVisibility(View.INVISIBLE);
        
        invalidateViews();
	}
	
	/**
	 * Release all dragging resources.
	 * 
	 * @param itemIndex
	 */
	private void stopDrag(int itemIndex, int endPosition) {
		if (mDragView == null) return;

		View item = getChildAt(itemIndex);
		
		if (endPosition != INVALID_POSITION) {
			long id = getItemIdAtPosition(mStartPosition);
			
			if (mDragNDropListener != null)
	        	mDragNDropListener.onItemDrop(this, item, mStartPosition, endPosition, id);
	        
	        ((DragNDropAdapter)getAdapter()).onItemDrop(this, item, mStartPosition, endPosition, id);
		}
		
        mDragView.setVisibility(GONE);
        WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.removeView(mDragView);
        
        mDragView.setImageDrawable(null);
        mDragView = null;
        
        item.setDrawingCacheEnabled(false);
        item.destroyDrawingCache();
        
        item.setVisibility(View.VISIBLE);
        
        mStartPosition = INVALID_POSITION;
        
        invalidateViews();
	}
	
	/**
	 * Move the drag view.
	 * 
	 * @param x
	 * @param y
	 */
	private void drag(int x, int y) {
		if (mDragView == null) return;

		WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams)mDragView.getLayoutParams();
		layoutParams.x = x;
		layoutParams.y = y - mDragPointOffset;
		
		WindowManager mWindowManager = (WindowManager) getContext()
				.getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.updateViewLayout(mDragView, layoutParams);
	}
}