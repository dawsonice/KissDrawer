package me.dawson.kissdrawer;

import me.dawson.kissdrawer.DrawerAttacher.DrawerListener;
import me.dawson.kissdrawer.DrawerAttacher.DrawerMode;
import me.dawson.kissdrawer.DrawerAttacher.Status;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

public class DrawerListView extends ListView {
	public static final String TAG = "DrawerListView";

	private static int WRAPPER_ID = 20140909;

	private boolean intercepted;

	private TriggerMode triggerMode;
	private DrawerAttacher drawerAttacher;
	private View drawerView;
	private boolean enableDrawer;
	private int drawerPos;

	private ListAdapter adapter;
	private OnItemClickListener clickListener;
	private OnItemLongClickListener longClickListener;

	public enum TriggerMode {
		CLICK, LONG_CLICK,
	}

	public interface InterceptListener {
		public boolean onIntercept(DrawerListView cl, MotionEvent event);
	}

	public DrawerListView(Context context) {
		this(context, null);
	}

	public DrawerListView(Context context, AttributeSet set) {
		super(context, set);
		init();
	}

	private void init() {
		super.setOnItemClickListener(realClickListener);
		super.setOnItemLongClickListener(realLongClickListener);
		super.setAdapter(realAdapter);
		this.triggerMode = TriggerMode.CLICK;
		this.enableDrawer = true;
		this.drawerPos = -1;
		drawerAttacher = new DrawerAttacher(this.getContext());
		drawerAttacher.setDrawerMode(DrawerMode.BELOW);
		drawerAttacher.setListener(drawerListener);
	}

	public void setDrawerView(View dv) {
		this.drawerView = dv;
	}

	public void setOnItemClickListener(OnItemClickListener l) {
		this.clickListener = l;
	}

	public void setOnItemLongClickListener(OnItemLongClickListener listener) {
		this.longClickListener = listener;
	}

	public void setAdapter(ListAdapter adapter) {
		this.adapter = adapter;
	}

	public int getDrawerPos() {
		return drawerPos;
	}

	public void setDrawerEnabled(boolean enable) {
		this.enableDrawer = enable;
		if (!enableDrawer) {
			hideDrawer();
		}
	}

	public void setTriggerMode(TriggerMode mode) {
		this.triggerMode = mode;
	}

	public boolean dispatchTouchEvent(MotionEvent event) {
		if (eventInView(event, drawerView)) {
			return super.dispatchTouchEvent(event);
		}

		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN && hideDrawer()) {
			intercepted = true;
			return true;
		}

		if (intercepted) {
			if (action == MotionEvent.ACTION_UP
					|| action == MotionEvent.ACTION_CANCEL) {
				intercepted = false;
			}
			return true;
		}

		return super.dispatchTouchEvent(event);
	}

	private OnItemClickListener realClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			if ((triggerMode == TriggerMode.CLICK) && triggerDrawer(view)) {
				drawerPos = position;
				return;
			}

			if (clickListener != null) {
				if (view != null && view.getId() == WRAPPER_ID) {
					RelativeLayout wrapper = (RelativeLayout) view;
					view = wrapper.getChildAt(0);
				}

				clickListener.onItemClick(parent, view, position, id);
			}
		}
	};

	private OnItemLongClickListener realLongClickListener = new OnItemLongClickListener() {

		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view,
				int position, long id) {
			if ((triggerMode == TriggerMode.LONG_CLICK) && triggerDrawer(view)) {
				drawerPos = position;
				return true;
			}

			if (longClickListener != null) {
				if (view != null && view.getId() == WRAPPER_ID) {
					RelativeLayout wrapper = (RelativeLayout) view;
					view = wrapper.getChildAt(0);
				}

				longClickListener.onItemLongClick(parent, view, position, id);
			}

			return true;
		}

	};

	private boolean triggerDrawer(View view) {
		if (!enableDrawer) {
			return false;
		}

		if (view == null || view.getId() != WRAPPER_ID) {
			return false;
		}

		RelativeLayout wrapper = (RelativeLayout) view;
		View target = wrapper.getChildAt(0);
		showDrawer(target);
		return true;
	}

	private ListAdapter realAdapter = new BaseAdapter() {

		@Override
		public int getCount() {
			if (adapter != null) {
				return adapter.getCount();
			}
			return 0;
		}

		@Override
		public Object getItem(int position) {
			if (adapter != null) {
				return adapter.getItem(position);
			}
			return null;
		}

		@Override
		public long getItemId(int position) {
			if (adapter != null) {
				return adapter.getItemId(position);
			}
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (adapter == null) {
				return null;
			}

			if (convertView != null && convertView.getId() == WRAPPER_ID) {
				RelativeLayout rl = (RelativeLayout) convertView;
				convertView = rl.getChildAt(0);
			}

			View view = adapter.getView(position, convertView, parent);
			if (view == null) {
				return null;
			}

			ViewGroup wrapper = new RelativeLayout(getContext());
			wrapper.setId(WRAPPER_ID);
			wrapper.addView(view);

			return wrapper;
		}

	};

	// removeViewAt(int) is not supported in AdapterView
	private void showDrawer(View target) {
		drawerAttacher.attach(target, drawerView);
		drawerAttacher.show();
	}

	private boolean hideDrawer() {
		int first = getFirstVisiblePosition();
		int last = getLastVisiblePosition();

		int size = last - first;
		for (int index = 0; index <= size; ++index) {
			View child = getChildAt(index);
			if (child == null || child.getId() != WRAPPER_ID) {
				continue;
			}

			RelativeLayout wrapper = (RelativeLayout) child;
			View content = wrapper.getChildAt(0);
			if (!(content instanceof DrawerAttacher)) {
				continue;
			}

			DrawerAttacher da = (DrawerAttacher) content;
			if (da.hide()) {
				drawerPos = -1;
				return true;
			}
		}
		return false;
	}

	private boolean eventInView(MotionEvent event, View view) {
		if (event == null || view == null) {
			return false;
		}

		int eventX = (int) event.getRawX();
		int eventY = (int) event.getRawY();

		int[] location = new int[2];
		view.getLocationOnScreen(location);

		int width = view.getWidth();
		int height = view.getHeight();
		int left = location[0];
		int top = location[1];
		int right = left + width;
		int bottom = top + height;

		Rect rect = new Rect(left, top, right, bottom);
		boolean contains = rect.contains(eventX, eventY);
		return contains;
	}

	private DrawerListener drawerListener = new DrawerListener() {

		@Override
		public void onStatus(DrawerAttacher da, Status status) {
			if (status == Status.HIDDEN) {
				da.detach();
			}
		}
	};
}
