package com.bandjou_mergegames.spineeditormobile.editor;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bandjou_mergegames.spineeditormobile.R;
import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Skin;
import com.bandjou_mergegames.spineeditormobile.model.Slot;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EditorDialogs {
    private static final int[] PALETTE = {
            0xFF35D0BA, 0xFFFFB74D, 0xFFEF5350, 0xFF42A5F5,
            0xFFAB47BC, 0xFF66BB6A, 0xFF4DD0E1, 0xFFFFFFFF, 0xFF8B98A5
    };
    private static final String[] SHAPES = {
            Attachment.SHAPE_RECT, Attachment.SHAPE_ELLIPSE, Attachment.SHAPE_TRIANGLE
    };

    private EditorDialogs() {
    }

    public static void showBoneProperties(Activity activity, EditorView editor) {
        String boneName = editor.getSelectedBoneName();
        Project project = editor.getProject();
        Bone bone = project == null ? null : project.findBone(boneName);
        if (bone == null) {
            Toast.makeText(activity, R.string.msg_select_bone, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout root = dialogRoot(activity);
        EditText name = addField(activity, root, activity.getString(R.string.field_name), bone.name,
                InputType.TYPE_CLASS_TEXT);
        LinearLayout positionRow = row(activity);
        EditText x = addInlineField(activity, positionRow, activity.getString(R.string.field_x),
                format(bone.x), numberInput());
        EditText y = addInlineField(activity, positionRow, activity.getString(R.string.field_y),
                format(bone.y), numberInput());
        addRow(root, positionRow);

        LinearLayout transformRow = row(activity);
        EditText rotation = addInlineField(activity, transformRow, activity.getString(R.string.field_rotation),
                format(bone.rotation), numberInput());
        EditText length = addInlineField(activity, transformRow, activity.getString(R.string.field_length),
                format(bone.length), numberInput());
        addRow(root, transformRow);

        LinearLayout scaleRow = row(activity);
        EditText scaleX = addInlineField(activity, scaleRow, activity.getString(R.string.field_scale_x),
                format(bone.scaleX), numberInput());
        EditText scaleY = addInlineField(activity, scaleRow, activity.getString(R.string.field_scale_y),
                format(bone.scaleY), numberInput());
        addRow(root, scaleRow);

        TextView note = note(activity, activity.getString(R.string.bone_note));
        root.addView(note, topMargin(activity, 16));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(activity.getString(R.string.dialog_bone_title) + ": " + bone.name)
                .setView(wrapInScroll(activity, root))
                .setPositiveButton(R.string.apply, null)
                .setNegativeButton(R.string.delete, null)
                .setNeutralButton(R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (editor.applyBoneProperties(bone.name, name.getText().toString(), parse(x, bone.x),
                    parse(y, bone.y), parse(rotation, bone.rotation), parse(scaleX, bone.scaleX),
                    parse(scaleY, bone.scaleY), parse(length, bone.length))) {
                dialog.dismiss();
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view ->
                confirmDeleteBone(activity, editor, bone.name, dialog));
        name.requestFocus();
        showKeyboard(activity, name);
    }

    public static void showSlotProperties(Activity activity, EditorView editor) {
        String slotName = editor.getSelectedSlotName();
        Project project = editor.getProject();
        Slot slot = project == null ? null : project.findSlot(slotName);
        Attachment attachment = project == null ? null : project.attachmentForSlot(slot);
        if (slot == null) {
            Toast.makeText(activity, R.string.no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        if (attachment == null) {
            java.util.List<Attachment> forSlot = project.attachmentsForSlot(slot.name);
            if (!forSlot.isEmpty()) {
                attachment = forSlot.get(0);
                editor.setSlotAttachment(slot.name, attachment.name);
                slot = project.findSlot(slotName);
            }
        }
        if (attachment == null) {
            Toast.makeText(activity, R.string.no_selection, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout root = dialogRoot(activity);

        java.util.List<Attachment> slotAttachments = project.attachmentsForSlot(slot.name);
        List<String> attachNames = new ArrayList<>();
        List<String> attachLabels = new ArrayList<>();
        int attachIndex = 0;
        for (int i = 0; i < slotAttachments.size(); i++) {
            Attachment a = slotAttachments.get(i);
            attachNames.add(a.name);
            attachLabels.add(a.name);
            if (a.name.equals(attachment.name)) attachIndex = i;
        }
        if (attachLabels.isEmpty()) {
            attachNames.add(attachment.name);
            attachLabels.add(attachment.name);
        }
        Spinner attachSpinner = addSpinner(activity, root, activity.getString(R.string.field_attachment),
                attachLabels, attachIndex);
        TextView duplicateAttach = chip(activity, activity.getString(R.string.new_attachment));
        LinearLayout attachRow = row(activity);
        attachRow.addView(duplicateAttach);
        root.addView(attachRow, topMargin(activity, 6));

        List<String> boneNames = new ArrayList<>();
        int boneIndex = 0;
        for (Bone item : project.bones) {
            if (item.name.equals(slot.bone)) boneIndex = boneNames.size();
            boneNames.add(item.name);
        }
        Spinner boneSpinner = addSpinner(activity, root, activity.getString(R.string.field_bone),
                boneNames, boneIndex);

        List<String> shapeLabels = new ArrayList<>();
        shapeLabels.add(activity.getString(R.string.shape_rect));
        shapeLabels.add(activity.getString(R.string.shape_ellipse));
        shapeLabels.add(activity.getString(R.string.shape_triangle));
        int shapeIndex = 0;
        for (int i = 0; i < SHAPES.length; i++) {
            if (SHAPES[i].equals(attachment.shape)) shapeIndex = i;
        }
        Spinner shapeSpinner = addSpinner(activity, root, activity.getString(R.string.field_shape),
                shapeLabels, shapeIndex);

        final int[] pickedColor = {attachment.color};
        final int[] paletteIndex = {paletteIndexFor(attachment.color)};
        TextView colorButton = colorButton(activity, pickedColor[0]);
        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 38));
        colorParams.topMargin = dp(activity, 8);
        root.addView(colorButton, colorParams);
        colorButton.setOnClickListener(view -> {
            paletteIndex[0] = (paletteIndex[0] + 1) % PALETTE.length;
            pickedColor[0] = PALETTE[paletteIndex[0]];
            styleColorButton(activity, colorButton, pickedColor[0]);
        });

        LinearLayout positionRow = row(activity);
        EditText x = addInlineField(activity, positionRow, activity.getString(R.string.field_x),
                format(attachment.x), numberInput());
        EditText y = addInlineField(activity, positionRow, activity.getString(R.string.field_y),
                format(attachment.y), numberInput());
        addRow(root, positionRow);

        LinearLayout sizeRow = row(activity);
        EditText width = addInlineField(activity, sizeRow, activity.getString(R.string.field_width),
                format(attachment.width), numberInput());
        EditText height = addInlineField(activity, sizeRow, activity.getString(R.string.field_height),
                format(attachment.height), numberInput());
        addRow(root, sizeRow);

        LinearLayout rotationRow = row(activity);
        EditText rotation = addInlineField(activity, rotationRow, activity.getString(R.string.field_rotation),
                format(attachment.rotation), numberInput());
        addRow(root, rotationRow);

        CheckBox slotVisible = checkBox(activity, activity.getString(R.string.slot_visible));
        slotVisible.setChecked(slot.visible);
        root.addView(slotVisible, topMargin(activity, 12));
        CheckBox attachmentVisible = checkBox(activity, activity.getString(R.string.attachment_visible));
        attachmentVisible.setChecked(attachment.visible);
        root.addView(attachmentVisible, topMargin(activity, 4));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(activity.getString(R.string.dialog_slot_title) + ": " + slot.name)
                .setView(wrapInScroll(activity, root))
                .setPositiveButton(R.string.apply, null)
                .setNegativeButton(R.string.delete, null)
                .setNeutralButton(R.string.cancel, null)
                .create();

        final Attachment currentAttachment = attachment;
        final String currentSlotName = slot.name;
        final Project proj = project;
        duplicateAttach.setOnClickListener(view -> {
            String dup = editor.duplicateAttachmentForSlot(currentSlotName);
            if (dup != null) {
                dialog.dismiss();
                editor.selectSlot(currentSlotName);
                showSlotProperties(activity, editor);
            }
        });

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int selectedBone = boneSpinner.getSelectedItemPosition();
            int selectedShape = shapeSpinner.getSelectedItemPosition();
            int selectedAttach = attachSpinner.getSelectedItemPosition();
            if (selectedBone < 0 || selectedShape < 0) return;
            String targetAttach = currentAttachment.name;
            if (selectedAttach >= 0 && selectedAttach < attachNames.size()) {
                targetAttach = attachNames.get(selectedAttach);
            }
            if (targetAttach != null && !targetAttach.equals(currentAttachment.name)) {
                editor.setSlotAttachment(currentSlotName, targetAttach);
            }
            Slot slotNow = proj.findSlot(currentSlotName);
            Attachment attNow = proj.attachmentForSlot(slotNow);
            if (attNow == null) attNow = currentAttachment;
            editor.applySlotProperties(currentSlotName, boneNames.get(selectedBone),
                    slotVisible.isChecked(), SHAPES[selectedShape], parse(x, attNow.x),
                    parse(y, attNow.y), parse(rotation, attNow.rotation),
                    parse(width, attNow.width), parse(height, attNow.height),
                    pickedColor[0], attachmentVisible.isChecked());
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                    .setTitle(R.string.delete)
                    .setMessage(activity.getString(R.string.msg_delete_shape, currentSlotName))
                    .setPositiveButton(R.string.delete, (confirm, which) -> {
                        editor.deleteSlot(currentSlotName);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    public static void showNewAnimation(Activity activity, EditorView editor) {
        Project project = editor.getProject();
        if (project == null) {
            return;
        }
        LinearLayout root = dialogRoot(activity);
        int index = 1;
        while (project.findAnimation("Animation " + index) != null) {
            index++;
        }
        EditText name = addField(activity, root, activity.getString(R.string.field_name),
                "Animation " + index, InputType.TYPE_CLASS_TEXT);
        EditText duration = addField(activity, root, activity.getString(R.string.field_duration),
                "1.0", numberInput());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_animation_title)
                .setView(wrapInScroll(activity, root))
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String animationName = name.getText().toString().trim();
            if (animationName.isEmpty()) {
                Toast.makeText(activity, R.string.msg_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            float seconds = parse(duration, 1f);
            if (seconds <= 0f) {
                seconds = 1f;
            }
            if (editor.addAnimation(animationName, seconds)) {
                dialog.dismiss();
            }
        });
        name.requestFocus();
        showKeyboard(activity, name);
    }

    public static void showHierarchy(Activity activity, EditorView editor) {
        if (editor.getProject() == null) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.AppBottomSheetDialog);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(activity, R.color.panel));
        root.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 20));

        LinearLayout titleRow = row(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(R.string.dialog_hierarchy_title);
        title.setTextColor(color(activity, R.color.text));
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = chip(activity, activity.getString(R.string.close));
        close.setOnClickListener(view -> dialog.dismiss());
        titleRow.addView(close);
        root.addView(titleRow);

        LinearLayout actions = row(activity);
        TextView addRoot = chip(activity, activity.getString(R.string.add_root_bone));
        TextView addChild = chip(activity, activity.getString(R.string.add_child_bone));
        actions.addView(addRoot);
        actions.addView(addChild, startMargin(activity, 8));
        root.addView(actions, topMargin(activity, 10));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int listHeight = Math.min(dp(activity, 430), Math.max(dp(activity, 220), screenHeight / 2));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> populateHierarchy(activity, list, editor, dialog, refresh[0]);
        addRoot.setOnClickListener(view -> {
            editor.addRootBone();
            if (refresh[0] != null) {
                refresh[0].run();
            }
        });
        addChild.setOnClickListener(view -> {
            editor.addChildBone();
            if (refresh[0] != null) {
                refresh[0].run();
            }
        });

        dialog.setContentView(root);
        dialog.show();
        dialog.getBehavior().setSkipCollapsed(true);
        if (refresh[0] != null) {
            refresh[0].run();
        }
    }

    public static void showSkinsDialog(Activity activity, EditorView editor) {
        Project project = editor.getProject();
        if (project == null) return;
        if (project.skins.isEmpty()) project.ensureDefaultSkin();

        LinearLayout root = dialogRoot(activity);
        List<String> skinNames = new ArrayList<>();
        int skinIndex = 0;
        for (int i = 0; i < project.skins.size(); i++) {
            Skin skin = project.skins.get(i);
            if (skin == null) continue;
            if (skin.name != null && skin.name.equals(project.activeSkin)) skinIndex = skinNames.size();
            if (skin.name != null) skinNames.add(skin.name);
        }
        if (skinNames.isEmpty()) {
            skinNames.add(project.activeSkin == null ? "default" : project.activeSkin);
        }
        skinIndex = Math.max(0, Math.min(skinIndex, skinNames.size()-1));
        Spinner skinSpinner = addSpinner(activity, root, activity.getString(R.string.skin), skinNames, skinIndex);
        EditText newName = addField(activity, root, activity.getString(R.string.skin_name), "", InputType.TYPE_CLASS_TEXT);
        root.addView(note(activity, activity.getString(R.string.skins_note)), topMargin(activity, 12));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(R.string.skins)
                .setView(wrapInScroll(activity, root))
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.add_skin, null)
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int idx = skinSpinner.getSelectedItemPosition();
            if (idx >= 0 && idx < skinNames.size()) {
                editor.switchSkin(skinNames.get(idx));
            }
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            String name = newName.getText().toString().trim();
            if (name.isEmpty()) {
                int n = project.skins.size() + 1;
                name = "skin" + n;
                while (project.findSkin(name) != null) { n++; name = "skin" + n; }
            }
            if (editor.addSkin(name)) {
                dialog.dismiss();
                editor.switchSkin(name);
                showSkinsDialog(activity, editor);
            }
        });
    }

    public static void showHelp(Activity activity) {
        showInfoDialog(activity, activity.getString(R.string.help_title),
                activity.getString(R.string.help_message));
    }

    public static void showAbout(Activity activity) {
        showInfoDialog(activity, activity.getString(R.string.about_title),
                activity.getString(R.string.about_message, versionName(activity)));
    }

    private static void showInfoDialog(Activity activity, String title, String message) {
        LinearLayout root = dialogRoot(activity);
        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextSize(16);
        text.setTextColor(color(activity, R.color.text));
        text.setLineSpacing(dp(activity, 4), 1f);
        text.setPadding(0, dp(activity, 6), 0, dp(activity, 18));
        root.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(title)
                .setView(wrapInScroll(activity, root))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static String versionName(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "1.0";
        }
    }

    private static void populateHierarchy(Activity activity, LinearLayout list, EditorView editor,
                                          BottomSheetDialog dialog, Runnable refresh) {
        list.removeAllViews();
        Project project = editor.getProject();
        if (project == null) {
            return;
        }
        list.addView(sectionTitle(activity, activity.getString(R.string.bones)));

        list.addView(sectionTitle(activity, activity.getString(R.string.slots)));
        for (Slot slot : project.slots) {
            boolean selected = slot.name.equals(editor.getSelectedSlotName());
            LinearLayout row = hierarchyRow(activity, selected, dp(activity, 8));
            TextView label = new TextView(activity);
            label.setText(slot.name);
            label.setTextSize(13);
            label.setSingleLine(true);
            label.setTextColor(color(activity, selected ? R.color.accent : R.color.text));
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(label, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));
            TextView up = chip(activity, activity.getString(R.string.move_up));
            final Runnable r = refresh;
            up.setOnClickListener(view -> {
                editor.moveSlot(slot.name, -1);
                if (r != null) {
                    r.run();
                }
            });
            row.addView(up, startMargin(activity, 6));
            TextView down = chip(activity, activity.getString(R.string.move_down));
            down.setOnClickListener(view -> {
                editor.moveSlot(slot.name, 1);
                if (r != null) {
                    r.run();
                }
            });
            row.addView(down, startMargin(activity, 6));
            row.setOnClickListener(view -> {
                editor.selectSlot(slot.name);
                if (r != null) {
                    r.run();
                }
            });
            list.addView(row);
        }

        list.addView(sectionTitle(activity, activity.getString(R.string.bones)));

        for (Bone bone : project.bones) {
            boolean selected = bone.name.equals(editor.getSelectedBoneName());
            LinearLayout row = hierarchyRow(activity, selected,
                    dp(activity, 8) + dp(activity, 14) * Math.min(10, project.boneDepth(bone)));
            TextView label = new TextView(activity);
            label.setText(bone.name);
            label.setTextSize(13);
            label.setSingleLine(true);
            label.setTextColor(color(activity, selected ? R.color.accent : R.color.text));
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(label, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));
            if (bone.parent != null) {
                TextView parent = new TextView(activity);
                parent.setText(bone.parent);
                parent.setTextSize(10);
                parent.setGravity(Gravity.CENTER_VERTICAL);
                parent.setTextColor(color(activity, R.color.text_dim));
                row.addView(parent, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 42)));
            }
            TextView menu = hierarchyMenu(activity);
            row.addView(menu, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 42)));
            String boneName = bone.name;
            row.setOnClickListener(view -> {
                editor.selectBone(boneName);
                refresh.run();
            });
            menu.setOnClickListener(view -> {
                dialog.dismiss();
                list.postDelayed(() -> showBoneProperties(activity, editor), 180);
            });
            list.addView(row);
        }

        list.addView(sectionTitleWithMargin(activity, activity.getString(R.string.slots)));
        if (project.slots.isEmpty()) {
            TextView empty = note(activity, activity.getString(R.string.empty_slots));
            list.addView(empty, topMargin(activity, 8));
            return;
        }
        for (Slot slot : project.slots) {
            boolean selected = slot.name.equals(editor.getSelectedSlotName());
            LinearLayout row = hierarchyRow(activity, selected, dp(activity, 14));
            TextView label = new TextView(activity);
            String attachmentName = slot.attachment == null ? slot.name : slot.attachment;
            label.setText(attachmentName + "  ·  " + slot.bone);
            label.setTextSize(13);
            label.setSingleLine(true);
            label.setTextColor(color(activity, selected ? R.color.accent : R.color.text));
            row.addView(label, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));
            TextView menu = hierarchyMenu(activity);
            row.addView(menu, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 42)));
            String slotName = slot.name;
            row.setOnClickListener(view -> {
                editor.selectSlot(slotName);
                refresh.run();
            });
            menu.setOnClickListener(view -> {
                editor.selectSlot(slotName);
                dialog.dismiss();
                list.postDelayed(() -> showSlotProperties(activity, editor), 180);
            });
            list.addView(row);
        }
    }

    private static LinearLayout hierarchyRow(Activity activity, boolean selected, int indent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(indent, 0, dp(activity, 2), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(activity, 8));
        background.setColor(selected ? 0x2935D0BA : Color.TRANSPARENT);
        row.setBackground(background);
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private static TextView hierarchyMenu(Activity activity) {
        TextView menu = new TextView(activity);
        menu.setText("⋯");
        menu.setTextSize(18);
        menu.setGravity(Gravity.CENTER);
        menu.setTextColor(color(activity, R.color.text_dim));
        menu.setClickable(true);
        menu.setFocusable(true);
        return menu;
    }

    private static TextView sectionTitle(Activity activity, String text) {
        TextView title = new TextView(activity);
        title.setText(text.toUpperCase(Locale.getDefault()));
        title.setTextSize(10);
        title.setLetterSpacing(0.08f);
        title.setTextColor(color(activity, R.color.text_dim));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 4));
        return title;
    }

    private static TextView sectionTitleWithMargin(Activity activity, String text) {
        TextView title = sectionTitle(activity, text);
        title.setPadding(dp(activity, 10), dp(activity, 18), dp(activity, 10), dp(activity, 4));
        return title;
    }

    private static void confirmDeleteBone(Activity activity, EditorView editor, String name,
                                          AlertDialog parentDialog) {
        new MaterialAlertDialogBuilder(activity, R.style.AppAlertDialog)
                .setTitle(R.string.delete)
                .setMessage(activity.getString(R.string.msg_delete_bone, name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    editor.deleteBoneRecursive(name);
                    parentDialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static ScrollView wrapInScroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    private static LinearLayout dialogRoot(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 4), dp(context, 20), 0);
        return root;
    }

    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private static void addRow(LinearLayout parent, View child) {
        addRow(parent, child, 8);
    }

    private static void addRow(LinearLayout parent, View child, int topMarginDp) {
        Context context = parent.getContext();
        parent.addView(child, topMargin(context, topMarginDp));
    }

    private static EditText addField(Context context, LinearLayout parent, String labelText,
                                     String value, int inputType) {
        TextView label = fieldLabel(context, labelText);
        parent.addView(label, topMargin(context, 12));
        EditText input = new EditText(context);
        input.setText(value);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(color(context, R.color.text));
        input.setPadding(0, dp(context, 4), 0, dp(context, 4));
        parent.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private static EditText addInlineField(Context context, LinearLayout row, String labelText,
                                           String value, int inputType) {
        TextView label = fieldLabel(context, labelText);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() > 0) {
            labelParams.setMarginStart(dp(context, 12));
        }
        row.addView(label, labelParams);
        EditText input = new EditText(context);
        input.setText(value);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(color(context, R.color.text));
        input.setPadding(0, dp(context, 4), 0, dp(context, 4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(context, 6));
        row.addView(input, params);
        return input;
    }

    private static Spinner addSpinner(Context context, LinearLayout parent, String labelText,
                                      List<String> values, int selected) {
        TextView label = fieldLabel(context, labelText);
        parent.addView(label, topMargin(context, 12));
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_item, values);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected), false);
        spinner.setBackgroundResource(R.drawable.bg_chip);
        spinner.setPopupBackgroundResource(R.color.panel2);
        spinner.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        parent.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)));
        return spinner;
    }

    private static TextView colorButton(Context context, int fill) {
        TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 38)));
        styleColorButton(context, button, fill);
        return button;
    }

    private static void styleColorButton(Context context, TextView button, int fill) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(context, 15));
        background.setStroke(dp(context, 1), 0x55FFFFFF);
        button.setBackground(background);
        button.setTextColor(luminance(fill) > 0.58f ? 0xFF0E1116 : 0xFFFFFFFF);
        button.setText(context.getString(R.string.color) + ": " + hexColor(fill));
    }

    private static CheckBox checkBox(Context context, String text) {
        CheckBox box = new CheckBox(context);
        box.setText(text);
        box.setTextSize(13);
        box.setTextColor(color(context, R.color.text));
        return box;
    }

    private static TextView note(Context context, String text) {
        TextView note = new TextView(context);
        note.setText(text);
        note.setTextSize(11.5f);
        note.setTextColor(color(context, R.color.text_dim));
        note.setLineSpacing(dp(context, 2), 1f);
        return note;
    }

    private static TextView fieldLabel(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(color(context, R.color.text_dim));
        return label;
    }

    private static TextView chip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(11);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(color(context, R.color.text));
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 32)));
        return chip;
    }

    private static LinearLayout.LayoutParams topMargin(Context context, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, marginDp);
        return params;
    }

    private static LinearLayout.LayoutParams startMargin(Context context, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(context, marginDp));
        return params;
    }

    private static int numberInput() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
    }

    private static float parse(EditText input, float fallback) {
        String value = input.getText().toString().trim().replace(',', '.');
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static int paletteIndexFor(int color) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == color) {
                return i;
            }
        }
        return 0;
    }

    private static String hexColor(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private static float luminance(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return (0.299f * red + 0.587f * green + 0.114f * blue) / 255f;
    }

    private static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    private static void showKeyboard(Context context, View view) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
