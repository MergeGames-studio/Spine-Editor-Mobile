package com.bandjou_mergegames.spineeditormobile.editor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.content.ContextCompat;

import com.bandjou_mergegames.spineeditormobile.R;
import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TimelineView extends View {
    private static final int MODE_NONE = 0;
    private static final int MODE_SCRUB = 1;
    private static final int MODE_VERTICAL = 2;
    private static final int MODE_LABEL = 3;

    private final Paint backgroundPaint = new Paint();
    private final Paint rulerBackgroundPaint = new Paint();
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedRowPaint = new Paint();
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keySelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skinKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path diamondPath = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final float touchSlop;
    private final float labelWidth;
    private final float rulerHeight;
    private final float rowHeight;

    private EditorView editor;
    private float scrollY;
    private int mode = MODE_NONE;
    private float downX;
    private float downY;
    private float startScrollY;
    private int downRow = -1;
    private int downTrackRow = -1;
    private float downKeyTime = -1f;
    private final List<String> rowBones = new ArrayList<>();

    public TimelineView(Context context) {
        this(context, null);
    }

    public TimelineView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimelineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        labelWidth = dp(94f);
        rulerHeight = dp(22f);
        rowHeight = dp(27f);
        initPaints();
    }

    private void initPaints() {
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.panel));
        rulerBackgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.panel2));
        tickPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        tickPaint.setStrokeWidth(dp(1f));
        rowPaint.setColor(0x1FFFFFFF);
        rowPaint.setStrokeWidth(dp(1f));
        selectedRowPaint.setColor(0x1A35D0BA);
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text));
        labelPaint.setTextSize(dp(10.5f));
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timePaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        timePaint.setTextSize(dp(9f));
        keyPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        keySelectedPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));
        skinKeyPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent));
        playheadPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent));
        playheadPaint.setStrokeWidth(dp(1.5f));
        keyRingPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent));
        keyRingPaint.setStyle(Paint.Style.STROKE);
        keyRingPaint.setStrokeWidth(dp(1.5f));
        emptyPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        emptyPaint.setTextSize(dp(11f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEditor(EditorView editor) {
        this.editor = editor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        canvas.drawRect(0, 0, getWidth(), rulerHeight, rulerBackgroundPaint);
        if (editor == null || editor.getProject() == null) {
            return;
        }
        Project project = editor.getProject();
        Animation animation = editor.getActiveAnimationObject();
        if (animation == null) {
            canvas.drawText(getContext().getString(R.string.timeline_no_animation),
                    getWidth() / 2f, getHeight() / 2f, emptyPaint);
            return;
        }

        buildRows(project, animation);
        float trackLeft = labelWidth;
        float trackRight = getWidth() - dp(12f);
        float trackWidth = Math.max(10f, trackRight - trackLeft);
        float duration = Math.max(0.1f, animation.duration);
        clampScroll();

        drawRuler(canvas, trackLeft, trackWidth, duration);
        drawRows(canvas, project, animation, trackLeft, trackWidth, duration);

        float playheadX = trackLeft + (editor.getTime() / duration) * trackWidth;
        playheadX = Math.max(trackLeft, Math.min(trackRight, playheadX));
        canvas.drawLine(playheadX, 0, playheadX, getHeight(), playheadPaint);
        diamondPath.reset();
        diamondPath.moveTo(playheadX, dp(2f));
        diamondPath.lineTo(playheadX + dp(5f), dp(8f));
        diamondPath.lineTo(playheadX, dp(14f));
        diamondPath.lineTo(playheadX - dp(5f), dp(8f));
        diamondPath.close();
        canvas.drawPath(diamondPath, playheadPaint);

        canvas.drawLine(labelWidth, 0, labelWidth, getHeight(), rowPaint);
    }

    private void buildRows(Project project, Animation animation) {
        rowBones.clear();
        for (Bone bone : project.bones) {
            List<Animation.BoneKey> keys = animation.keys(bone.name);
            boolean hasKeys = keys != null && !keys.isEmpty();
            if (hasKeys || bone.name.equals(editor.getSelectedBoneName())) {
                rowBones.add(bone.name);
            }
        }
    }

    private void drawRuler(Canvas canvas, float trackLeft, float trackWidth, float duration) {
        float[] steps = {0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 20f};
        float step = steps[steps.length - 1];
        for (float candidate : steps) {
            if (candidate / duration * trackWidth >= dp(44f)) {
                step = candidate;
                break;
            }
        }
        for (float t = 0f; t <= duration + 0.0001f; t += step) {
            float x = trackLeft + (t / duration) * trackWidth;
            if (x > getWidth() - dp(2f)) {
                break;
            }
            tickPaint.setColor(ContextCompat.getColor(getContext(), R.color.axis));
            canvas.drawLine(x, rulerHeight - dp(5f), x, rulerHeight, tickPaint);
            tickPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
            String label = String.format(Locale.US, "%.1fs", t);
            canvas.drawText(label, x + dp(3f), rulerHeight - dp(7f), timePaint);
        }
        tickPaint.setColor(ContextCompat.getColor(getContext(), R.color.axis));
        canvas.drawLine(0, rulerHeight, getWidth(), rulerHeight, tickPaint);
    }

    private void drawSkinRow(Canvas canvas, Animation animation, float trackLeft, float trackWidth,
                             float duration) {
        float rowTop = rulerHeight - scrollY;
        float rowBottom = rowTop + rowHeight;
        if (rowBottom < rulerHeight || rowTop > getHeight()) {
            return;
        }
        boolean keyAtTime = animation.skinKeyAt(editor.getTime()) != null;
        if (keyAtTime) {
            canvas.drawRect(0, rowTop, getWidth(), rowBottom, selectedRowPaint);
        }
        canvas.drawLine(labelWidth, rowBottom, getWidth(), rowBottom, rowPaint);
        labelPaint.setColor(keyAtTime
                ? ContextCompat.getColor(getContext(), R.color.accent)
                : ContextCompat.getColor(getContext(), R.color.text));
        canvas.drawText(ellipsize(getContext().getString(R.string.skin_keys), labelWidth - dp(18f)),
                dp(10f), rowTop + rowHeight / 2f + dp(3.5f), labelPaint);
        for (Animation.SkinKey key : animation.skinKeys) {
            if (key == null) {
                continue;
            }
            float x = trackLeft + (key.time / duration) * trackWidth;
            float y = rowTop + rowHeight / 2f;
            if (x < trackLeft - dp(6f) || x > getWidth() + dp(6f)) {
                continue;
            }
            diamondPath.reset();
            diamondPath.moveTo(x, y - dp(5f));
            diamondPath.lineTo(x + dp(5f), y);
            diamondPath.lineTo(x, y + dp(5f));
            diamondPath.lineTo(x - dp(5f), y);
            diamondPath.close();
            canvas.drawPath(diamondPath, skinKeyPaint);
            if (Math.abs(editor.getTime() - key.time) < 0.01f) {
                canvas.drawCircle(x, y, dp(8f), keyRingPaint);
            }
        }
    }

    private void drawRows(Canvas canvas, Project project, Animation animation, float trackLeft,
                          float trackWidth, float duration) {
        int save = canvas.save();
        canvas.clipRect(0, rulerHeight, getWidth(), getHeight());
        drawSkinRow(canvas, animation, trackLeft, trackWidth, duration);
        for (int i = 0; i < rowBones.size(); i++) {
            String boneName = rowBones.get(i);
            float rowTop = rulerHeight + (i + 1) * rowHeight - scrollY;
            float rowBottom = rowTop + rowHeight;
            if (rowBottom < rulerHeight || rowTop > getHeight()) {
                continue;
            }
            boolean selected = boneName.equals(editor.getSelectedBoneName());
            if (selected) {
                canvas.drawRect(0, rowTop, getWidth(), rowBottom, selectedRowPaint);
            }
            canvas.drawLine(labelWidth, rowBottom, getWidth(), rowBottom, rowPaint);
            Bone bone = project.findBone(boneName);
            String label = bone == null ? boneName : bone.name;
            labelPaint.setColor(selected
                    ? ContextCompat.getColor(getContext(), R.color.selection)
                    : ContextCompat.getColor(getContext(), R.color.text));
            canvas.drawText(ellipsize(label, labelWidth - dp(18f)), dp(10f),
                    rowTop + rowHeight / 2f + dp(3.5f), labelPaint);

            List<Animation.BoneKey> keys = animation.keys(boneName);
            if (keys == null) {
                continue;
            }
            for (Animation.BoneKey key : keys) {
                float x = trackLeft + (key.time / duration) * trackWidth;
                float y = rowTop + rowHeight / 2f;
                if (x < trackLeft - dp(6f) || x > getWidth() + dp(6f)) {
                    continue;
                }
                diamondPath.reset();
                diamondPath.moveTo(x, y - dp(5f));
                diamondPath.lineTo(x + dp(5f), y);
                diamondPath.lineTo(x, y + dp(5f));
                diamondPath.lineTo(x - dp(5f), y);
                diamondPath.close();
                canvas.drawPath(diamondPath, selected ? keySelectedPaint : keyPaint);
                if (Math.abs(editor.getTime() - key.time) < 0.01f) {
                    canvas.drawCircle(x, y, dp(8f), keyRingPaint);
                }
            }
        }
        canvas.restoreToCount(save);
    }

    private String ellipsize(String text, float maxWidth) {
        if (labelPaint.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && labelPaint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private void clampScroll() {
        float contentHeight = (rowBones.size() + 1) * rowHeight;
        float available = getHeight() - rulerHeight;
        float maxScroll = Math.max(0f, contentHeight - available);
        scrollY = Math.max(0f, Math.min(scrollY, maxScroll));
    }

    private float xForTime(float time, float duration) {
        float trackLeft = labelWidth;
        float trackRight = getWidth() - dp(12f);
        float trackWidth = Math.max(10f, trackRight - trackLeft);
        return trackLeft + (time / Math.max(0.1f, duration)) * trackWidth;
    }

    private int rowAt(float y) {
        if (y < rulerHeight) {
            return -1;
        }
        float contentY = y - rulerHeight + scrollY;
        int index = (int) (contentY / rowHeight);
        if (index < 0 || index >= rowBones.size() + 1) {
            return -1;
        }
        return index;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editor == null || editor.getActiveAnimationObject() == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                downX = event.getX();
                downY = event.getY();
                startScrollY = scrollY;
                downRow = rowAt(downY);
                downTrackRow = -1;
                downKeyTime = -1f;
                if (downY < rulerHeight) {
                    mode = MODE_SCRUB;
                    seek(event.getX());
                } else if (event.getX() < labelWidth) {
                    mode = MODE_LABEL;
                } else {
                    mode = MODE_SCRUB;
                    int row = downRow;
                    Animation animation = editor.getActiveAnimationObject();
                    if (row == 0 && animation != null) {
                        float duration = Math.max(0.1f, animation.duration);
                        for (Animation.SkinKey key : animation.skinKeys) {
                            if (key != null && Math.abs(event.getX() - xForTime(key.time, duration))
                                    <= dp(13f)) {
                                downKeyTime = key.time;
                                break;
                            }
                        }
                    } else if (row > 0 && animation != null) {
                        String boneName = rowBones.get(row - 1);
                        float duration = Math.max(0.1f, animation.duration);
                        List<Animation.BoneKey> keys = animation.keys(boneName);
                        if (keys != null) {
                            for (Animation.BoneKey key : keys) {
                                if (Math.abs(event.getX() - xForTime(key.time, duration)) <= dp(13f)) {
                                    downKeyTime = key.time;
                                    break;
                                }
                            }
                        }
                    }
                    seek(downKeyTime >= 0f ? xForTime(downKeyTime,
                            Math.max(0.1f, editor.getActiveAnimationObject().duration)) : event.getX());
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_VERTICAL) {
                    scrollY = startScrollY - (event.getY() - downY);
                    clampScroll();
                    invalidate();
                } else if (mode == MODE_SCRUB) {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dy > touchSlop && dy > dx && event.getX() >= labelWidth) {
                        mode = MODE_VERTICAL;
                        startScrollY = scrollY;
                        downY = event.getY();
                    } else {
                        seek(event.getX());
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mode == MODE_LABEL) {
                    int row = downRow;
                    if (row == 0 && getContext() instanceof Activity) {
                        EditorDialogs.showSkinsDialog((Activity) getContext(), editor);
                    } else if (row > 0 && row <= rowBones.size()) {
                        editor.selectBone(rowBones.get(row - 1));
                    }
                }
                mode = MODE_NONE;
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void seek(float x) {
        if (editor == null) {
            return;
        }
        Animation animation = editor.getActiveAnimationObject();
        if (animation == null) {
            return;
        }
        float duration = Math.max(0.1f, animation.duration);
        float trackLeft = labelWidth;
        float trackRight = getWidth() - dp(12f);
        float trackWidth = Math.max(10f, trackRight - trackLeft);
        float t = (x - trackLeft) / trackWidth * duration;
        t = Math.max(0f, Math.min(duration, t));
        editor.setTime(t);
        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * density;
    }
}