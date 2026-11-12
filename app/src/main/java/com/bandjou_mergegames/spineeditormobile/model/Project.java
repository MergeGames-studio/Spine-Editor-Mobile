package com.bandjou_mergegames.spineeditormobile.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Project {
    public static final String FORMAT = "spine-editor-mobile";
    public static final int FORMAT_VERSION = 1;

    public String name = "Untitled";
    public float width = 800f;
    public float height = 600f;
    public int fps = 30;
    public String activeAnimation;
    public String activeSkin = "default";

    public final List<Bone> bones = new ArrayList<>();
    public final List<Slot> slots = new ArrayList<>();
    public final List<Attachment> attachments = new ArrayList<>();
    public final List<Animation> animations = new ArrayList<>();
    public final List<Skin> skins = new ArrayList<>();

    public Bone findBone(String name) {
        if (name == null) {
            return null;
        }
        for (Bone bone : bones) {
            if (name.equals(bone.name)) {
                return bone;
            }
        }
        return null;
    }

    public Slot findSlot(String name) {
        if (name == null) {
            return null;
        }
        for (Slot slot : slots) {
            if (name.equals(slot.name)) {
                return slot;
            }
        }
        return null;
    }

    public Attachment findAttachment(String name) {
        if (name == null) {
            return null;
        }
        for (Attachment attachment : attachments) {
            if (name.equals(attachment.name)) {
                return attachment;
            }
        }
        return null;
    }

    public Attachment attachmentForSlot(Slot slot) {
        if (slot == null || slot.attachment == null) {
            return null;
        }
        for (Attachment attachment : attachments) {
            if (slot.name.equals(attachment.slot) && slot.attachment.equals(attachment.name)) {
                return attachment;
            }
        }
        return null;
    }

    public Animation findAnimation(String name) {
        if (name == null) {
            return null;
        }
        for (Animation animation : animations) {
            if (name.equals(animation.name)) {
                return animation;
            }
        }
        return null;
    }

    public Skin findSkin(String name) {
        if (name == null) {
            return null;
        }
        for (Skin skin : skins) {
            if (name.equals(skin.name)) {
                return skin;
            }
        }
        return null;
    }

    public Skin getActiveSkin() {
        Skin skin = findSkin(activeSkin);
        if (skin == null) {
            ensureDefaultSkin();
            skin = findSkin(activeSkin);
        }
        return skin;
    }

    public void ensureDefaultSkin() {
        if (skins.isEmpty()) {
            Skin skin = new Skin("default");
            skins.add(skin);
        }
        if (activeSkin == null || findSkin(activeSkin) == null) {
            activeSkin = skins.get(0).name;
        }
        syncCurrentSlotsToActiveSkin();
    }

    public void syncCurrentSlotsToActiveSkin() {
        Skin skin = getActiveSkin();
        if (skin == null) {
            return;
        }
        for (Slot slot : slots) {
            if (slot == null || slot.name == null) {
                continue;
            }
            skin.attachments.put(slot.name, slot.attachment == null ? "" : slot.attachment);
        }
    }

    public void setActiveSkin(String name) {
        Skin skin = findSkin(name);
        if (skin == null) {
            return;
        }
        syncCurrentSlotsToActiveSkin();
        activeSkin = skin.name;
        for (Slot slot : slots) {
            if (slot == null || slot.name == null) {
                continue;
            }
            String value = skin.attachments.get(slot.name);
            if (value != null) {
                slot.attachment = value.isEmpty() ? null : value;
            }
        }
    }

    public void setSlotAttachmentForActiveSkin(String slotName, String attachmentName) {
        Slot slot = findSlot(slotName);
        if (slot == null) {
            return;
        }
        slot.attachment = attachmentName;
        syncCurrentSlotsToActiveSkin();
    }

    public java.util.List<Attachment> attachmentsForSlot(String slotName) {
        java.util.List<Attachment> result = new java.util.ArrayList<>();
        if (slotName == null) {
            return result;
        }
        for (Attachment attachment : attachments) {
            if (slotName.equals(attachment.slot)) {
                result.add(attachment);
            }
        }
        return result;
    }

    public List<Slot> slotsForBone(String boneName) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (boneName != null && boneName.equals(slot.bone)) {
                result.add(slot);
            }
        }
        return result;
    }

    public boolean isDescendant(String boneName, String possibleAncestor) {
        Bone bone = findBone(boneName);
        Set<String> visited = new HashSet<>();
        while (bone != null && bone.parent != null && visited.add(bone.name)) {
            if (bone.parent.equals(possibleAncestor)) {
                return true;
            }
            bone = findBone(bone.parent);
        }
        return false;
    }

    public int boneDepth(Bone bone) {
        int depth = 0;
        Set<String> visited = new HashSet<>();
        while (bone != null && bone.parent != null && visited.add(bone.name)) {
            depth++;
            bone = findBone(bone.parent);
        }
        return depth;
    }

    public void ensureRoot() {
        if (bones.isEmpty()) {
            Bone root = new Bone("root", null);
            root.rotation = 90f;
            root.length = 60f;
            root.color = 0xFF8B98A5;
            bones.add(root);
        }
    }

    public void prune() {
        ensureDefaultSkin();
        Set<String> boneNames = new HashSet<>();
        Set<String> uniqueBoneNames = new HashSet<>();
        for (Bone bone : bones) {
            if (bone.name == null || bone.name.trim().isEmpty()) {
                bone.name = "bone";
            }
            String base = bone.name;
            int suffix = 2;
            while (!uniqueBoneNames.add(bone.name)) {
                bone.name = base + suffix++;
            }
            boneNames.add(bone.name);
        }
        for (Bone bone : bones) {
            if (bone.parent != null && !boneNames.contains(bone.parent)) {
                bone.parent = null;
            }
            if (bone.parent != null && bone.parent.equals(bone.name)) {
                bone.parent = null;
            }
        }

        Set<String> uniqueSlotNames = new HashSet<>();
        Iterator<Slot> slotIterator = slots.iterator();
        while (slotIterator.hasNext()) {
            Slot slot = slotIterator.next();
            if (slot.name == null || slot.name.trim().isEmpty()) {
                slot.name = "slot";
            }
            String base = slot.name;
            int suffix = 2;
            while (!uniqueSlotNames.add(slot.name)) {
                slot.name = base + suffix++;
            }
            if (slot.bone == null || !boneNames.contains(slot.bone)) {
                slotIterator.remove();
            }
        }

        Set<String> uniqueAttachmentNames = new HashSet<>();
        Iterator<Attachment> attachmentIterator = attachments.iterator();
        while (attachmentIterator.hasNext()) {
            Attachment attachment = attachmentIterator.next();
            if (findSlot(attachment.slot) == null) {
                attachmentIterator.remove();
                continue;
            }
            if (attachment.name == null || attachment.name.trim().isEmpty()) {
                attachment.name = "shape";
            }
            String oldName = attachment.name;
            String base = attachment.name;
            int suffix = 2;
            while (!uniqueAttachmentNames.add(attachment.name)) {
                attachment.name = base + suffix++;
            }
            if (!oldName.equals(attachment.name)) {
                for (Slot slot : slots) {
                    if (slot.name.equals(attachment.slot) && oldName.equals(slot.attachment)) {
                        slot.attachment = attachment.name;
                    }
                }
            }
        }

        for (Slot slot : slots) {
            Attachment active = attachmentForSlot(slot);
            if (active == null) {
                Attachment first = null;
                for (Attachment attachment : attachments) {
                    if (slot.name.equals(attachment.slot)) {
                        first = attachment;
                        break;
                    }
                }
                slot.attachment = first == null ? null : first.name;
            }
        }

        Set<String> slotNames = new HashSet<>();
        for (Slot slot : slots) {
            slotNames.add(slot.name);
        }
        Set<String> attachmentKeys = new HashSet<>();
        for (Attachment attachment : attachments) {
            attachmentKeys.add(attachment.slot + "\0" + attachment.name);
        }
        if (skins.isEmpty()) {
            ensureDefaultSkin();
        }
        Set<String> skinNames = new HashSet<>();
        for (Skin skin : new ArrayList<>(skins)) {
            if (skin == null || skin.name == null || skin.name.trim().isEmpty() || !skinNames.add(skin.name)) {
                skins.remove(skin);
            }
        }
        if (skins.isEmpty()) {
            ensureDefaultSkin();
        }
        for (Skin skin : skins) {
            java.util.Iterator<java.util.Map.Entry<String, String>> it = skin.attachments.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, String> e = it.next();
                if (!slotNames.contains(e.getKey())) {
                    it.remove();
                    continue;
                }
                String val = e.getValue();
                if (val != null && !val.isEmpty() && !attachmentKeys.contains(e.getKey() + "\0" + val)) {
                    e.setValue("");
                }
            }
        }
        if (activeSkin == null || findSkin(activeSkin) == null) {
            activeSkin = skins.get(0).name;
        }
        syncCurrentSlotsToActiveSkin();

        Set<String> validSkinNames = new HashSet<>();
        for (Skin skin : skins) {
            if (skin != null && skin.name != null) {
                validSkinNames.add(skin.name);
            }
        }

        Iterator<Animation> animationIterator = animations.iterator();
        while (animationIterator.hasNext()) {
            Animation animation = animationIterator.next();
            animation.bones.keySet().removeIf(name -> !boneNames.contains(name));
            animation.skinKeys.removeIf(key -> key == null || key.skin == null
                    || !validSkinNames.contains(key.skin));
            animation.skinKeys.sort((a, b) -> Float.compare(a.time, b.time));
            animation.duration = Math.max(0.1f, animation.duration);
            if (animation.name == null || animation.name.trim().isEmpty()) {
                animation.name = "animation";
            }
        }

        if (activeAnimation != null && findAnimation(activeAnimation) == null) {
            activeAnimation = null;
        }
    }
}
