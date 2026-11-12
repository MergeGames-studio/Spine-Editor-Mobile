package com.bandjou_mergegames.spineeditormobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bandjou_mergegames.spineeditormobile.data.ProjectCodec;
import com.bandjou_mergegames.spineeditormobile.data.SampleProject;
import com.bandjou_mergegames.spineeditormobile.data.SpineJsonCodec;
import com.bandjou_mergegames.spineeditormobile.data.SpineZipCodec;
import com.bandjou_mergegames.spineeditormobile.editor.EditorDialogs;
import com.bandjou_mergegames.spineeditormobile.editor.EditorView;
import com.bandjou_mergegames.spineeditormobile.editor.TimelineView;
import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Slot;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements EditorView.Listener {
    private static final String PROJECT_FILE = "editor_project.spineproj";
    private static final int REQUEST_IMPORT_SPINE = 1001;
    private static final int REQUEST_IMPORT_SPINE_ZIP = 1005;
    private static final int REQUEST_IMPORT_IMAGES = 1007;
    private static final int REQUEST_IMPORT_PROJECT = 1002;
    private static final int REQUEST_EXPORT_SPINE = 1003;
    private static final int REQUEST_EXPORT_SPINE_ZIP = 1006;
    private static final int REQUEST_EXPORT_PROJECT = 1004;
    private static final int[] TOOL_IDS = {
            R.id.toolSelect, R.id.toolBone, R.id.toolShape, R.id.toolMove,
            R.id.toolRotate, R.id.toolScale, R.id.toolPan
    };

    private final Handler autosaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autosaveRunnable = this::saveProjectQuietly;
    private final List<String> spinnerNames = new ArrayList<>();

    private EditorView editorView;
    private TimelineView timelineView;
    private Toolbar toolbar;
    private Spinner animationSpinner;
    private TextView selectedText;
    private TextView timeText;
    private TextView btnLoop;
    private TextView btnTimeline;
    private ImageButton btnPlay;
    private View timelinePanel;
    private boolean spinnerUpdating;
    private boolean lastCanUndo;
    private boolean lastCanRedo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_main);
        setupSystemBars();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        editorView = findViewById(R.id.editorView);
        timelineView = findViewById(R.id.timelineView);
        timelineView.setEditor(editorView);
        editorView.setListener(this);

        bindControls();
        Project project = loadProject();
        editorView.setProject(project, true);
        loadImagesForProject();
        updateProjectHeader();
        refreshAnimationSpinner();
        updateToolButtons();
        updateSelectionLabel();
        updateTimeLabel();
        updateLoopButton();
        updatePlayButton();
        float screenHeightDp = getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density;
        setTimelineVisible(screenHeightDp >= 560f);
    }

    private void setupSystemBars() {
        try {
            View root = findViewById(R.id.root);
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    int left = insets.getSystemWindowInsetLeft();
                    int top = insets.getSystemWindowInsetTop();
                    int right = insets.getSystemWindowInsetRight();
                    int bottom = insets.getSystemWindowInsetBottom();
                    view.setPadding(left, top, right, bottom);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        } catch (Throwable ignored) {
        }
        showTutorialIfNeeded();
    }

    private void showTutorialIfNeeded() {
        android.content.SharedPreferences prefs = getSharedPreferences("spine_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("tutorial_done", false)) {
            return;
        }
        final int[] step = {0};
        final int[] titles = {
                R.string.tutorial_step1_title,
                R.string.tutorial_step2_title,
                R.string.tutorial_step3_title,
                R.string.tutorial_step4_title,
                R.string.tutorial_step5_title
        };
        final int[] bodies = {
                R.string.tutorial_step1_body,
                R.string.tutorial_step2_body,
                R.string.tutorial_step3_body,
                R.string.tutorial_step4_body,
                R.string.tutorial_step5_body
        };
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_tutorial);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.6f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        android.widget.TextView title = dialog.findViewById(R.id.tutorialTitle);
        android.widget.TextView body = dialog.findViewById(R.id.tutorialBody);
        android.widget.TextView skip = dialog.findViewById(R.id.tutorialSkip);
        android.widget.TextView next = dialog.findViewById(R.id.tutorialNext);
        Runnable update = () -> {
            title.setText(titles[step[0]]);
            body.setText(bodies[step[0]]);
            if (step[0] == titles.length - 1) {
                next.setText(R.string.tutorial_done);
            } else {
                next.setText(R.string.tutorial_next);
            }
        };
        update.run();
        skip.setOnClickListener(v -> {
            prefs.edit().putBoolean("tutorial_done", true).apply();
            dialog.dismiss();
        });
        next.setOnClickListener(v -> {
            if (step[0] < titles.length - 1) {
                step[0]++;
                update.run();
            } else {
                prefs.edit().putBoolean("tutorial_done", true).apply();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void bindControls() {
        selectedText = findViewById(R.id.selectedText);
        timeText = findViewById(R.id.timeText);
        btnLoop = findViewById(R.id.btnLoop);
        btnTimeline = findViewById(R.id.btnTimeline);
        btnPlay = findViewById(R.id.btnPlay);
        timelinePanel = findViewById(R.id.timelinePanel);
        animationSpinner = findViewById(R.id.animationSpinner);

        for (int i = 0; i < TOOL_IDS.length; i++) {
            final int tool = i;
            findViewById(TOOL_IDS[i]).setOnClickListener(view -> editorView.setTool(tool));
        }
        findViewById(R.id.btnDeleteTool).setOnClickListener(view -> confirmDelete());
        findViewById(R.id.btnProperties).setOnClickListener(view -> openProperties());
        findViewById(R.id.btnHierarchy).setOnClickListener(view ->
                EditorDialogs.showHierarchy(this, editorView));
        findViewById(R.id.btnSkin).setOnClickListener(view ->
                EditorDialogs.showSkinsDialog(this, editorView));
        btnTimeline.setOnClickListener(view ->
                setTimelineVisible(timelinePanel.getVisibility() != View.VISIBLE));
        findViewById(R.id.btnCollapseTimeline).setOnClickListener(view ->
                setTimelineVisible(false));
        findViewById(R.id.btnZoomIn).setOnClickListener(view -> editorView.zoomBy(1.25f));
        findViewById(R.id.btnZoomOut).setOnClickListener(view -> editorView.zoomBy(0.8f));
        findViewById(R.id.btnFit).setOnClickListener(view -> editorView.fitView());
        btnPlay.setOnClickListener(view -> editorView.playPause());
        findViewById(R.id.btnAddAnimation).setOnClickListener(view ->
                EditorDialogs.showNewAnimation(this, editorView));
        findViewById(R.id.btnDeleteAnimation).setOnClickListener(view -> confirmDeleteAnimation());
        findViewById(R.id.btnAddKey).setOnClickListener(view -> editorView.addKeyForSelected());
        findViewById(R.id.btnDeleteKey).setOnClickListener(view -> editorView.deleteKeyForSelected());
        findViewById(R.id.btnAddSkinKey).setOnClickListener(view ->
                editorView.addSkinKeyForCurrentSkin());
        findViewById(R.id.btnDeleteSkinKey).setOnClickListener(view ->
                editorView.deleteSkinKeyAtPlayhead());
        btnLoop.setOnClickListener(view -> {
            Animation animation = editorView.getActiveAnimationObject();
            if (animation == null) {
                Toast.makeText(this, R.string.msg_select_animation, Toast.LENGTH_SHORT).show();
                return;
            }
            editorView.setLoop(!animation.loop);
            updateLoopButton();
        });

        animationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerUpdating || position < 0 || position >= spinnerNames.size()) {
                    return;
                }
                String name = position == 0 ? null : spinnerNames.get(position);
                Animation current = editorView.getActiveAnimationObject();
                String currentName = current == null ? null : current.name;
                if (!Objects.equals(name, currentName)) {
                    editorView.setActiveAnimation(name);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private Project loadProject() {
        File file = projectFile();
        if (!file.exists()) {
            return SampleProject.create();
        }
        try (InputStream input = new FileInputStream(file)) {
            Project project = ProjectCodec.fromJson(readText(input));
            if (project.bones.isEmpty()) {
                project.ensureRoot();
            }
            return project;
        } catch (Exception exception) {
            Toast.makeText(this, R.string.load_error, Toast.LENGTH_LONG).show();
            return SampleProject.create();
        }
    }

    private File projectFile() {
        return new File(getFilesDir(), PROJECT_FILE);
    }

    private void saveProjectQuietly() {
        saveProject(false);
    }

    private void saveProject(boolean notify) {
        if (editorView == null || editorView.getProject() == null) {
            return;
        }
        try (OutputStream output = new FileOutputStream(projectFile())) {
            output.write(ProjectCodec.toJson(editorView.getProject()).getBytes(StandardCharsets.UTF_8));
            if (notify) {
                Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception exception) {
            if (notify) {
                showError(R.string.save_error, exception);
            }
        }
    }

    private void scheduleAutosave() {
        autosaveHandler.removeCallbacks(autosaveRunnable);
        autosaveHandler.postDelayed(autosaveRunnable, 700);
    }

    private void updateProjectHeader() {
        Project project = editorView.getProject();
        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
            toolbar.setSubtitle(project == null ? null : project.name);
        }
    }

    private void updateToolButtons() {
        int tool = editorView.getTool();
        for (int i = 0; i < TOOL_IDS.length; i++) {
            findViewById(TOOL_IDS[i]).setSelected(i == tool);
        }
    }

    private void updateSelectionLabel() {
        if (selectedText == null) {
            return;
        }
        String bone = editorView.getSelectedBoneName();
        String slot = editorView.getSelectedSlotName();
        String attachment = editorView.getSelectedAttachmentName();
        if (slot != null && attachment != null) {
            selectedText.setText(getString(R.string.selected_slot, attachment, bone));
        } else if (bone != null) {
            selectedText.setText(getString(R.string.selected_bone, bone));
        } else {
            selectedText.setText(R.string.no_selection);
        }
    }

    private void updateTimeLabel() {
        if (timeText == null) {
            return;
        }
        Animation animation = editorView.getActiveAnimationObject();
        if (animation == null) {
            timeText.setText(R.string.no_animation);
        } else {
            timeText.setText(String.format(Locale.US, "%.2f / %.2f s",
                    editorView.getTime(), Math.max(0.1f, animation.duration)));
        }
    }

    private void updatePlayButton() {
        if (btnPlay == null) {
            return;
        }
        boolean available = editorView.getActiveAnimationObject() != null;
        boolean playing = editorView.isPlaying();
        btnPlay.setEnabled(available);
        btnPlay.setAlpha(available ? 1f : 0.4f);
        btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlay.setContentDescription(getString(playing ? R.string.pause : R.string.play));
    }

    private void updateLoopButton() {
        if (btnLoop == null) {
            return;
        }
        Animation animation = editorView.getActiveAnimationObject();
        btnLoop.setEnabled(animation != null);
        btnLoop.setAlpha(animation == null ? 0.4f : 1f);
        btnLoop.setText(animation == null || animation.loop
                ? R.string.loop_on : R.string.loop_off);
    }

    private void refreshAnimationSpinner() {
        if (animationSpinner == null) {
            return;
        }
        Project project = editorView.getProject();
        spinnerNames.clear();
        spinnerNames.add(getString(R.string.setup));
        int selected = 0;
        if (project != null) {
            for (Animation animation : project.animations) {
                spinnerNames.add(animation.name);
                if (animation.name.equals(project.activeAnimation)) {
                    selected = spinnerNames.size() - 1;
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, spinnerNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerUpdating = true;
        animationSpinner.setAdapter(adapter);
        animationSpinner.setSelection(selected, false);
        spinnerUpdating = false;
    }

    private void setTimelineVisible(boolean visible) {
        timelinePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        btnTimeline.setText(visible ? R.string.hide_timeline : R.string.timeline);
    }

    private void openProperties() {
        if (editorView.getSelectedSlotName() != null) {
            EditorDialogs.showSlotProperties(this, editorView);
        } else if (editorView.getSelectedBoneName() != null) {
            EditorDialogs.showBoneProperties(this, editorView);
        } else {
            Toast.makeText(this, R.string.msg_select_bone, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        String slotName = editorView.getSelectedSlotName();
        Project project = editorView.getProject();
        if (slotName != null && project != null) {
            Attachment attachment = project.attachmentForSlot(project.findSlot(slotName));
            String label = attachment == null ? slotName : attachment.name;
            new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.msg_delete_shape, label))
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            editorView.deleteSlot(slotName))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        String boneName = editorView.getSelectedBoneName();
        if (boneName != null) {
            new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.msg_delete_bone, boneName))
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            editorView.deleteBoneRecursive(boneName))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteAnimation() {
        Animation animation = editorView.getActiveAnimationObject();
        if (animation == null) {
            Toast.makeText(this, R.string.msg_select_animation, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                .setTitle(R.string.delete_animation_title)
                .setMessage(getString(R.string.delete_animation_message, animation.name))
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        editorView.deleteActiveAnimation())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmNewProject() {
        new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_new_project_title)
                .setMessage(R.string.dialog_new_project_message)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    Project project = SampleProject.empty();
                    project.name = "Untitled";
                    editorView.setProject(project, true);
                    updateProjectHeader();
                    refreshAnimationSpinner();
                    saveProject(false);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void importSpine(Uri uri) {
        try {
            String name = stripExtension(documentName(uri));
            if (TextUtils.isEmpty(name)) {
                name = "Imported Spine";
            }
            Project project = SpineJsonCodec.fromSpineJson(readText(uri), name);
            project.name = name;
            applyImportedProject(project);
        } catch (Exception exception) {
            showError(R.string.import_error, exception);
        }
    }

    private void importSpineZip(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open document");
            }
            SpineZipCodec.Package pkg = SpineZipCodec.fromZip(input);
            if (pkg.project == null) {
                throw new IOException("Invalid zip");
            }
            String name = stripExtension(documentName(uri));
            if (TextUtils.isEmpty(name)) {
                name = pkg.project.name;
            }
            pkg.project.name = TextUtils.isEmpty(name) ? "Imported Spine" : name;
            if (pkg.images != null) {
                for (java.util.Map.Entry<String, byte[]> entry : pkg.images.entrySet()) {
                    saveImageToStorage(stripExtension(entry.getKey()), entry.getValue());
                    Bitmap bitmap = BitmapFactory.decodeByteArray(entry.getValue(), 0, entry.getValue().length);
                    if (bitmap != null && editorView != null) {
                        editorView.putImage(stripExtension(entry.getKey()), bitmap);
                    }
                }
            }
            applyImportedProject(pkg.project);
            loadImagesForProject();
        } catch (Exception exception) {
            new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.import_error)
                    .setMessage(R.string.import_zip_error)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void importProject(Uri uri) {
        try {
            applyImportedProject(ProjectCodec.fromJson(readText(uri)));
        } catch (Exception exception) {
            showError(R.string.import_error, exception);
        }
    }

    private void importImages(Uri uri) {
        try {
            if (editorView == null || editorView.getProject() == null) {
                return;
            }
            String fileName = documentName(uri);
            String base = stripExtension(fileName);
            if (TextUtils.isEmpty(base)) {
                base = "image";
            }
            askImageName(base, uri);
        } catch (Exception exception) {
            new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.import_error)
                    .setMessage(R.string.import_images_error)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void askImageName(String defaultName, Uri uri) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(defaultName);
        input.setSingleLine(true);
        input.setTextColor(getResources().getColor(R.color.text));
        new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                .setTitle(R.string.menu_import_images)
                .setView(input)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        name = defaultName;
                    }
                    importImageWithName(name, uri);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private void importImageWithName(String name, Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IOException("Cannot open image");
            }
            byte[] data = readAll(input);
            input.close();
            saveImageToStorage(name, data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) {
                throw new IOException("Invalid image");
            }
            float width = bitmap.getWidth();
            float height = bitmap.getHeight();
            Project project = editorView.getProject();
            String boneName = editorView.getSelectedBoneName();
            if (boneName == null && !project.bones.isEmpty()) {
                boneName = project.bones.get(0).name;
            }
            if (boneName == null) {
                throw new IOException("No bone");
            }
            String slotName = uniqueSlotName(name);
            String attachmentName = uniqueAttachmentName(name);
            Slot slot = new Slot(slotName, boneName);
            Attachment attachment = new Attachment(attachmentName, slotName);
            attachment.shape = Attachment.SHAPE_RECT;
            attachment.width = width;
            attachment.height = height;
            attachment.x = 0f;
            attachment.y = 0f;
            attachment.color = 0xFFFFFFFF;
            attachment.path = name;
            attachment.region = name;
            slot.attachment = attachmentName;
            project.slots.add(slot);
            project.attachments.add(attachment);
            project.syncCurrentSlotsToActiveSkin();
            editorView.putImage(name, bitmap);
            editorView.selectSlot(slotName);
            editorView.invalidate();
            Toast.makeText(this, R.string.imported, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.import_error)
                    .setMessage(R.string.import_images_error)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void importImagesClip(android.content.ClipData clipData) {
        if (clipData == null) {
            return;
        }
        for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            if (uri != null) {
                importImages(uri);
            }
        }
    }

    private String uniqueSlotName(String base) {
        Project project = editorView.getProject();
        String name = base;
        int i = 2;
        while (project != null && project.findSlot(name) != null) {
            name = base + i++;
        }
        return name;
    }

    private String uniqueAttachmentName(String base) {
        Project project = editorView.getProject();
        String name = base;
        int i = 2;
        while (project != null && project.findAttachment(name) != null) {
            name = base + i++;
        }
        return name;
    }

    private java.util.Map<String, Bitmap> getImageCache() {
        try {
            java.lang.reflect.Field field = editorView.getClass().getDeclaredField("imageCache");
            field.setAccessible(true);
            Object obj = field.get(editorView);
            if (obj instanceof java.util.Map) {
                return (java.util.Map<String, Bitmap>) obj;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveImageToStorage(String name, byte[] data) throws IOException {
        File dir = new File(getFilesDir(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        try (FileOutputStream out = new FileOutputStream(new File(dir, safe + ".png"))) {
            out.write(data);
        }
    }

    private void loadImagesForProject() {
        if (editorView == null || editorView.getProject() == null) {
            return;
        }
        File dir = new File(getFilesDir(), "images");
        if (!dir.exists()) {
            return;
        }
        for (com.bandjou_mergegames.spineeditormobile.model.Attachment attachment : editorView.getProject().attachments) {
            String key = attachment.region != null ? attachment.region : attachment.path;
            if (key == null || editorView.getImage(key) != null) {
                continue;
            }
            String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
            File file = new File(dir, safe + ".png");
            if (!file.exists()) {
                continue;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                editorView.putImage(key, bitmap);
            }
        }
    }

    private void applyImportedProject(Project project) {
        if (project.bones.isEmpty()) {
            project.ensureRoot();
        }
        editorView.setProject(project, true);
        updateProjectHeader();
        refreshAnimationSpinner();
        saveProject(false);
        Toast.makeText(this, R.string.imported, Toast.LENGTH_SHORT).show();
    }

    private void exportSpine(Uri uri) {
        try {
            Project project = editorView.getProject();
            if (project == null) {
                return;
            }
            writeText(uri, SpineJsonCodec.toSpineJson(project));
            Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            showError(R.string.export_error, exception);
        }
    }

    private void exportSpineZip(Uri uri) {
        try {
            Project project = editorView.getProject();
            if (project == null) {
                return;
            }
            byte[] data = SpineZipCodec.toZip(project, project.name, getImageCache());
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Cannot open document");
                }
                output.write(data);
            }
            Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            showError(R.string.export_error, exception);
        }
    }

    private void exportProject(Uri uri) {
        try {
            Project project = editorView.getProject();
            if (project == null) {
                return;
            }
            writeText(uri, ProjectCodec.toJson(project));
            Toast.makeText(this, R.string.exported, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            showError(R.string.export_error, exception);
        }
    }

    private void exportPng() {
        Bitmap bitmap = editorView.exportBitmap();
        if (bitmap == null) {
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show();
            return;
        }
        File file = new File(getCacheDir(), "spine_" + System.currentTimeMillis() + ".png");
        try (OutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("PNG compression failed");
            }
        } catch (Exception exception) {
            showError(R.string.export_error, exception);
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.menu_export_png)));
    }

    private void refreshUndoRedoMenu() {
        if (editorView == null) {
            return;
        }
        boolean canUndo = editorView.canUndo();
        boolean canRedo = editorView.canRedo();
        if (canUndo != lastCanUndo || canRedo != lastCanRedo) {
            lastCanUndo = canUndo;
            lastCanRedo = canRedo;
            invalidateOptionsMenu();
        }
    }

    private void openDocument(int requestCode, int titleRes) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, getString(titleRes)), requestCode);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private void createDocument(int requestCode, String mime, String title) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, title);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private String safeFileName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "spine_project";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String documentName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String name = uri.getLastPathSegment();
        return name == null ? "spine_project" : name;
    }

    private String stripExtension(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String readText(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open document");
            }
            return readText(input);
        }
    }

    private String readText(InputStream input) throws IOException {
        return new String(readAll(input), "UTF-8");
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void writeText(Uri uri, String text) throws IOException {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Cannot open document");
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void showError(int titleRes, Exception exception) {
        String message = exception.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = exception.getClass().getSimpleName();
        }
        new MaterialAlertDialogBuilder(this, R.style.AppAlertDialog)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_IMPORT_IMAGES) {
            if (data.getClipData() != null) {
                importImagesClip(data.getClipData());
            } else if (uri != null) {
                importImages(uri);
            }
            return;
        }
        switch (requestCode) {
            case REQUEST_IMPORT_SPINE:
                importSpine(uri);
                break;
            case REQUEST_IMPORT_SPINE_ZIP:
                importSpineZip(uri);
                break;
            case REQUEST_IMPORT_PROJECT:
                importProject(uri);
                break;
            case REQUEST_EXPORT_SPINE:
                exportSpine(uri);
                break;
            case REQUEST_EXPORT_SPINE_ZIP:
                exportSpineZip(uri);
                break;
            case REQUEST_EXPORT_PROJECT:
                exportProject(uri);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem undo = menu.findItem(R.id.action_undo);
        MenuItem redo = menu.findItem(R.id.action_redo);
        if (undo != null) {
            undo.setEnabled(editorView != null && editorView.canUndo());
        }
        if (redo != null) {
            redo.setEnabled(editorView != null && editorView.canRedo());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            editorView.undo();
            refreshUndoRedoMenu();
            return true;
        }
        if (id == R.id.action_redo) {
            editorView.redo();
            refreshUndoRedoMenu();
            return true;
        }
        if (id == R.id.action_save) {
            saveProject(true);
            return true;
        }
        if (id == R.id.action_new) {
            confirmNewProject();
            return true;
        }
        if (id == R.id.action_import_spine) {
            openDocument(REQUEST_IMPORT_SPINE, R.string.menu_import_spine);
            return true;
        }
        if (id == R.id.action_import_spine_zip) {
            openDocument(REQUEST_IMPORT_SPINE_ZIP, R.string.menu_import_spine_zip);
            return true;
        }
        if (id == R.id.action_import_images) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(Intent.createChooser(intent, getString(R.string.menu_import_images)), REQUEST_IMPORT_IMAGES);
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_import_project) {
            openDocument(REQUEST_IMPORT_PROJECT, R.string.menu_import_project);
            return true;
        }
        if (id == R.id.action_export_spine) {
            Project project = editorView.getProject();
            String name = project == null ? "skeleton" : safeFileName(project.name);
            createDocument(REQUEST_EXPORT_SPINE, "application/json", name + ".json");
            return true;
        }
        if (id == R.id.action_export_spine_zip) {
            Project project = editorView.getProject();
            String name = project == null ? "skeleton" : safeFileName(project.name);
            createDocument(REQUEST_EXPORT_SPINE_ZIP, "application/zip", name + ".zip");
            return true;
        }
        if (id == R.id.action_export_project) {
            Project project = editorView.getProject();
            String name = project == null ? "spine_project" : safeFileName(project.name);
            createDocument(REQUEST_EXPORT_PROJECT, "application/octet-stream",
                    name + ".spineproj");
            return true;
        }
        if (id == R.id.action_export_png) {
            exportPng();
            return true;
        }
        if (id == R.id.action_help) {
            EditorDialogs.showHelp(this);
            return true;
        }
        if (id == R.id.action_about) {
            EditorDialogs.showAbout(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autosaveHandler.removeCallbacks(autosaveRunnable);
        if (editorView != null) {
            saveProject(false);
        }
    }

    @Override
    protected void onDestroy() {
        autosaveHandler.removeCallbacks(autosaveRunnable);
        super.onDestroy();
    }

    @Override
    public void onSelectionChanged() {
        updateSelectionLabel();
        if (timelineView != null) {
            timelineView.invalidate();
        }
    }

    @Override
    public void onProjectModified() {
        scheduleAutosave();
        updateLoopButton();
        if (timelineView != null) {
            timelineView.invalidate();
        }
        refreshUndoRedoMenu();
    }

    @Override
    public void onTimeChanged() {
        updateTimeLabel();
        if (timelineView != null) {
            timelineView.invalidate();
        }
    }

    @Override
    public void onPlayStateChanged() {
        updatePlayButton();
        updateTimeLabel();
    }

    @Override
    public void onAnimationChanged() {
        refreshAnimationSpinner();
        updateTimeLabel();
        updateLoopButton();
        updatePlayButton();
        if (timelineView != null) {
            timelineView.invalidate();
        }
        refreshUndoRedoMenu();
    }

    @Override
    public void onToolChanged() {
        updateToolButtons();
    }
}