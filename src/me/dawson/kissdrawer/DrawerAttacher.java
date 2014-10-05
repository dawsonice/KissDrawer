package me.dawson.kissdrawer;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;

public class DrawerAttacher extends ViewGroup {
	public static final String TAG = "DrawerAttacher";

	private View targetView;
	private View drawerView;
	private int targetWidth;
	private int targetHeight;

	private int drawerWidth;
	private int drawerHeight;
	private int drawerOffset;

	private ValueAnimator showAnimator;
	private ValueAnimator hideAnimator;
	private DrawerListener listener;

	private Status status;
	private DrawerMode drawerMode;

	public enum Status {
		DETACHED, ATTACHED, SHOWING, SHOWED, HIDDING, HIDDEN,
	};

	public enum DrawerMode {
		LEFT, ABOVE, RIGHT, BELOW, OVERLAY,
	};

	public static interface DrawerListener {
		public void onStatus(DrawerAttacher da, Status status);
	}

	public DrawerAttacher(Context context) {
		this(context, null);
	}

	public DrawerAttacher(Context context, AttributeSet set) {
		super(context, set);
		init();
	}

	private void init() {
		setWillNotDraw(false);
		status = Status.DETACHED;
		drawerMode = DrawerMode.ABOVE;
	}

	public void setListener(DrawerListener listener) {
		this.listener = listener;
	}

	public void setDrawerMode(DrawerMode mode) {
		if (mode == this.drawerMode) {
			return;
		}

		reset();
		this.drawerMode = mode;
	}

	public Status getStatus() {
		return this.status;
	}

	public void reset() {
		if (showAnimator != null && showAnimator.isRunning()) {
			showAnimator.cancel();
		}

		if (hideAnimator != null && hideAnimator.isRunning()) {
			hideAnimator.cancel();
		}

		if (targetView != null && drawerView != null) {
			setStatus(Status.HIDDEN);
		} else {
			setStatus(Status.DETACHED);
		}

		drawerOffset = 0;
		requestLayout();
	}

	public void detach() {
		if (targetView == null || drawerView == null) {
			return;
		}

		ViewGroup.LayoutParams params = this.getLayoutParams();
		params.width = targetWidth;
		params.height = targetHeight;
		this.removeView(drawerView);

		ViewGroup parent = (ViewGroup) this.getParent();
		int index = parent.indexOfChild(this);
		parent.removeViewAt(index);

		this.removeView(targetView);
		this.removeView(drawerView);
		drawerView = null;

		parent.addView(targetView, index, params);
		targetView = null;

		parent.invalidate();
		setStatus(Status.DETACHED);
	}

	private void setStatus(Status st) {
		if (status == st) {
			return;
		}

		status = st;
		if (this.listener != null) {
			listener.onStatus(this, status);
		}
	}

	public void attach(View target, View drawer) {
		if (target == null || drawer == null) {
			return;
		}

		if (target == targetView && drawer == drawerView) {
			Log.w(TAG, "duplicated attach!");
			return;
		}

		if (status != Status.DETACHED) {
			detach();
		}

		this.targetView = target;
		this.drawerView = drawer;

		ViewGroup.LayoutParams params = targetView.getLayoutParams();
		targetWidth = targetView.getWidth();
		targetHeight = targetView.getHeight();

		params.width = targetWidth;
		params.height = targetHeight;

		ViewGroup parent = (ViewGroup) targetView.getParent();
		int index = parent.indexOfChild(targetView);
		parent.removeViewAt(index);
		parent.addView(this, index, params);
		targetView.setVisibility(View.VISIBLE);
		drawerView.setVisibility(View.VISIBLE);

		addView(targetView);
		addView(drawerView, 0);

		drawerHeight = drawerView.getHeight();
		drawerWidth = drawerView.getWidth();
		if (drawerHeight <= 0 || drawerWidth <= 0) {
			int widthSpec = MeasureSpec.UNSPECIFIED;
			if (drawerMode == DrawerMode.ABOVE
					|| drawerMode == DrawerMode.BELOW) {
				widthSpec = MeasureSpec.makeMeasureSpec(targetWidth,
						MeasureSpec.EXACTLY);
			}
			int heightSpec = MeasureSpec.UNSPECIFIED;
			if (drawerMode == DrawerMode.LEFT || drawerMode == DrawerMode.RIGHT) {
				heightSpec = MeasureSpec.makeMeasureSpec(targetHeight,
						MeasureSpec.EXACTLY);
			}
			drawerView.measure(widthSpec, heightSpec);

			drawerHeight = drawerView.getMeasuredHeight();
			drawerWidth = drawerView.getMeasuredWidth();
		}

		parent.invalidate();
		setStatus(Status.ATTACHED);
	}

	public boolean show() {
		if ((status != Status.HIDDEN && status != Status.ATTACHED)
				|| (status == Status.SHOWED || status == Status.SHOWING)) {
			return false;
		}

		if (showAnimator != null && showAnimator.isRunning()) {
			showAnimator.cancel();
		}

		showAnimator = new ValueAnimator();
		int offset = drawerWidth;
		if (drawerMode == DrawerMode.LEFT || drawerMode == DrawerMode.RIGHT) {
			drawerHeight = targetHeight;
			offset = drawerWidth;
		} else if (drawerMode == DrawerMode.BELOW
				|| drawerMode == DrawerMode.ABOVE) {
			drawerWidth = targetWidth;
			offset = drawerHeight;
		}
		showAnimator.setIntValues(0, offset);
		showAnimator.setDuration(500);
		showAnimator.setRepeatCount(0);
		DecelerateInterpolator interpolator = new DecelerateInterpolator();
		showAnimator.setInterpolator(interpolator);
		showAnimator.addListener(al);
		showAnimator.addUpdateListener(ul);
		showAnimator.start();
		setStatus(Status.SHOWING);
		return true;
	}

	public boolean hide() {
		if (status != Status.SHOWED
				|| (status == Status.HIDDING || status == Status.HIDDEN)) {
			return false;
		}

		if (hideAnimator != null && hideAnimator.isRunning()) {
			hideAnimator.cancel();
		}

		hideAnimator = new ValueAnimator();
		hideAnimator.setIntValues(drawerOffset, 0);
		hideAnimator.setDuration(500);
		hideAnimator.setRepeatCount(0);
		AccelerateInterpolator interpolator = new AccelerateInterpolator();
		hideAnimator.setInterpolator(interpolator);
		hideAnimator.addListener(al);
		hideAnimator.addUpdateListener(ul);
		hideAnimator.start();
		setStatus(Status.HIDDING);
		return true;
	}

	private AnimatorUpdateListener ul = new AnimatorUpdateListener() {

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			drawerOffset = (Integer) animation.getAnimatedValue();
			Log.d(TAG, "onAnimationUpdate drawerOffset " + drawerOffset);
			requestLayout();
		}
	};

	private AnimatorListener al = new AnimatorListener() {

		@Override
		public void onAnimationStart(Animator animation) {

		}

		@Override
		public void onAnimationRepeat(Animator animation) {

		}

		@Override
		public void onAnimationEnd(Animator animation) {
			Log.d(TAG, "onAnimationEnd");
			if (animation == hideAnimator) {
				setStatus(Status.HIDDEN);
			} else if (animation == showAnimator) {
				setStatus(Status.SHOWED);
			}
		}

		@Override
		public void onAnimationCancel(Animator animation) {

		}
	};

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int targetLeft = 0;
		int targetTop = 0;
		int targetRight = 0;
		int targetBottom = 0;

		int drawerLeft = 0;
		int drawerTop = 0;
		int drawerRight = 0;
		int drawerBottom = 0;

		if (drawerMode == DrawerMode.LEFT) {
			targetLeft = drawerOffset;
			drawerLeft = drawerOffset - drawerWidth;
			targetTop = 0;
			drawerTop = 0;
		} else if (drawerMode == DrawerMode.ABOVE) {
			targetLeft = 0;
			targetTop = drawerOffset;
			drawerLeft = 0;
			drawerTop = drawerOffset - drawerHeight;
		} else if (drawerMode == DrawerMode.RIGHT) {
			targetLeft = 0 - drawerOffset;
			targetTop = 0;
			drawerLeft = targetWidth - drawerOffset;
			drawerTop = 0;
		} else if (drawerMode == DrawerMode.BELOW) {
			targetLeft = 0;
			targetTop = 0 - drawerOffset;
			drawerLeft = 0;
			drawerTop = targetHeight - drawerOffset;
		} else if (drawerMode == DrawerMode.OVERLAY) {

		}

		targetRight = targetLeft + targetWidth;
		targetBottom = targetTop + targetHeight;

		drawerRight = drawerLeft + drawerWidth;
		drawerBottom = drawerTop + drawerHeight;

		Log.d(TAG, "onLayout  targetTop " + targetTop + " drawerTop "
				+ drawerTop);

		drawerView.layout(drawerLeft, drawerTop, drawerRight, drawerBottom);
		targetView.layout(targetLeft, targetTop, targetRight, targetBottom);
	}
}
