package com.bandjou_mergegames.spineeditormobile.data;

import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Slot;

public final class SampleProject {
    private SampleProject() {
    }

    public static Project create() {
        Project project = new Project();
        project.name = "Spine Sample";
        project.width = 800;
        project.height = 600;

        addBone(project, "root", null, 0, 0, 0, 0, 0xFF6E7F8D);
        addBone(project, "torso", "root", 0, 0, 90, 120, 0xFF35D0BA);
        addBone(project, "head", "torso", 120, 0, 0, 45, 0xFFFFB74D);
        addBone(project, "arm_l", "torso", 85, 0, 140, 75, 0xFF64B5F6);
        addBone(project, "arm_r", "torso", 85, 0, -140, 75, 0xFF64B5F6);
        addBone(project, "leg_l", "root", -20, 0, -85, 85, 0xFFBA68C8);
        addBone(project, "leg_r", "root", 20, 0, -95, 85, 0xFFBA68C8);

        addShape(project, "pelvis", "root", "pelvis_shape", "ellipse", 0, 0, 0, 74, 34, 0xFF1F6F64);
        addShape(project, "leg_l_slot", "leg_l", "leg_l_shape", "ellipse", 42, 0, 0, 86, 24, 0xFFBA68C8);
        addShape(project, "leg_r_slot", "leg_r", "leg_r_shape", "ellipse", 42, 0, 0, 86, 24, 0xFFBA68C8);
        addShape(project, "torso_slot", "torso", "torso_shape", "ellipse", 60, 0, 0, 126, 64, 0xFF2BB39B);
        addShape(project, "arm_l_slot", "arm_l", "arm_l_shape", "ellipse", 37, 0, 0, 76, 20, 0xFF64B5F6);
        addShape(project, "arm_r_slot", "arm_r", "arm_r_shape", "ellipse", 37, 0, 0, 76, 20, 0xFF64B5F6);
        addShape(project, "head_slot", "head", "head_shape", "ellipse", 22, 0, 0, 54, 54, 0xFFFFB74D);
        addShape(project, "eye_l_slot", "head", "eye_l", "ellipse", 32, 11, 0, 9, 9, 0xFF10161D);
        addShape(project, "eye_r_slot", "head", "eye_r", "ellipse", 32, -11, 0, 9, 9, 0xFF10161D);

        Animation idle = new Animation("Idle", 2f);
        idle.loop = true;
        addRotationKey(project, idle, "torso", 0f, 90f);
        addRotationKey(project, idle, "torso", 1f, 92.5f);
        addRotationKey(project, idle, "torso", 2f, 90f);
        addRotationKey(project, idle, "head", 0f, 0f);
        addRotationKey(project, idle, "head", 1f, -4f);
        addRotationKey(project, idle, "head", 2f, 0f);
        addRotationKey(project, idle, "arm_l", 0f, 140f);
        addRotationKey(project, idle, "arm_l", 1f, 150f);
        addRotationKey(project, idle, "arm_l", 2f, 140f);
        addRotationKey(project, idle, "arm_r", 0f, -140f);
        addRotationKey(project, idle, "arm_r", 1f, -150f);
        addRotationKey(project, idle, "arm_r", 2f, -140f);
        addRotationKey(project, idle, "leg_l", 0f, -85f);
        addRotationKey(project, idle, "leg_l", 1f, -83f);
        addRotationKey(project, idle, "leg_l", 2f, -85f);
        addRotationKey(project, idle, "leg_r", 0f, -95f);
        addRotationKey(project, idle, "leg_r", 1f, -97f);
        addRotationKey(project, idle, "leg_r", 2f, -95f);
        project.animations.add(idle);
        project.activeAnimation = idle.name;

        project.prune();
        return project;
    }

    public static Project empty() {
        Project project = new Project();
        Bone root = new Bone("root", null);
        root.rotation = 90f;
        root.length = 80f;
        root.color = 0xFF8B98A5;
        project.bones.add(root);
        project.prune();
        return project;
    }

    private static void addBone(Project project, String name, String parent, float x, float y,
                                float rotation, float length, int color) {
        Bone bone = new Bone(name, parent);
        bone.x = x;
        bone.y = y;
        bone.rotation = rotation;
        bone.length = length;
        bone.color = color;
        project.bones.add(bone);
    }

    private static void addShape(Project project, String slotName, String boneName, String attachmentName,
                                 String shape, float x, float y, float rotation, float width, float height,
                                 int color) {
        Slot slot = new Slot(slotName, boneName);
        slot.attachment = attachmentName;
        project.slots.add(slot);

        Attachment attachment = new Attachment(attachmentName, slotName);
        attachment.shape = shape;
        attachment.x = x;
        attachment.y = y;
        attachment.rotation = rotation;
        attachment.width = width;
        attachment.height = height;
        attachment.color = color;
        project.attachments.add(attachment);
    }

    private static void addRotationKey(Project project, Animation animation, String boneName, float time,
                                       float rotation) {
        Bone bone = project.findBone(boneName);
        if (bone == null) {
            return;
        }
        animation.setKey(boneName, new Animation.BoneKey(time, bone.x, bone.y, rotation, bone.scaleX, bone.scaleY));
    }
}
