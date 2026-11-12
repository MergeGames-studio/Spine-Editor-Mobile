package com.bandjou_mergegames.spineeditormobile.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.bandjou_mergegames.spineeditormobile.R;
import com.bandjou_mergegames.spineeditormobile.data.ProjectCodec;
import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Pose;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Slot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EditorView extends View {
    public static final int TOOL_SELECT = 0;
    public static final int TOOL_BONE = 1;
    public static final int TOOL_SHAPE = 2;
    public static final int TOOL_MOVE = 3;
    public static final int TOOL_ROTATE = 4;
    public static final int TOOL_SCALE = 5;
    public static final int TOOL_PAN = 6;

    public interface Listener {
        void onSelectionChanged();

        void onProjectModified();

        void onTimeChanged();

        void onPlayStateChanged();

        void onAnimationChanged();

        void onToolChanged();
    }

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_PAN_PENDING = 1;
    private static final int GESTURE_PAN = 2;
    private static final int GESTURE_PINCH = 3;
    private static final int GESTURE_CREATE_BONE = 4;
    private static final int GESTURE_CREATE_SHAPE = 5;
    private static final int GESTURE_MOVE = 6;
    private static final int GESTURE_ROTATE = 7;
    private static final int GESTURE_SCALE = 8;

    private static final int[] BONE_COLORS = {
            0xFF35D0BA, 0xFF64B5F6, 0xFFFFB74D, 0xFFBA68C8,
            0xFF4DD0E1, 0xFFAED581, 0xFFE57373, 0xFF9FA8DA
    };

    private static final int[] SHAPE_COLORS = {
            0xFF35D0BA, 0xFF64B5F6, 0xFFFFB74D, 0xFFBA68C8,
            0xFF4DD0E1, 0xFFE57373, 0xFFAED581, 0xFFFFF176,
            0xFF80CBC4, 0xFFF48FB1
    };

    private final Paint backgroundPaint = new Paint();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boneSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attachmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attachmentStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private final float touchSlop;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private String pendingUndo;

    private Project project;
    private Listener listener;
    private int tool = TOOL_SELECT;
    private String selectedBone;
    private String selectedSlot;
    private float time;
    private boolean playing;
    private boolean pendingInitialFit;
    private float manualSkinTime = Float.NaN;

    private float camX;
    private float camY;
    private float zoom = 1f;

    private float downScreenX;
    private float downScreenY;
    private float lastScreenX;
    private float lastScreenY;
    private float downWorldX;
    private float downWorldY;
    private float lastWorldX;
    private float lastWorldY;
    private boolean moved;

    private int gesture = GESTURE_NONE;
    private String targetBone;
    private float createStartWorldX;
    private float createStartWorldY;
    private float createCurrentWorldX;
    private float createCurrentWorldY;

    private float startLocalX;
    private float startLocalY;
    private float startLocalRotation;
    private float startScaleX;
    private float startScaleY;
    private float startTouchAngle;
    private float startParentAngle;
    private float startParentScaleX = 1f;
    private float startParentScaleY = 1f;

    private float shapeStartLocalX;
    private float shapeStartLocalY;
    private float shapeCurrentLocalX;
    private float shapeCurrentLocalY;
    private boolean shapeDragged;

    private float pinchDistance;
    private float pinchFocusX;
    private float pinchFocusY;

    private long lastFrameNanos;
    private Pose lastPose;
    private final java.util.HashMap<String, Bitmap> imageCache = new java.util.HashMap<>();

    private final Runnable playbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing || project == null) {
                return;
            }
            Animation animation = getActiveAnimationObject();
            if (animation == null) {
                playing = false;
                notifyPlayState();
                return;
            }
            long now = System.nanoTime();
            float delta = (now - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = now;
            delta = Math.min(delta, 0.1f);
            time += delta;
            if (time >= animation.duration) {
                if (animation.loop) {
                    time = time % Math.max(0.1f, animation.duration);
                } else {
                    time = animation.duration;
                    playing = false;
                    notifyPlayState();
                }
            }
            manualSkinTime = Float.NaN;
            applyTimelineSkin();
            if (listener != null) {
                listener.onTimeChanged();
            }
            invalidate();
            if (playing) {
                postOnAnimation(this);
            }
        }
    };

    public EditorView(Context context) {
        this(context, null);
    }

    public EditorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setClickable(true);
        initPaints();
    }

    private void initPaints() {
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.bg));
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.grid));
        gridPaint.setStrokeWidth(dp(1f));
        axisPaint.setColor(ContextCompat.getColor(getContext(), R.color.axis));
        axisPaint.setStrokeWidth(dp(1.4f));

        bonePaint.setStyle(Paint.Style.STROKE);
        bonePaint.setStrokeWidth(dp(3f));
        bonePaint.setStrokeCap(Paint.Cap.ROUND);
        boneSelectedPaint.setStyle(Paint.Style.STROKE);
        boneSelectedPaint.setStrokeWidth(dp(5f));
        boneSelectedPaint.setStrokeCap(Paint.Cap.ROUND);
        boneSelectedPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));

        jointPaint.setStyle(Paint.Style.FILL);
        jointPaint.setColor(ContextCompat.getColor(getContext(), R.color.panel));
        jointStrokePaint.setStyle(Paint.Style.STROKE);
        jointStrokePaint.setStrokeWidth(dp(1.5f));

        attachmentPaint.setStyle(Paint.Style.FILL);
        attachmentStrokePaint.setStyle(Paint.Style.STROKE);
        attachmentStrokePaint.setStrokeWidth(dp(1.2f));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(2.5f));
        selectionPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));

        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.text));
        textPaint.setTextSize(dp(11f));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        smallTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        smallTextPaint.setTextSize(dp(9f));
        smallTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        hintTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text));
        hintTextPaint.setTextSize(dp(10.5f));
        hintTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        hintBackgroundPaint.setColor(0xCC11161D);

        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeWidth(dp(2f));
        previewPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));
        previewPaint.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));
        previewFillPaint.setStyle(Paint.Style.FILL);
        previewFillPaint.setColor(0x3335D0BA);

        emptyTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        emptyTextPaint.setTextSize(dp(14f));
        emptyTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project, boolean resetView) {
        pausePlayback();
        this.project = project;
        if (this.project != null) {
            this.project.ensureRoot();
        }
        undoStack.clear();
        redoStack.clear();
        pendingUndo = null;
        time = 0f;
        manualSkinTime = Float.NaN;
        playing = false;
        selectedSlot = null;
        selectedBone = this.project == null || this.project.bones.isEmpty() ? null
                : this.project.bones.get(0).name;
        pendingInitialFit = resetView;
        applyTimelineSkin();
        if (resetView && getWidth() > 0 && getHeight() > 0) {
            fitView();
        }
        invalidate();
        notifySelection();
        notifyProjectModified();
        notifyAnimationChanged();
        notifyTimeChanged();
        notifyPlayState();
        notifyToolChanged();
    }

    public void putImage(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) {
            return;
        }
        imageCache.put(key, bitmap);
        invalidate();
    }

    public Bitmap getImage(String key) {
        if (key == null) {
            return null;
        }
        return imageCache.get(key);
    }

    private void restoreFromUndo(Project next) {
        pausePlayback();
        String keepBone = selectedBone;
        String keepSlot = selectedSlot;
        float keepTime = time;
        project = next;
        project.ensureRoot();
        selectedBone = project.findBone(keepBone) != null ? keepBone
                : (project.bones.isEmpty() ? null : project.bones.get(0).name);
        selectedSlot = project.findSlot(keepSlot) != null ? keepSlot : null;
        if (project.findAnimation(project.activeAnimation) == null) {
            project.activeAnimation = null;
        }
        time = Math.min(keepTime, getDuration());
        manualSkinTime = Float.NaN;
        applyTimelineSkin();
        pendingUndo = null;
        invalidate();
        notifySelection();
        notifyProjectModified();
        notifyAnimationChanged();
        notifyTimeChanged();
        notifyPlayState();
    }

    public String getSelectedBoneName() {
        return selectedBone;
    }

    public String getSelectedSlotName() {
        return selectedSlot;
    }

    public String getSelectedAttachmentName() {
        Slot slot = project == null ? null : project.findSlot(selectedSlot);
        return slot == null ? null : slot.attachment;
    }

    public int getTool() {
        return tool;
    }

    public void setTool(int tool) {
        if (this.tool == tool) {
            return;
        }
        finishGesture(false);
        this.tool = tool;
        invalidate();
        if (listener != null) {
            listener.onToolChanged();
        }
    }

    public void selectBone(String boneName) {
        if (boneName != null && (project == null || project.findBone(boneName) == null)) {
            boneName = null;
        }
        selectedBone = boneName;
        if (selectedSlot != null) {
            Slot slot = project == null ? null : project.findSlot(selectedSlot);
            if (slot == null || selectedBone == null || !slot.bone.equals(selectedBone)) {
                selectedSlot = null;
            }
        }
        invalidate();
        notifySelection();
    }

    public void selectSlot(String slotName) {
        Slot slot = project == null ? null : project.findSlot(slotName);
        if (slot == null) {
            selectedSlot = null;
        } else {
            selectedSlot = slot.name;
            selectedBone = slot.bone;
        }
        invalidate();
        notifySelection();
    }

    public Animation getActiveAnimationObject() {
        return project == null ? null : project.findAnimation(project.activeAnimation);
    }

    public void setActiveAnimation(String name) {
        if (project == null) {
            return;
        }
        if (name != null && project.findAnimation(name) == null) {
            name = null;
        }
        pausePlayback();
        project.activeAnimation = name;
        time = 0f;
        manualSkinTime = Float.NaN;
        applyTimelineSkin();
        invalidate();
        notifyAnimationChanged();
        notifyTimeChanged();
        notifyProjectModified();
    }

    public float getTime() {
        return time;
    }

    public void setTime(float newTime) {
        pausePlayback();
        float duration = getDuration();
        time = Math.max(0f, Math.min(newTime, duration));
        manualSkinTime = Float.NaN;
        applyTimelineSkin();
        invalidate();
        notifyTimeChanged();
    }

    private void setPlaybackTime(float newTime) {
        time = Math.max(0f, Math.min(newTime, getDuration()));
        manualSkinTime = Float.NaN;
        applyTimelineSkin();
        invalidate();
    }

    public float getDuration() {
        Animation animation = getActiveAnimationObject();
        return animation == null ? 0f : Math.max(0.1f, animation.duration);
    }

    public boolean isPlaying() {
        return playing;
    }

    public void playPause() {
        if (playing) {
            pausePlayback();
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            showMessage(getContext().getString(R.string.msg_select_animation));
            return;
        }
        if (time >= animation.duration - 0.001f) {
            time = 0f;
        }
        playing = true;
        lastFrameNanos = System.nanoTime();
        postOnAnimation(playbackRunnable);
        notifyPlayState();
        invalidate();
    }

    public void pausePlayback() {
        if (!playing) {
            return;
        }
        playing = false;
        removeCallbacks(playbackRunnable);
        notifyPlayState();
        invalidate();
    }

    public boolean isLoop() {
        Animation animation = getActiveAnimationObject();
        return animation == null || animation.loop;
    }

    public void setLoop(boolean loop) {
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            return;
        }
        beginEdit();
        animation.loop = loop;
        commitEdit();
    }

    public boolean hasKeyAtPlayhead() {
        if (selectedBone == null) {
            return false;
        }
        Animation animation = getActiveAnimationObject();
        return animation != null && animation.keyAt(selectedBone, time) != null;
    }

    public void addKeyForSelected() {
        if (selectedBone == null) {
            showMessage(getContext().getString(R.string.msg_select_bone));
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            showMessage(getContext().getString(R.string.msg_select_animation));
            return;
        }
        Pose pose = buildPose();
        Pose.Local local = pose.local(selectedBone);
        if (local == null) {
            return;
        }
        beginEdit();
        animation.setKey(selectedBone, new Animation.BoneKey(time, local.x, local.y, local.rotation,
                local.scaleX, local.scaleY));
        if (time > animation.duration) {
            animation.duration = time;
        }
        commitEdit();
        showMessage(getContext().getString(R.string.msg_key_added));
        notifyProjectModified();
        notifyTimeChanged();
        invalidate();
    }

    public void deleteKeyForSelected() {
        if (selectedBone == null) {
            showMessage(getContext().getString(R.string.msg_select_bone));
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            showMessage(getContext().getString(R.string.msg_select_animation));
            return;
        }
        beginEdit();
        boolean removed = animation.removeKey(selectedBone, time);
        if (removed) {
            commitEdit();
            showMessage(getContext().getString(R.string.msg_key_deleted));
            notifyProjectModified();
            notifyTimeChanged();
            invalidate();
        } else {
            cancelEdit();
            showMessage(getContext().getString(R.string.msg_no_key));
        }
    }

    public boolean hasSkinKeyAtPlayhead() {
        Animation animation = getActiveAnimationObject();
        return animation != null && animation.skinKeyAt(time) != null;
    }

    public void addSkinKeyForCurrentSkin() {
        if (project == null) {
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            showMessage(getContext().getString(R.string.msg_select_animation));
            return;
        }
        project.ensureDefaultSkin();
        String skinName = project.activeSkin;
        if (skinName == null || project.findSkin(skinName) == null) {
            showMessage(getContext().getString(R.string.msg_select_skin));
            return;
        }
        beginEdit();
        animation.setSkinKey(new Animation.SkinKey(time, skinName));
        if (time > animation.duration) {
            animation.duration = time;
        }
        manualSkinTime = Float.NaN;
        commitEdit();
        showMessage(getContext().getString(R.string.msg_skin_key_added));
        notifyProjectModified();
        notifyTimeChanged();
        invalidate();
    }

    public void deleteSkinKeyAtPlayhead() {
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            showMessage(getContext().getString(R.string.msg_select_animation));
            return;
        }
        beginEdit();
        boolean removed = animation.removeSkinKey(time);
        if (removed) {
            manualSkinTime = Float.NaN;
            commitEdit();
            showMessage(getContext().getString(R.string.msg_skin_key_deleted));
            notifyProjectModified();
            notifyTimeChanged();
            invalidate();
        } else {
            cancelEdit();
            showMessage(getContext().getString(R.string.msg_no_skin_key));
        }
    }

    public void addRootBone() {
        if (project == null) {
            return;
        }
        beginEdit();
        Bone bone = new Bone(uniqueBoneName("bone"), null);
        bone.rotation = 90f;
        bone.length = 80f;
        bone.color = BONE_COLORS[project.bones.size() % BONE_COLORS.length];
        project.bones.add(bone);
        selectedBone = bone.name;
        selectedSlot = null;
        commitEdit();
        notifySelection();
        invalidate();
    }

    public void addChildBone() {
        if (project == null) {
            return;
        }
        if (selectedBone == null) {
            addRootBone();
            return;
        }
        Bone parent = project.findBone(selectedBone);
        if (parent == null) {
            addRootBone();
            return;
        }
        beginEdit();
        Bone bone = new Bone(uniqueBoneName("bone"), parent.name);
        bone.x = parent.length;
        bone.y = 0f;
        bone.rotation = 0f;
        bone.length = 50f;
        bone.color = BONE_COLORS[project.bones.size() % BONE_COLORS.length];
        project.bones.add(bone);
        selectedBone = bone.name;
        selectedSlot = null;
        commitEdit();
        notifySelection();
        invalidate();
    }

    public void moveSlot(String slotName, int delta) {
        if (project == null || slotName == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < project.slots.size(); i++) {
            if (slotName.equals(project.slots.get(i).name)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int target = index + delta;
        if (target < 0 || target >= project.slots.size()) {
            return;
        }
        beginEdit();
        com.bandjou_mergegames.spineeditormobile.model.Slot slot = project.slots.remove(index);
        project.slots.add(target, slot);
        commitEdit();
        invalidate();
    }

    public boolean addAnimation(String name, float duration) {
        if (project == null) {
            return false;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (Animation animation : project.animations) {
            if (animation.name.equalsIgnoreCase(trimmed)) {
                showMessage(getContext().getString(R.string.msg_animation_exists));
                return false;
            }
        }
        beginEdit();
        Animation animation = new Animation(trimmed, Math.max(0.1f, Math.min(60f, duration)));
        animation.loop = true;
        project.animations.add(animation);
        project.activeAnimation = animation.name;
        time = 0f;
        commitEdit();
        notifyAnimationChanged();
        notifyTimeChanged();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public void deleteActiveAnimation() {
        if (project == null) {
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null) {
            return;
        }
        beginEdit();
        project.animations.remove(animation);
        project.activeAnimation = project.animations.isEmpty() ? null : project.animations.get(0).name;
        time = 0f;
        commitEdit();
        notifyAnimationChanged();
        notifyTimeChanged();
        notifyProjectModified();
        invalidate();
    }

    public void deleteSelected() {
        if (selectedSlot != null) {
            deleteSlot(selectedSlot);
        } else if (selectedBone != null) {
            deleteBoneRecursive(selectedBone);
        }
    }

    public void deleteSlot(String slotName) {
        if (project == null) {
            return;
        }
        Slot slot = project.findSlot(slotName);
        if (slot == null) {
            return;
        }
        beginEdit();
        project.slots.remove(slot);
        project.attachments.removeIf(attachment -> slotName.equals(attachment.slot));
        if (slotName.equals(selectedSlot)) {
            selectedSlot = null;
        }
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
    }

    public void deleteBoneRecursive(String boneName) {
        if (project == null) {
            return;
        }
        Bone bone = project.findBone(boneName);
        if (bone == null) {
            return;
        }
        beginEdit();
        Set<String> removeBones = new HashSet<>();
        removeBones.add(bone.name);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Bone candidate : project.bones) {
                if (candidate.parent != null && removeBones.contains(candidate.parent)
                        && removeBones.add(candidate.name)) {
                    changed = true;
                }
            }
        }
        project.bones.removeIf(candidate -> removeBones.contains(candidate.name));
        Set<String> removeSlots = new HashSet<>();
        for (Slot slot : project.slots) {
            if (removeBones.contains(slot.bone)) {
                removeSlots.add(slot.name);
            }
        }
        project.slots.removeIf(slot -> removeSlots.contains(slot.name));
        project.attachments.removeIf(attachment -> removeSlots.contains(attachment.slot));
        for (Animation animation : project.animations) {
            for (String removed : removeBones) {
                animation.bones.remove(removed);
            }
        }
        if (project.bones.isEmpty()) {
            project.ensureRoot();
        }
        if (removeBones.contains(selectedBone) || selectedBone == null) {
            selectedBone = project.bones.isEmpty() ? null : project.bones.get(0).name;
        }
        if (selectedSlot != null && project.findSlot(selectedSlot) == null) {
            selectedSlot = null;
        }
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
    }

    public boolean applyBoneProperties(String oldName, String newName, float x, float y, float rotation,
                                       float scaleX, float scaleY, float length) {
        if (project == null) {
            return false;
        }
        Bone bone = project.findBone(oldName);
        if (bone == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            showMessage(getContext().getString(R.string.msg_name_empty));
            return false;
        }
        Bone duplicate = project.findBone(trimmed);
        if (duplicate != null && duplicate != bone) {
            showMessage(getContext().getString(R.string.msg_name_exists));
            return false;
        }
        beginEdit();
        if (!trimmed.equals(oldName)) {
            bone.name = trimmed;
            for (Bone child : project.bones) {
                if (oldName.equals(child.parent)) {
                    child.parent = trimmed;
                }
            }
            for (Slot slot : project.slots) {
                if (oldName.equals(slot.bone)) {
                    slot.bone = trimmed;
                }
            }
            for (Animation animation : project.animations) {
                ArrayList<Animation.BoneKey> keys = animation.bones.remove(oldName);
                if (keys != null) {
                    animation.bones.put(trimmed, keys);
                }
            }
            if (oldName.equals(selectedBone)) {
                selectedBone = trimmed;
            }
        }
        bone.x = x;
        bone.y = y;
        bone.rotation = rotation;
        bone.scaleX = Math.max(0.001f, scaleX);
        bone.scaleY = Math.max(0.001f, scaleY);
        bone.length = Math.max(0f, length);
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public boolean applySlotProperties(String slotName, String boneName, boolean slotVisible,
                                       String shape, float x, float y, float rotation, float width,
                                       float height, int color, boolean attachmentVisible) {
        if (project == null) {
            return false;
        }
        Slot slot = project.findSlot(slotName);
        if (slot == null) {
            return false;
        }
        Attachment attachment = project.attachmentForSlot(slot);
        if (attachment == null) {
            return false;
        }
        if (project.findBone(boneName) == null) {
            showMessage(getContext().getString(R.string.msg_select_bone));
            return false;
        }
        beginEdit();
        slot.bone = boneName;
        slot.visible = slotVisible;
        attachment.shape = shape;
        attachment.x = x;
        attachment.y = y;
        attachment.rotation = rotation;
        attachment.width = Math.max(1f, width);
        attachment.height = Math.max(1f, height);
        attachment.color = color;
        attachment.visible = attachmentVisible;
        project.syncCurrentSlotsToActiveSkin();
        if (slotName.equals(selectedSlot)) {
            selectedBone = boneName;
        }
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public boolean setSlotAttachment(String slotName, String attachmentName) {
        if (project == null) {
            return false;
        }
        Slot slot = project.findSlot(slotName);
        if (slot == null) {
            return false;
        }
        if (attachmentName != null && project.findAttachment(attachmentName) == null) {
            return false;
        }
        beginEdit();
        project.setSlotAttachmentForActiveSkin(slotName, attachmentName);
        if (slotName.equals(selectedSlot)) {
            selectedBone = slot.bone;
        }
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public String duplicateAttachmentForSlot(String slotName) {
        if (project == null) {
            return null;
        }
        Slot slot = project.findSlot(slotName);
        if (slot == null) {
            return null;
        }
        Attachment src = project.attachmentForSlot(slot);
        if (src == null) {
            return null;
        }
        beginEdit();
        Attachment copy = src.copy();
        copy.name = uniqueAttachmentName(src.name + "_" + (project.activeSkin == null ? "skin" : project.activeSkin));
        copy.slot = slot.name;
        project.attachments.add(copy);
        project.setSlotAttachmentForActiveSkin(slot.name, copy.name);
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
        return copy.name;
    }

    public boolean switchSkin(String skinName) {
        if (project == null || project.findSkin(skinName) == null) {
            return false;
        }
        beginEdit();
        project.setActiveSkin(skinName);
        manualSkinTime = time;
        Animation animation = getActiveAnimationObject();
        if (animation != null) {
            Animation.SkinKey key = animation.skinKeyAt(time);
            if (key != null) {
                key.skin = skinName;
            }
        }
        commitEdit();
        notifySelection();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public boolean addSkin(String skinName) {
        if (project == null) {
            return false;
        }
        String name = skinName == null ? "" : skinName.trim();
        if (name.isEmpty()) {
            showMessage(getContext().getString(R.string.msg_name_empty));
            return false;
        }
        if (project.findSkin(name) != null) {
            showMessage(getContext().getString(R.string.msg_skin_exists));
            return false;
        }
        beginEdit();
        project.syncCurrentSlotsToActiveSkin();
        com.bandjou_mergegames.spineeditormobile.model.Skin current = project.getActiveSkin();
        com.bandjou_mergegames.spineeditormobile.model.Skin skin = new com.bandjou_mergegames.spineeditormobile.model.Skin(name);
        if (current != null) {
            skin.attachments.putAll(current.attachments);
        }
        project.skins.add(skin);
        project.activeSkin = name;
        commitEdit();
        notifyProjectModified();
        invalidate();
        return true;
    }

    public String getActiveSkinName() {
        return project == null ? null : project.activeSkin;
    }

    public boolean undo() {
        if (undoStack.isEmpty() || project == null) {
            return false;
        }
        redoStack.push(ProjectCodec.toJson(project));
        try {
            Project restored = ProjectCodec.fromJson(undoStack.pop());
            restoreFromUndo(restored);
            return true;
        } catch (Exception exception) {
            showMessage("Undo failed");
            return false;
        }
    }

    public boolean redo() {
        if (redoStack.isEmpty() || project == null) {
            return false;
        }
        undoStack.push(ProjectCodec.toJson(project));
        try {
            Project restored = ProjectCodec.fromJson(redoStack.pop());
            restoreFromUndo(restored);
            return true;
        } catch (Exception exception) {
            showMessage("Redo failed");
            return false;
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void beginEdit() {
        if (pendingUndo == null && project != null) {
            pendingUndo = ProjectCodec.toJson(project);
        }
    }

    public void commitEdit() {
        if (pendingUndo == null || project == null) {
            return;
        }
        String current = ProjectCodec.toJson(project);
        if (!current.equals(pendingUndo)) {
            undoStack.push(pendingUndo);
            while (undoStack.size() > 60) {
                undoStack.removeLast();
            }
            redoStack.clear();
            pendingUndo = null;
            if (listener != null) {
                listener.onProjectModified();
            }
        } else {
            pendingUndo = null;
        }
    }

    public void cancelEdit() {
        pendingUndo = null;
    }

    public void fitView() {
        if (project == null) {
            return;
        }
        if (getWidth() == 0 || getHeight() == 0) {
            pendingInitialFit = true;
            return;
        }
        Pose pose = buildPose();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;
        for (Bone bone : project.bones) {
            Pose.World world = pose.world(bone.name);
            if (world == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            float tipX = world.x + bone.length * world.scaleX * (float) Math.cos(Math.toRadians(world.rotation));
            float tipY = world.y + bone.length * world.scaleX * (float) Math.sin(Math.toRadians(world.rotation));
            minX = Math.min(minX, tipX);
            minY = Math.min(minY, tipY);
            maxX = Math.max(maxX, tipX);
            maxY = Math.max(maxY, tipY);
        }
        for (Slot slot : project.slots) {
            Attachment attachment = project.attachmentForSlot(slot);
            if (attachment == null) {
                continue;
            }
            Pose.World world = pose.world(slot.bone);
            if (world == null) {
                continue;
            }
            float[] points = attachmentWorldPoints(attachment, world);
            for (int i = 0; i < points.length; i += 2) {
                found = true;
                minX = Math.min(minX, points[i]);
                minY = Math.min(minY, points[i + 1]);
                maxX = Math.max(maxX, points[i]);
                maxY = Math.max(maxY, points[i + 1]);
            }
        }
        if (!found) {
            minX = -project.width / 2f;
            maxX = project.width / 2f;
            minY = -project.height / 2f;
            maxY = project.height / 2f;
        }
        float width = Math.max(20f, maxX - minX);
        float height = Math.max(20f, maxY - minY);
        float availableWidth = Math.max(100f, getWidth() - dp(90f));
        float availableHeight = Math.max(100f, getHeight() - dp(120f));
        zoom = Math.max(0.01f, Math.min(12f, Math.min(availableWidth / width, availableHeight / height)));
        camX = (minX + maxX) / 2f;
        camY = (minY + maxY) / 2f;
        pendingInitialFit = false;
        invalidate();
    }

    public void zoomBy(float factor) {
        zoom = Math.max(0.02f, Math.min(20f, zoom * factor));
        invalidate();
    }

    public Bitmap exportBitmap() {
        if (getWidth() <= 0 || getHeight() <= 0 || project == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        renderScene(canvas, false);
        return bitmap;
    }

    public int getBoneCount() {
        return project == null ? 0 : project.bones.size();
    }

    private Pose buildPose() {
        applyTimelineSkin();
        return Pose.build(project, getActiveAnimationObject(), time);
    }

    private void applyTimelineSkin() {
        if (project == null) {
            return;
        }
        if (!Float.isNaN(manualSkinTime) && Math.abs(manualSkinTime - time) < 0.001f) {
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation == null || animation.skinKeys.isEmpty()) {
            return;
        }
        String skinName = animation.skinAt(time);
        if (skinName == null || project.findSkin(skinName) == null) {
            return;
        }
        if (!skinName.equals(project.activeSkin)) {
            project.setActiveSkin(skinName);
        }
    }

    private void applySelectedLocal(float x, float y, float rotation, float scaleX, float scaleY) {
        if (project == null || selectedBone == null) {
            return;
        }
        Animation animation = getActiveAnimationObject();
        if (animation != null) {
            animation.setKey(selectedBone, new Animation.BoneKey(time, x, y, rotation,
                    Math.max(0.001f, scaleX), Math.max(0.001f, scaleY)));
        } else {
            Bone bone = project.findBone(selectedBone);
            if (bone == null) {
                return;
            }
            bone.x = x;
            bone.y = y;
            bone.rotation = rotation;
            bone.scaleX = Math.max(0.001f, scaleX);
            bone.scaleY = Math.max(0.001f, scaleY);
        }
        invalidate();
        if (listener != null) {
            listener.onProjectModified();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        renderScene(canvas, true);
    }

    private void renderScene(Canvas canvas, boolean interactive) {
        canvas.drawColor(backgroundPaint.getColor());
        if (project == null) {
            return;
        }
        lastPose = buildPose();
        drawGrid(canvas);
        drawAttachments(canvas, lastPose, interactive);
        drawBones(canvas, lastPose, interactive);
        if (interactive) {
            drawGesturePreview(canvas, lastPose);
            drawOverlay(canvas);
        }
        if (project.bones.isEmpty()) {
            canvas.drawText(getContext().getString(R.string.canvas_empty), getWidth() / 2f,
                    getHeight() / 2f, emptyTextPaint);
        }
    }

    private void drawGrid(Canvas canvas) {
        float left = toWorldX(0f);
        float right = toWorldX(getWidth());
        float top = toWorldY(0f);
        float bottom = toWorldY(getHeight());
        float step = 50f;
        while (step * zoom < dp(36f)) {
            step *= 2f;
        }
        while (step * zoom > dp(110f) && step > 1f) {
            step /= 2f;
        }
        float startX = (float) (Math.floor(left / step) * step);
        float startY = (float) (Math.floor(bottom / step) * step);
        int guard = 0;
        for (float worldX = startX; worldX <= right && guard < 400; worldX += step, guard++) {
            float screenX = toScreenX(worldX);
            canvas.drawLine(screenX, 0f, screenX, getHeight(), gridPaint);
        }
        guard = 0;
        for (float worldY = startY; worldY <= top && guard < 400; worldY += step, guard++) {
            float screenY = toScreenY(worldY);
            canvas.drawLine(0f, screenY, getWidth(), screenY, gridPaint);
        }
        if (left < 0f && right > 0f) {
            float x = toScreenX(0f);
            canvas.drawLine(x, 0f, x, getHeight(), axisPaint);
        }
        if (bottom < 0f && top > 0f) {
            float y = toScreenY(0f);
            canvas.drawLine(0f, y, getWidth(), y, axisPaint);
        }
    }

    private void drawBones(Canvas canvas, Pose pose, boolean interactive) {
        for (Bone bone : project.bones) {
            if (!bone.visible) {
                continue;
            }
            Pose.World world = pose.world(bone.name);
            if (world == null) {
                continue;
            }
            boolean selected = interactive && bone.name.equals(selectedBone);
            float jointX = toScreenX(world.x);
            float jointY = toScreenY(world.y);
            double radians = Math.toRadians(world.rotation);
            float dirX = (float) Math.cos(radians);
            float dirY = (float) -Math.sin(radians);
            float tipWorldX = world.x + bone.length * world.scaleX * dirX;
            float tipWorldY = world.y + bone.length * world.scaleX * (float) Math.sin(radians);
            float tipX = toScreenX(tipWorldX);
            float tipY = toScreenY(tipWorldY);

            if (selected) {
                boneSelectedPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));
                canvas.drawLine(jointX, jointY, tipX, tipY, boneSelectedPaint);
            }
            bonePaint.setColor(bone.color);
            canvas.drawLine(jointX, jointY, tipX, tipY, bonePaint);

            if (bone.length > 0.01f) {
                float screenDx = tipX - jointX;
                float screenDy = tipY - jointY;
                float length = (float) Math.hypot(screenDx, screenDy);
                if (length > 1f) {
                    float nx = screenDx / length;
                    float ny = screenDy / length;
                    float px = -ny;
                    float py = nx;
                    path.reset();
                    path.moveTo(tipX, tipY);
                    path.lineTo(tipX - nx * dp(9f) + px * dp(4f), tipY - ny * dp(9f) + py * dp(4f));
                    path.lineTo(tipX - nx * dp(9f) - px * dp(4f), tipY - ny * dp(9f) - py * dp(4f));
                    path.close();
                    Paint fill = selected ? boneSelectedPaint : bonePaint;
                    fill.setStyle(Paint.Style.FILL);
                    canvas.drawPath(path, fill);
                    fill.setStyle(Paint.Style.STROKE);
                }
            }

            jointPaint.setColor(selected
                    ? ContextCompat.getColor(getContext(), R.color.selection)
                    : ContextCompat.getColor(getContext(), R.color.panel));
            jointStrokePaint.setColor(selected
                    ? ContextCompat.getColor(getContext(), R.color.selection)
                    : bone.color);
            float jointRadius = selected ? dp(5.5f) : dp(4.5f);
            canvas.drawCircle(jointX, jointY, jointRadius, jointPaint);
            canvas.drawCircle(jointX, jointY, jointRadius, jointStrokePaint);

            String label = bone.name;
            if (selected) {
                smallTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));
            } else {
                smallTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
            }
            float midX = (jointX + tipX) / 2f + dp(6f);
            float midY = (jointY + tipY) / 2f - dp(4f);
            canvas.drawText(label, midX, midY, smallTextPaint);
        }
    }

    private void drawAttachments(Canvas canvas, Pose pose, boolean interactive) {
        java.util.List<Slot> slotsToDraw = new java.util.ArrayList<>();
        if (project != null) {
            slotsToDraw.addAll(project.slots);
        }
        Animation anim = null;
        if (project != null && project.activeAnimation != null) {
            anim = project.findAnimation(project.activeAnimation);
        }
        if (anim != null && anim.drawOrder != null && !anim.drawOrder.isEmpty() && time >= 0f) {
            Animation.DrawOrderKey key = null;
            for (Animation.DrawOrderKey k : anim.drawOrder) {
                if (k == null) {
                    continue;
                }
                if (k.time <= time + 0.0001f) {
                    key = k;
                } else {
                    break;
                }
            }
            if (key != null && key.offsets != null && !key.offsets.isEmpty()) {
                java.util.LinkedHashMap<String, Slot> map = new java.util.LinkedHashMap<>();
                for (Slot slot : slotsToDraw) {
                    if (slot != null) {
                        map.put(slot.name, slot);
                    }
                }
                java.util.ArrayList<Slot> ordered = new java.util.ArrayList<>(slotsToDraw);
                for (com.bandjou_mergegames.spineeditormobile.model.Animation.DrawOrderOffset off : key.offsets) {
                    if (off == null || off.slot == null) {
                        continue;
                    }
                    Slot slot = map.get(off.slot);
                    if (slot == null) {
                        continue;
                    }
                    ordered.remove(slot);
                    int idx = slotsToDraw.indexOf(slot) + off.offset;
                    if (idx < 0) {
                        idx = 0;
                    }
                    if (idx > ordered.size()) {
                        idx = ordered.size();
                    }
                    ordered.add(idx, slot);
                }
                slotsToDraw = ordered;
            }
        }
        for (Slot slot : slotsToDraw) {
            if (!slot.visible) {
                continue;
            }
            Attachment attachment = project.attachmentForSlot(slot);
            if (attachment == null || !attachment.visible) {
                continue;
            }
            Pose.World world = pose.world(slot.bone);
            if (world == null) {
                continue;
            }
            String key = attachment.region != null ? attachment.region : attachment.path;
            Bitmap image = key == null ? null : imageCache.get(key);
            if (image != null && !image.isRecycled()) {
                drawAttachmentImage(canvas, attachment, world, image, slot.name.equals(selectedSlot) && interactive);
                continue;
            }
            float[] screenPoints = attachmentScreenPoints(attachment, world);
            if (screenPoints.length < 6) {
                continue;
            }
            buildPath(screenPoints);
            attachmentPaint.setColor(attachment.color);
            attachmentPaint.setAlpha(220);
            canvas.drawPath(path, attachmentPaint);
            attachmentStrokePaint.setColor(lighten(attachment.color, 0.35f));
            attachmentStrokePaint.setAlpha(235);
            canvas.drawPath(path, attachmentStrokePaint);

            if (interactive && slot.name.equals(selectedSlot)) {
                canvas.drawPath(path, selectionPaint);
            }
        }
    }

    private float[] attachmentScreenPoints(Attachment attachment, Pose.World world) {
        float[] worldPoints = attachmentWorldPoints(attachment, world);
        float[] screenPoints = new float[worldPoints.length];
        for (int i = 0; i < worldPoints.length; i += 2) {
            screenPoints[i] = toScreenX(worldPoints[i]);
            screenPoints[i + 1] = toScreenY(worldPoints[i + 1]);
        }
        return screenPoints;
    }

    private float[] attachmentWorldPoints(Attachment attachment, Pose.World world) {
        float width = Math.max(1f, Math.abs(attachment.width));
        float height = Math.max(1f, Math.abs(attachment.height));
        int count;
        String shape = attachment.shape == null ? Attachment.SHAPE_RECT : attachment.shape;
        switch (shape) {
            case Attachment.SHAPE_TRIANGLE:
                count = 3;
                break;
            case Attachment.SHAPE_ELLIPSE:
                count = 40;
                break;
            case Attachment.SHAPE_RECT:
            default:
                count = 4;
                break;
        }
        float[] points = new float[count * 2];
        double attachmentRadians = Math.toRadians(attachment.rotation);
        double boneRadians = Math.toRadians(world.rotation);
        float attachmentCos = (float) Math.cos(attachmentRadians);
        float attachmentSin = (float) Math.sin(attachmentRadians);
        float boneCos = (float) Math.cos(boneRadians);
        float boneSin = (float) Math.sin(boneRadians);
        float scaleX = world.scaleX;
        float scaleY = world.scaleY;
        if (Math.abs(scaleX) < 0.0001f) {
            scaleX = 1f;
        }
        if (Math.abs(scaleY) < 0.0001f) {
            scaleY = 1f;
        }
        for (int i = 0; i < count; i++) {
            float localX;
            float localY;
            if (Attachment.SHAPE_ELLIPSE.equals(shape)) {
                double angle = Math.PI * 2.0 * i / count;
                localX = (float) Math.cos(angle) * width / 2f;
                localY = (float) Math.sin(angle) * height / 2f;
            } else if (Attachment.SHAPE_TRIANGLE.equals(shape)) {
                if (i == 0) {
                    localX = 0f;
                    localY = -height / 2f;
                } else if (i == 1) {
                    localX = width / 2f;
                    localY = height / 2f;
                } else {
                    localX = -width / 2f;
                    localY = height / 2f;
                }
            } else {
                if (i == 0) {
                    localX = -width / 2f;
                    localY = -height / 2f;
                } else if (i == 1) {
                    localX = width / 2f;
                    localY = -height / 2f;
                } else if (i == 2) {
                    localX = width / 2f;
                    localY = height / 2f;
                } else {
                    localX = -width / 2f;
                    localY = height / 2f;
                }
            }
            float rotatedX = localX * attachmentCos - localY * attachmentSin + attachment.x;
            float rotatedY = localX * attachmentSin + localY * attachmentCos + attachment.y;
            float worldX = world.x + rotatedX * scaleX * boneCos - rotatedY * scaleY * boneSin;
            float worldY = world.y + rotatedX * scaleX * boneSin + rotatedY * scaleY * boneCos;
            points[i * 2] = worldX;
            points[i * 2 + 1] = worldY;
        }
        return points;
    }

    private void buildPath(float[] screenPoints) {
        path.reset();
        path.moveTo(screenPoints[0], screenPoints[1]);
        for (int i = 2; i < screenPoints.length; i += 2) {
            path.lineTo(screenPoints[i], screenPoints[i + 1]);
        }
        path.close();
    }

    private void drawGesturePreview(Canvas canvas, Pose pose) {
        if (gesture == GESTURE_CREATE_BONE) {
            float startX = toScreenX(createStartWorldX);
            float startY = toScreenY(createStartWorldY);
            float currentX = toScreenX(createCurrentWorldX);
            float currentY = toScreenY(createCurrentWorldY);
            canvas.drawLine(startX, startY, currentX, currentY, previewPaint);
            canvas.drawCircle(startX, startY, dp(5f), previewFillPaint);
            canvas.drawCircle(currentX, currentY, dp(5f), previewFillPaint);
            float worldLength = (float) Math.hypot(createCurrentWorldX - createStartWorldX,
                    createCurrentWorldY - createStartWorldY);
            if (worldLength > 0.1f) {
                String label = String.format(Locale.US, "%.0f", worldLength);
                hintTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection));
                canvas.drawText(label, currentX + dp(10f), currentY - dp(8f), hintTextPaint);
            }
        } else if (gesture == GESTURE_CREATE_SHAPE && targetBone != null) {
            Pose.World world = pose.world(targetBone);
            if (world == null) {
                return;
            }
            Attachment preview = new Attachment("preview", null);
            preview.shape = Attachment.SHAPE_RECT;
            preview.color = ContextCompat.getColor(getContext(), R.color.accent);
            if (shapeDragged) {
                preview.x = (shapeStartLocalX + shapeCurrentLocalX) / 2f;
                preview.y = (shapeStartLocalY + shapeCurrentLocalY) / 2f;
                preview.width = Math.max(4f, Math.abs(shapeCurrentLocalX - shapeStartLocalX));
                preview.height = Math.max(4f, Math.abs(shapeCurrentLocalY - shapeStartLocalY));
            } else {
                preview.x = shapeStartLocalX;
                preview.y = shapeStartLocalY;
                preview.width = 60f;
                preview.height = 50f;
            }
            float[] screenPoints = attachmentScreenPoints(preview, world);
            buildPath(screenPoints);
            canvas.drawPath(path, previewFillPaint);
            canvas.drawPath(path, previewPaint);
        }
    }

    private void drawOverlay(Canvas canvas) {
        String[] names = getResources().getStringArray(R.array.tool_names);
        String[] hints = getResources().getStringArray(R.array.tool_hints);
        int index = Math.max(0, Math.min(tool, names.length - 1));
        String title = names[index];
        String hint = hints[index];
        float padding = dp(10f);
        float titleSize = dp(11f);
        float hintSize = dp(10f);
        hintTextPaint.setTextSize(titleSize);
        float width = Math.max(hintTextPaint.measureText(title), measure(hint, hintSize)) + padding * 2f;
        float height = titleSize + hintSize + padding * 2f + dp(8f);
        float left = dp(10f);
        float top = dp(10f);
        hintBackgroundPaint.setColor(0xCC11161D);
        canvas.drawRoundRect(left, top, left + width, top + height, dp(10f), dp(10f), hintBackgroundPaint);
        hintTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text));
        hintTextPaint.setTextSize(titleSize);
        canvas.drawText(title, left + padding, top + padding + titleSize, hintTextPaint);
        hintTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        hintTextPaint.setTextSize(hintSize);
        canvas.drawText(hint, left + padding, top + padding + titleSize + dp(8f) + hintSize, hintTextPaint);
        hintTextPaint.setTextSize(dp(10.5f));

        String status = String.format(Locale.US, "%s · %d%% · %s: %d",
                getContext().getString(R.string.canvas_zoom), Math.round(zoom * 100f),
                getContext().getString(R.string.canvas_bones), getBoneCount());
        smallTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_dim));
        smallTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(status, getWidth() - dp(12f), getHeight() - dp(10f), smallTextPaint);
        smallTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAttachmentImage(Canvas canvas, Attachment attachment, Pose.World world,
                                     Bitmap image, boolean selected) {
        float attachmentRadians = (float) Math.toRadians(world.rotation + attachment.rotation);
        float boneRadians = (float) Math.toRadians(world.rotation);
        float attachmentCos = (float) Math.cos(attachmentRadians);
        float attachmentSin = (float) Math.sin(attachmentRadians);
        float boneCos = (float) Math.cos(boneRadians);
        float boneSin = (float) Math.sin(boneRadians);
        float scaleX = world.scaleX;
        float scaleY = world.scaleY;
        float width = Math.max(1f, Math.abs(attachment.width));
        float height = Math.max(1f, Math.abs(attachment.height));
        float sx = width / image.getWidth();
        float sy = height / image.getHeight();
        float localX = attachment.x;
        float localY = attachment.y;
        float rotatedX = localX * attachmentCos - localY * attachmentSin;
        float rotatedY = localX * attachmentSin + localY * attachmentCos;
        float worldX = world.x + rotatedX * scaleX * boneCos - rotatedY * scaleY * boneSin;
        float worldY = world.y + rotatedX * scaleX * boneSin + rotatedY * scaleY * boneCos;
        float screenX = toScreenX(worldX);
        float screenY = toScreenY(worldY);
        float screenW = width * scaleX * zoom;
        float screenH = height * scaleY * zoom;
        canvas.save();
        canvas.translate(screenX, screenY);
        canvas.rotate(-(world.rotation + attachment.rotation));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (selected) {
            paint.setAlpha(255);
        } else {
            paint.setAlpha(230);
        }
        RectF dst = new RectF(-screenW / 2f, -screenH / 2f, screenW / 2f, screenH / 2f);
        canvas.drawBitmap(image, new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), dst, paint);
        if (selected) {
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(ContextCompat.getColor(getContext(), R.color.selection));
            canvas.drawRect(dst, stroke);
        }
        canvas.restore();
    }

    private float measure(String text, float size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        return paint.measureText(text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (project == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pausePlayback();
                downScreenX = event.getX();
                downScreenY = event.getY();
                lastScreenX = downScreenX;
                lastScreenY = downScreenY;
                downWorldX = toWorldX(downScreenX);
                downWorldY = toWorldY(downScreenY);
                lastWorldX = downWorldX;
                lastWorldY = downWorldY;
                moved = false;
                handleGestureDown(downScreenX, downScreenY);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    startPinch(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    handlePinch(event);
                } else {
                    handleSingleMove(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (gesture == GESTURE_PINCH) {
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastScreenX = event.getX(remaining);
                    lastScreenY = event.getY(remaining);
                    gesture = GESTURE_PAN;
                }
                return true;
            case MotionEvent.ACTION_UP:
                finishGesture(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishGesture(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleGestureDown(float x, float y) {
        float worldX = toWorldX(x);
        float worldY = toWorldY(y);
        switch (tool) {
            case TOOL_SELECT:
                gesture = GESTURE_PAN_PENDING;
                break;
            case TOOL_PAN:
                gesture = GESTURE_PAN;
                break;
            case TOOL_BONE:
                gesture = GESTURE_CREATE_BONE;
                targetBone = selectedBone;
                createStartWorldX = worldX;
                createStartWorldY = worldY;
                createCurrentWorldX = worldX;
                createCurrentWorldY = worldY;
                beginEdit();
                break;
            case TOOL_SHAPE:
                if (selectedBone == null) {
                    showMessage(getContext().getString(R.string.msg_select_bone));
                    gesture = GESTURE_NONE;
                    return;
                }
                gesture = GESTURE_CREATE_SHAPE;
                targetBone = selectedBone;
                shapeDragged = false;
                shapeStartLocalX = worldToLocalX(selectedBone, worldX, worldY);
                shapeStartLocalY = worldToLocalY(selectedBone, worldX, worldY);
                shapeCurrentLocalX = shapeStartLocalX;
                shapeCurrentLocalY = shapeStartLocalY;
                beginEdit();
                break;
            case TOOL_MOVE:
            case TOOL_ROTATE:
            case TOOL_SCALE:
                if (selectedBone == null) {
                    showMessage(getContext().getString(R.string.msg_select_bone));
                    gesture = GESTURE_NONE;
                    return;
                }
                Pose pose = buildPose();
                Pose.Local local = pose.local(selectedBone);
                Pose.World world = pose.world(selectedBone);
                Bone bone = project.findBone(selectedBone);
                if (local == null || world == null || bone == null) {
                    gesture = GESTURE_NONE;
                    return;
                }
                startLocalX = local.x;
                startLocalY = local.y;
                startLocalRotation = local.rotation;
                startScaleX = local.scaleX;
                startScaleY = local.scaleY;
                startParentAngle = 0f;
                startParentScaleX = 1f;
                startParentScaleY = 1f;
                if (bone.parent != null) {
                    Pose.World parentWorld = pose.world(bone.parent);
                    if (parentWorld != null) {
                        startParentAngle = parentWorld.rotation;
                        startParentScaleX = Math.abs(parentWorld.scaleX) < 0.0001f ? 1f : parentWorld.scaleX;
                        startParentScaleY = Math.abs(parentWorld.scaleY) < 0.0001f ? 1f : parentWorld.scaleY;
                    }
                }
                startTouchAngle = (float) Math.toDegrees(Math.atan2(worldY - world.y, worldX - world.x));
                if (tool == TOOL_MOVE) {
                    gesture = GESTURE_MOVE;
                } else if (tool == TOOL_ROTATE) {
                    gesture = GESTURE_ROTATE;
                } else {
                    gesture = GESTURE_SCALE;
                }
                beginEdit();
                break;
            default:
                gesture = GESTURE_NONE;
                break;
        }
    }

    private void handleSingleMove(float x, float y) {
        float dx = x - lastScreenX;
        float dy = y - lastScreenY;
        if (!moved && Math.hypot(x - downScreenX, y - downScreenY) > touchSlop) {
            moved = true;
            if (gesture == GESTURE_PAN_PENDING) {
                gesture = GESTURE_PAN;
                dx = x - downScreenX;
                dy = y - downScreenY;
            }
        }
        switch (gesture) {
            case GESTURE_PAN_PENDING:
                break;
            case GESTURE_PAN:
                camX -= dx / zoom;
                camY += dy / zoom;
                invalidate();
                break;
            case GESTURE_CREATE_BONE:
                createCurrentWorldX = toWorldX(x);
                createCurrentWorldY = toWorldY(y);
                invalidate();
                break;
            case GESTURE_CREATE_SHAPE:
                shapeDragged = moved;
                shapeCurrentLocalX = worldToLocalX(targetBone, toWorldX(x), toWorldY(y));
                shapeCurrentLocalY = worldToLocalY(targetBone, toWorldX(x), toWorldY(y));
                invalidate();
                break;
            case GESTURE_MOVE: {
                float worldX = toWorldX(x);
                float worldY = toWorldY(y);
                float worldDx = worldX - downWorldX;
                float worldDy = worldY - downWorldY;
                float cos = (float) Math.cos(Math.toRadians(-startParentAngle));
                float sin = (float) Math.sin(Math.toRadians(-startParentAngle));
                float localDx = (worldDx * cos - worldDy * sin) / startParentScaleX;
                float localDy = (worldDx * sin + worldDy * cos) / startParentScaleY;
                applySelectedLocal(startLocalX + localDx, startLocalY + localDy,
                        startLocalRotation, startScaleX, startScaleY);
                break;
            }
            case GESTURE_ROTATE: {
                Pose pose = buildPose();
                Pose.World world = pose.world(selectedBone);
                if (world == null) {
                    break;
                }
                float angle = (float) Math.toDegrees(Math.atan2(toWorldY(y) - world.y, toWorldX(x) - world.x));
                float delta = normalizeAngle(angle - startTouchAngle);
                applySelectedLocal(startLocalX, startLocalY, startLocalRotation + delta,
                        startScaleX, startScaleY);
                break;
            }
            case GESTURE_SCALE: {
                float fx = 1f + (x - downScreenX) / (240f * density);
                float fy = 1f - (y - downScreenY) / (240f * density);
                fx = Math.max(0.05f, Math.min(20f, fx));
                fy = Math.max(0.05f, Math.min(20f, fy));
                applySelectedLocal(startLocalX, startLocalY, startLocalRotation,
                        startScaleX * fx, startScaleY * fy);
                break;
            }
            default:
                break;
        }
        lastScreenX = x;
        lastScreenY = y;
        lastWorldX = toWorldX(x);
        lastWorldY = toWorldY(y);
    }

    private void finishGesture(boolean commit) {
        switch (gesture) {
            case GESTURE_PAN_PENDING:
                if (!moved) {
                    selectAt(downScreenX, downScreenY);
                }
                cancelEdit();
                break;
            case GESTURE_CREATE_BONE:
                if (commit) {
                    finishCreateBone();
                } else {
                    cancelEdit();
                }
                break;
            case GESTURE_CREATE_SHAPE:
                if (commit) {
                    finishCreateShape();
                } else {
                    cancelEdit();
                }
                break;
            case GESTURE_MOVE:
            case GESTURE_ROTATE:
            case GESTURE_SCALE:
                if (commit) {
                    commitEdit();
                } else {
                    commitEdit();
                }
                break;
            case GESTURE_PINCH:
            case GESTURE_PAN:
            case GESTURE_NONE:
            default:
                cancelEdit();
                break;
        }
        gesture = GESTURE_NONE;
        targetBone = null;
        moved = false;
        invalidate();
    }

    private void startPinch(MotionEvent event) {
        if (gesture == GESTURE_CREATE_BONE || gesture == GESTURE_CREATE_SHAPE) {
            cancelEdit();
        } else if (gesture == GESTURE_MOVE || gesture == GESTURE_ROTATE || gesture == GESTURE_SCALE) {
            commitEdit();
        }
        gesture = GESTURE_PINCH;
        pinchDistance = pointerDistance(event);
        pinchFocusX = pointerFocusX(event);
        pinchFocusY = pointerFocusY(event);
    }

    private void handlePinch(MotionEvent event) {
        if (gesture != GESTURE_PINCH) {
            startPinch(event);
            return;
        }
        float distance = pointerDistance(event);
        float focusX = pointerFocusX(event);
        float focusY = pointerFocusY(event);
        if (pinchDistance > 1f) {
            float worldFocusX = toWorldX(pinchFocusX);
            float worldFocusY = toWorldY(pinchFocusY);
            float newZoom = Math.max(0.02f, Math.min(20f, zoom * (distance / pinchDistance)));
            zoom = newZoom;
            camX = worldFocusX - (focusX - getWidth() / 2f) / zoom;
            camY = worldFocusY + (focusY - getHeight() / 2f) / zoom;
        }
        pinchDistance = distance;
        pinchFocusX = focusX;
        pinchFocusY = focusY;
        invalidate();
    }

    private float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        return (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
    }

    private float pointerFocusX(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return event.getX();
        }
        return (event.getX(0) + event.getX(1)) / 2f;
    }

    private float pointerFocusY(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return event.getY();
        }
        return (event.getY(0) + event.getY(1)) / 2f;
    }

    private void finishCreateBone() {
        float dx = createCurrentWorldX - createStartWorldX;
        float dy = createCurrentWorldY - createStartWorldY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance * zoom < dp(10f)) {
            cancelEdit();
            showMessage(getContext().getString(R.string.msg_drag_bone));
            return;
        }
        Pose pose = buildPose();
        Pose.World parentWorld = targetBone == null ? null : pose.world(targetBone);
        float parentAngle = parentWorld == null ? 0f : parentWorld.rotation;
        float parentScaleX = parentWorld == null ? 1f : parentWorld.scaleX;
        float parentScaleY = parentWorld == null ? 1f : parentWorld.scaleY;
        if (Math.abs(parentScaleX) < 0.0001f) {
            parentScaleX = 1f;
        }
        if (Math.abs(parentScaleY) < 0.0001f) {
            parentScaleY = 1f;
        }
        float localX;
        float localY;
        if (parentWorld == null) {
            localX = createStartWorldX;
            localY = createStartWorldY;
        } else {
            float worldDx = createStartWorldX - parentWorld.x;
            float worldDy = createStartWorldY - parentWorld.y;
            float cos = (float) Math.cos(Math.toRadians(-parentAngle));
            float sin = (float) Math.sin(Math.toRadians(-parentAngle));
            localX = (worldDx * cos - worldDy * sin) / parentScaleX;
            localY = (worldDx * sin + worldDy * cos) / parentScaleY;
        }
        float worldAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        float localLength = distance / Math.max(0.0001f, Math.abs(parentScaleX));
        Bone bone = new Bone(uniqueBoneName("bone"), targetBone);
        bone.x = localX;
        bone.y = localY;
        bone.rotation = normalizeAngle(worldAngle - parentAngle);
        bone.length = localLength;
        bone.color = BONE_COLORS[project.bones.size() % BONE_COLORS.length];
        project.bones.add(bone);
        selectedBone = bone.name;
        selectedSlot = null;
        commitEdit();
        notifySelection();
        invalidate();
    }

    private void finishCreateShape() {
        if (targetBone == null || project.findBone(targetBone) == null) {
            cancelEdit();
            return;
        }
        float centerLocalX;
        float centerLocalY;
        float width;
        float height;
        if (shapeDragged) {
            centerLocalX = (shapeStartLocalX + shapeCurrentLocalX) / 2f;
            centerLocalY = (shapeStartLocalY + shapeCurrentLocalY) / 2f;
            width = Math.max(4f, Math.abs(shapeCurrentLocalX - shapeStartLocalX));
            height = Math.max(4f, Math.abs(shapeCurrentLocalY - shapeStartLocalY));
        } else {
            centerLocalX = shapeStartLocalX;
            centerLocalY = shapeStartLocalY;
            width = 60f;
            height = 50f;
        }
        Slot slot = new Slot(uniqueSlotName("slot"), targetBone);
        Attachment attachment = new Attachment(uniqueAttachmentName("shape"), slot.name);
        attachment.shape = Attachment.SHAPE_RECT;
        attachment.x = centerLocalX;
        attachment.y = centerLocalY;
        attachment.width = width;
        attachment.height = height;
        attachment.color = SHAPE_COLORS[project.attachments.size() % SHAPE_COLORS.length];
        slot.attachment = attachment.name;
        project.slots.add(slot);
        project.attachments.add(attachment);
        project.syncCurrentSlotsToActiveSkin();
        selectedSlot = slot.name;
        selectedBone = targetBone;
        commitEdit();
        notifySelection();
        invalidate();
    }

    private void selectAt(float x, float y) {
        Pose pose = buildPose();
        for (int i = project.slots.size() - 1; i >= 0; i--) {
            Slot slot = project.slots.get(i);
            if (!slot.visible) {
                continue;
            }
            Attachment attachment = project.attachmentForSlot(slot);
            if (attachment == null || !attachment.visible) {
                continue;
            }
            Pose.World world = pose.world(slot.bone);
            if (world == null) {
                continue;
            }
            float[] points = attachmentScreenPoints(attachment, world);
            if (pointInPolygon(points, x, y)) {
                selectSlot(slot.name);
                return;
            }
        }
        for (int i = project.bones.size() - 1; i >= 0; i--) {
            Bone bone = project.bones.get(i);
            if (!bone.visible) {
                continue;
            }
            Pose.World world = pose.world(bone.name);
            if (world == null) {
                continue;
            }
            float jointX = toScreenX(world.x);
            float jointY = toScreenY(world.y);
            float tipWorldX = world.x + bone.length * world.scaleX
                    * (float) Math.cos(Math.toRadians(world.rotation));
            float tipWorldY = world.y + bone.length * world.scaleX
                    * (float) Math.sin(Math.toRadians(world.rotation));
            float tipX = toScreenX(tipWorldX);
            float tipY = toScreenY(tipWorldY);
            float distance = distanceToSegment(x, y, jointX, jointY, tipX, tipY);
            if (distance <= dp(18f) || Math.hypot(x - jointX, y - jointY) <= dp(16f)) {
                selectBone(bone.name);
                return;
            }
        }
        selectBone(null);
    }

    private boolean pointInPolygon(float[] points, float x, float y) {
        boolean inside = false;
        int count = points.length / 2;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            float xi = points[i * 2];
            float yi = points[i * 2 + 1];
            float xj = points[j * 2];
            float yj = points[j * 2 + 1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 0.000001f) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 0.0001f) {
            return (float) Math.hypot(px - x1, py - y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        float projectionX = x1 + t * dx;
        float projectionY = y1 + t * dy;
        return (float) Math.hypot(px - projectionX, py - projectionY);
    }

    private float worldToLocalX(String boneName, float worldX, float worldY) {
        Pose pose = buildPose();
        Pose.World world = pose.world(boneName);
        if (world == null) {
            return 0f;
        }
        float dx = worldX - world.x;
        float dy = worldY - world.y;
        float cos = (float) Math.cos(Math.toRadians(-world.rotation));
        float sin = (float) Math.sin(Math.toRadians(-world.rotation));
        float scaleX = Math.abs(world.scaleX) < 0.0001f ? 1f : world.scaleX;
        return (dx * cos - dy * sin) / scaleX;
    }

    private float worldToLocalY(String boneName, float worldX, float worldY) {
        Pose pose = buildPose();
        Pose.World world = pose.world(boneName);
        if (world == null) {
            return 0f;
        }
        float dx = worldX - world.x;
        float dy = worldY - world.y;
        float cos = (float) Math.cos(Math.toRadians(-world.rotation));
        float sin = (float) Math.sin(Math.toRadians(-world.rotation));
        float scaleY = Math.abs(world.scaleY) < 0.0001f ? 1f : world.scaleY;
        return (dx * sin + dy * cos) / scaleY;
    }

    private String uniqueBoneName(String base) {
        Set<String> names = new HashSet<>();
        for (Bone bone : project.bones) {
            names.add(bone.name);
        }
        if (!names.contains(base)) {
            return base;
        }
        int index = 1;
        while (names.contains(base + index)) {
            index++;
        }
        return base + index;
    }

    private String uniqueSlotName(String base) {
        Set<String> names = new HashSet<>();
        for (Slot slot : project.slots) {
            names.add(slot.name);
        }
        if (!names.contains(base)) {
            return base;
        }
        int index = 1;
        while (names.contains(base + index)) {
            index++;
        }
        return base + index;
    }

    private String uniqueAttachmentName(String base) {
        Set<String> names = new HashSet<>();
        for (Attachment attachment : project.attachments) {
            names.add(attachment.name);
        }
        if (!names.contains(base)) {
            return base;
        }
        int index = 1;
        while (names.contains(base + index)) {
            index++;
        }
        return base + index;
    }

    private float toWorldX(float screenX) {
        return camX + (screenX - getWidth() / 2f) / zoom;
    }

    private float toWorldY(float screenY) {
        return camY - (screenY - getHeight() / 2f) / zoom;
    }

    private float toScreenX(float worldX) {
        return getWidth() / 2f + (worldX - camX) * zoom;
    }

    private float toScreenY(float worldY) {
        return getHeight() / 2f - (worldY - camY) * zoom;
    }

    private float dp(float value) {
        return value * density;
    }

    private static float normalizeAngle(float angle) {
        while (angle > 180f) {
            angle -= 360f;
        }
        while (angle < -180f) {
            angle += 360f;
        }
        return angle;
    }

    private static int lighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * amount));
        g = Math.min(255, (int) (g + (255 - g) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void showMessage(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void notifySelection() {
        if (listener != null) {
            listener.onSelectionChanged();
        }
    }

    private void notifyProjectModified() {
        if (listener != null) {
            listener.onProjectModified();
        }
    }

    private void notifyTimeChanged() {
        if (listener != null) {
            listener.onTimeChanged();
        }
    }

    private void notifyPlayState() {
        if (listener != null) {
            listener.onPlayStateChanged();
        }
    }

    private void notifyAnimationChanged() {
        if (listener != null) {
            listener.onAnimationChanged();
        }
    }

    private void notifyToolChanged() {
        if (listener != null) {
            listener.onToolChanged();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (pendingInitialFit) {
            fitView();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        playing = false;
        removeCallbacks(playbackRunnable);
    }
}