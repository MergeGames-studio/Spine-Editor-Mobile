package com.bandjou_mergegames.spineeditormobile.data;

import com.bandjou_mergegames.spineeditormobile.model.Animation;
import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Bone;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Skin;
import com.bandjou_mergegames.spineeditormobile.model.Slot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SpineJsonCodec {
    private static final int[] COLOR_PALETTE = {
            0xFF35D0BA, 0xFFFFB74D, 0xFF64B5F6, 0xFFBA68C8,
            0xFF4DD0E1, 0xFFE57373, 0xFFAED581, 0xFFFFF176
    };

    private SpineJsonCodec() {
    }

    public static String toSpineJson(Project project) {
        try {
            if (project != null) {
                project.syncCurrentSlotsToActiveSkin();
            }
            JSONObject root = new JSONObject();
            JSONObject skeleton = new JSONObject();
            skeleton.put("spine", "4.1.24");
            skeleton.put("width", project.width);
            skeleton.put("height", project.height);
            skeleton.put("fps", project.fps);
            root.put("skeleton", skeleton);

            JSONArray bones = new JSONArray();
            for (Bone bone : project.bones) {
                JSONObject map = new JSONObject();
                map.put("name", bone.name);
                if (bone.parent != null) {
                    map.put("parent", bone.parent);
                }
                map.put("length", bone.length);
                map.put("x", bone.x);
                map.put("y", bone.y);
                map.put("rotation", bone.rotation);
                map.put("scaleX", bone.scaleX);
                map.put("scaleY", bone.scaleY);
                map.put("color", toSpineColor(bone.color));
                bones.put(map);
            }
            root.put("bones", bones);

            Skin setupSkin = project.findSkin("default");
            if (setupSkin == null) {
                setupSkin = project.getActiveSkin();
            }
            JSONArray slots = new JSONArray();
            for (Slot slot : project.slots) {
                JSONObject map = new JSONObject();
                map.put("name", slot.name);
                map.put("bone", slot.bone);
                String attachmentName = setupSkin == null ? slot.attachment : setupSkin.attachments.get(slot.name);
                if (slot.visible && attachmentName != null && !attachmentName.isEmpty()) {
                    Attachment attachment = project.findAttachment(attachmentName);
                    if (attachment != null && slot.name.equals(attachment.slot) && attachment.visible) {
                        map.put("attachment", attachmentName);
                    }
                }
                map.put("color", toSpineColor(slot.color));
                slots.put(map);
            }
            root.put("slots", slots);

            JSONArray skins = new JSONArray();
            if (project.skins.isEmpty()) {
                project.ensureDefaultSkin();
            }
            List<Skin> orderedSkins = new ArrayList<>();
            if (setupSkin != null) {
                orderedSkins.add(setupSkin);
            }
            for (Skin skin : project.skins) {
                if (skin != null && skin != setupSkin) {
                    orderedSkins.add(skin);
                }
            }
            for (Skin skin : orderedSkins) {
                if (skin == null || skin.name == null) {
                    continue;
                }
                JSONObject attachmentsBySlot = new JSONObject();
                for (Slot slot : project.slots) {
                    if (slot == null || slot.name == null) {
                        continue;
                    }
                    String attachmentName = skin.attachments.get(slot.name);
                    if (attachmentName == null || attachmentName.isEmpty()) {
                        continue;
                    }
                    Attachment attachment = project.findAttachment(attachmentName);
                    if (attachment == null || !slot.name.equals(attachment.slot)) {
                        continue;
                    }
                    JSONObject slotMap = attachmentsBySlot.optJSONObject(slot.name);
                    if (slotMap == null) {
                        slotMap = new JSONObject();
                        attachmentsBySlot.put(slot.name, slotMap);
                    }
                    JSONObject map = new JSONObject();
                    map.put("type", "region");
                    String regionName = attachment.region != null && !attachment.region.trim().isEmpty()
                            ? attachment.region.trim() : (attachment.path != null && !attachment.path.trim().isEmpty()
                            ? attachment.path.trim() : attachment.name);
                    map.put("path", regionName);
                    map.put("x", attachment.x);
                    map.put("y", attachment.y);
                    map.put("rotation", attachment.rotation);
                    map.put("width", attachment.width);
                    map.put("height", attachment.height);
                    map.put("color", toSpineColor(attachment.color));
                    slotMap.put(attachment.name, map);
                }
                JSONObject skinJson = new JSONObject();
                skinJson.put("name", skin.name);
                skinJson.put("attachments", attachmentsBySlot);
                skins.put(skinJson);
            }
            root.put("skins", skins);

            JSONObject animations = new JSONObject();
            for (Animation animation : project.animations) {
                JSONObject animationMap = new JSONObject();
                JSONObject boneTimelines = new JSONObject();
                for (Map.Entry<String, ArrayList<Animation.BoneKey>> entry : animation.bones.entrySet()) {
                    Bone setup = project.findBone(entry.getKey());
                    if (setup == null) {
                        continue;
                    }
                    ArrayList<Animation.BoneKey> keys = entry.getValue();
                    if (keys == null || keys.isEmpty()) {
                        continue;
                    }
                    keys.sort((a, b) -> Float.compare(a.time, b.time));

                    boolean hasRotate = false;
                    boolean hasTranslate = false;
                    boolean hasScale = false;
                    for (Animation.BoneKey key : keys) {
                        if (Math.abs(key.rotation - setup.rotation) > 0.0001f) hasRotate = true;
                        if (Math.abs(key.x - setup.x) > 0.0001f || Math.abs(key.y - setup.y) > 0.0001f) hasTranslate = true;
                        if (Math.abs(key.scaleX - setup.scaleX) > 0.0001f || Math.abs(key.scaleY - setup.scaleY) > 0.0001f) hasScale = true;
                    }
                    if (!hasRotate && !hasTranslate && !hasScale) {
                        continue;
                    }

                    JSONObject timelines = new JSONObject();
                    Animation.BoneKey lastKey = keys.get(keys.size() - 1);
                    boolean needsDurationKey = animation.duration > lastKey.time + 0.0001f;

                    if (hasRotate) {
                        JSONArray array = new JSONArray();
                        for (Animation.BoneKey key : keys) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", key.time);
                            frame.put("value", key.rotation);
                            frame.put("angle", key.rotation);
                            array.put(frame);
                        }
                        if (needsDurationKey) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", animation.duration);
                            frame.put("value", lastKey.rotation);
                            frame.put("angle", lastKey.rotation);
                            array.put(frame);
                        }
                        timelines.put("rotate", array);
                    }
                    if (hasTranslate) {
                        JSONArray array = new JSONArray();
                        for (Animation.BoneKey key : keys) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", key.time);
                            frame.put("x", key.x);
                            frame.put("y", key.y);
                            array.put(frame);
                        }
                        if (needsDurationKey) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", animation.duration);
                            frame.put("x", lastKey.x);
                            frame.put("y", lastKey.y);
                            array.put(frame);
                        }
                        timelines.put("translate", array);
                    }
                    if (hasScale) {
                        JSONArray array = new JSONArray();
                        for (Animation.BoneKey key : keys) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", key.time);
                            frame.put("x", key.scaleX);
                            frame.put("y", key.scaleY);
                            array.put(frame);
                        }
                        if (needsDurationKey) {
                            JSONObject frame = new JSONObject();
                            frame.put("time", animation.duration);
                            frame.put("x", lastKey.scaleX);
                            frame.put("y", lastKey.scaleY);
                            array.put(frame);
                        }
                        timelines.put("scale", array);
                    }
                    if (timelines.length() > 0) {
                        boneTimelines.put(entry.getKey(), timelines);
                    }
                }
                animationMap.put("bones", boneTimelines);
                if (animation.drawOrder != null && !animation.drawOrder.isEmpty()) {
                    JSONArray drawOrderArr = new JSONArray();
                    for (Animation.DrawOrderKey key : animation.drawOrder) {
                        if (key == null) continue;
                        JSONObject keyJson = new JSONObject();
                        keyJson.put("time", key.time);
                        if (key.offsets != null && !key.offsets.isEmpty()) {
                            JSONArray offsets = new JSONArray();
                            for (Animation.DrawOrderOffset off : key.offsets) {
                                if (off == null) continue;
                                JSONObject offJson = new JSONObject();
                                if (off.slot != null) offJson.put("slot", off.slot);
                                offJson.put("offset", off.offset);
                                offsets.put(offJson);
                            }
                            if (offsets.length() > 0) keyJson.put("offsets", offsets);
                        }
                        drawOrderArr.put(keyJson);
                    }
                    if (drawOrderArr.length() > 0) animationMap.put("drawOrder", drawOrderArr);
                }
                if (animation.events != null && !animation.events.isEmpty()) {
                    JSONArray eventsArr = new JSONArray();
                    for (Animation.EventKey key : animation.events) {
                        if (key == null) continue;
                        JSONObject keyJson = new JSONObject();
                        keyJson.put("time", key.time);
                        if (key.events != null && !key.events.isEmpty()) {
                            JSONArray list = new JSONArray();
                            for (Animation.EventData ev : key.events) {
                                if (ev == null) continue;
                                JSONObject evJson = new JSONObject();
                                if (ev.name != null) evJson.put("name", ev.name);
                                if (ev.stringValue != null) evJson.put("string", ev.stringValue);
                                evJson.put("int", ev.intValue);
                                evJson.put("float", ev.floatValue);
                                list.put(evJson);
                            }
                            if (list.length() > 0) keyJson.put("events", list);
                        }
                        eventsArr.put(keyJson);
                    }
                    if (eventsArr.length() > 0) animationMap.put("events", eventsArr);
                }
                animations.put(animation.name, animationMap);
            }
            root.put("animations", animations);
            return root.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to export Spine JSON", exception);
        }
    }

    public static Project fromSpineJson(String json, String fallbackName) throws JSONException {
        JSONObject root = new JSONObject(json);
        Project project = new Project();
        project.name = fallbackName == null || fallbackName.trim().isEmpty() ? "Imported Spine" : fallbackName;

        JSONObject skeleton = root.optJSONObject("skeleton");
        if (skeleton != null) {
            project.width = (float) skeleton.optDouble("width", 800);
            project.height = (float) skeleton.optDouble("height", 600);
            project.fps = Math.max(1, skeleton.optInt("fps", 30));
        }

        JSONArray bones = root.optJSONArray("bones");
        if (bones != null) {
            for (int i = 0; i < bones.length(); i++) {
                JSONObject map = bones.optJSONObject(i);
                if (map == null) continue;
                String name = map.optString("name", null);
                if (name == null || name.trim().isEmpty()) continue;
                Bone bone = new Bone(name, optStringOrNull(map, "parent"));
                bone.x = (float) map.optDouble("x", 0);
                bone.y = (float) map.optDouble("y", 0);
                bone.rotation = (float) map.optDouble("rotation", 0);
                bone.scaleX = (float) map.optDouble("scaleX", 1);
                bone.scaleY = (float) map.optDouble("scaleY", 1);
                bone.length = (float) map.optDouble("length", 50);
                String color = optStringOrNull(map, "color");
                if (color != null) bone.color = parseColor(color, 0xFF5C7080);
                project.bones.add(bone);
            }
        }
        project.ensureRoot();

        JSONArray slots = root.optJSONArray("slots");
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject map = slots.optJSONObject(i);
                if (map == null) continue;
                String name = map.optString("name", null);
                String boneName = map.optString("bone", null);
                if (name == null || boneName == null || project.findBone(boneName) == null) continue;
                Slot slot = new Slot(name, boneName);
                slot.color = parseColor(optStringOrNull(map, "color"), 0xFFFFFFFF);
                slot.visible = map.optBoolean("visible", true);
                slot.attachment = optStringOrNull(map, "attachment");
                project.slots.add(slot);
            }
        }

        readSkins(root, project);
        project.prune();

        JSONObject animations = root.optJSONObject("animations");
        if (animations != null) {
            Iterator<String> names = animations.keys();
            while (names.hasNext()) {
                String animationName = names.next();
                JSONObject animationMap = animations.optJSONObject(animationName);
                if (animationMap == null) continue;
                Animation animation = new Animation();
                animation.name = animationName;
                animation.loop = true;
                float maxTime = 0f;

                JSONObject boneMaps = animationMap.optJSONObject("bones");
                if (boneMaps != null) {
                    Iterator<String> boneNames = boneMaps.keys();
                    while (boneNames.hasNext()) {
                        String boneName = boneNames.next();
                        Bone setup = project.findBone(boneName);
                        if (setup == null) continue;
                        JSONObject timelines = boneMaps.optJSONObject(boneName);
                        if (timelines == null) continue;
                        TreeMap<Float, Animation.BoneKey> merged = new TreeMap<>();
                        readRotateTimeline(timelines.optJSONArray("rotate"), setup, merged);
                        readTranslateTimeline(timelines.optJSONArray("translate"), setup, merged);
                        readTranslateXTimeline(timelines.optJSONArray("translatex"), setup, merged);
                        readTranslateYTimeline(timelines.optJSONArray("translatey"), setup, merged);
                        readScaleTimeline(timelines.optJSONArray("scale"), setup, merged);
                        readScaleXTimeline(timelines.optJSONArray("scalex"), setup, merged);
                        readScaleYTimeline(timelines.optJSONArray("scaley"), setup, merged);
                        if (merged.isEmpty()) continue;
                        ArrayList<Animation.BoneKey> keys = new ArrayList<>(merged.values());
                        animation.bones.put(boneName, keys);
                        maxTime = Math.max(maxTime, keys.get(keys.size() - 1).time);
                    }
                }
                maxTime = Math.max(maxTime, scanMaxTime(animationMap));
                JSONArray drawOrderArr = animationMap.optJSONArray("drawOrder");
                if (drawOrderArr != null) {
                    for (int d = 0; d < drawOrderArr.length(); d++) {
                        JSONObject dk = drawOrderArr.optJSONObject(d);
                        if (dk == null) continue;
                        Animation.DrawOrderKey key = new Animation.DrawOrderKey((float) dk.optDouble("time", 0));
                        maxTime = Math.max(maxTime, key.time);
                        JSONArray offsets = dk.optJSONArray("offsets");
                        if (offsets != null) {
                            for (int o = 0; o < offsets.length(); o++) {
                                JSONObject of = offsets.optJSONObject(o);
                                if (of == null) continue;
                                String slot = of.optString("slot", null);
                                if (slot == null) continue;
                                key.offsets.add(new Animation.DrawOrderOffset(slot, of.optInt("offset", 0)));
                            }
                        }
                        animation.drawOrder.add(key);
                    }
                    animation.drawOrder.sort((a, b) -> Float.compare(a.time, b.time));
                }
                JSONArray eventsArr = animationMap.optJSONArray("events");
                if (eventsArr != null) {
                    for (int e = 0; e < eventsArr.length(); e++) {
                        JSONObject ek = eventsArr.optJSONObject(e);
                        if (ek == null) continue;
                        Animation.EventKey key = new Animation.EventKey((float) ek.optDouble("time", 0));
                        maxTime = Math.max(maxTime, key.time);
                        JSONArray list = ek.optJSONArray("events");
                        if (list != null) {
                            for (int l = 0; l < list.length(); l++) {
                                JSONObject ev = list.optJSONObject(l);
                                if (ev == null) continue;
                                Animation.EventData data = new Animation.EventData(ev.optString("name"));
                                data.stringValue = optStringOrNull(ev, "string");
                                data.intValue = ev.optInt("int", 0);
                                data.floatValue = (float) ev.optDouble("float", 0);
                                key.events.add(data);
                            }
                        } else {
                            String name = ek.optString("name", null);
                            if (name != null && !name.isEmpty()) {
                                Animation.EventData data = new Animation.EventData(name);
                                data.stringValue = optStringOrNull(ek, "string");
                                data.intValue = ek.optInt("int", 0);
                                data.floatValue = (float) ek.optDouble("float", 0);
                                key.events.add(data);
                            }
                        }
                        if (!key.events.isEmpty()) animation.events.add(key);
                    }
                    animation.events.sort((a, b) -> Float.compare(a.time, b.time));
                }
                JSONArray skinKeysArr = animationMap.optJSONArray("skinKeys");
                if (skinKeysArr != null) {
                    for (int s = 0; s < skinKeysArr.length(); s++) {
                        JSONObject sk = skinKeysArr.optJSONObject(s);
                        if (sk == null) continue;
                        String skin = optStringOrNull(sk, "skin");
                        if (skin == null) continue;
                        float keyTime = (float) sk.optDouble("time", 0);
                        animation.skinKeys.add(new Animation.SkinKey(keyTime, skin));
                        maxTime = Math.max(maxTime, keyTime);
                    }
                    animation.skinKeys.sort((a, b) -> Float.compare(a.time, b.time));
                }
                animation.duration = Math.max(0.1f, maxTime);
                project.animations.add(animation);
            }
        }

        if (!project.animations.isEmpty()) {
            project.activeAnimation = project.animations.get(0).name;
        }
        project.prune();
        return project;
    }

    private static void readSkins(JSONObject root, Project project) throws JSONException {
        project.skins.clear();
        project.attachments.clear();
        JSONArray skins = root.optJSONArray("skins");
        int colorIndex = 0;
        boolean firstSkin = true;
        if (skins != null) {
            for (int i = 0; i < skins.length(); i++) {
                JSONObject skin = skins.optJSONObject(i);
                if (skin == null) continue;
                String skinName = skin.optString("name", "default");
                if (skinName == null || skinName.trim().isEmpty()) skinName = "skin" + (i + 1);
                Skin skinModel = project.findSkin(skinName);
                if (skinModel == null) {
                    skinModel = new Skin(skinName);
                    project.skins.add(skinModel);
                }
                JSONObject attachments = skin.optJSONObject("attachments");
                colorIndex = readSkinAttachmentsMap(attachments, project, skinModel, colorIndex, firstSkin);
                if (firstSkin) {
                    project.activeSkin = skinName;
                    firstSkin = false;
                }
            }
        } else {
            JSONObject skinsObject = root.optJSONObject("skins");
            if (skinsObject != null) {
                Iterator<String> names = skinsObject.keys();
                while (names.hasNext()) {
                    String skinName = names.next();
                    JSONObject attachments = skinsObject.optJSONObject(skinName);
                    if (attachments == null) continue;
                    Skin skinModel = project.findSkin(skinName);
                    if (skinModel == null) {
                        skinModel = new Skin(skinName);
                        project.skins.add(skinModel);
                    }
                    colorIndex = readSkinAttachmentsMap(attachments, project, skinModel, colorIndex, firstSkin);
                    if (firstSkin || "default".equals(skinName)) {
                        project.activeSkin = skinName;
                        firstSkin = false;
                    }
                }
            }
        }
        if (project.skins.isEmpty()) {
            project.ensureDefaultSkin();
        }
        if (project.activeSkin == null || project.findSkin(project.activeSkin) == null) {
            project.activeSkin = project.skins.get(0).name;
        }
        Skin active = project.findSkin(project.activeSkin);
        if (active != null) {
            for (Slot slot : project.slots) {
                if (slot == null) continue;
                String value = active.attachments.get(slot.name);
                if (value != null) {
                    slot.attachment = value.isEmpty() ? null : value;
                }
            }
        }
    }

    private static int readSkinAttachmentsMap(JSONObject attachmentsBySlot, Project project, Skin skinModel,
                                              int colorIndex, boolean applyToSlots) {
        if (attachmentsBySlot == null) return colorIndex;
        Iterator<String> slotNames = attachmentsBySlot.keys();
        while (slotNames.hasNext()) {
            String slotName = slotNames.next();
            Slot slot = project.findSlot(slotName);
            JSONObject slotAttachments = attachmentsBySlot.optJSONObject(slotName);
            if (slot == null || slotAttachments == null) continue;
            Iterator<String> attachmentNames = slotAttachments.keys();
            while (attachmentNames.hasNext()) {
                String attachmentName = attachmentNames.next();
                JSONObject attachmentMap = slotAttachments.optJSONObject(attachmentName);
                if (attachmentMap == null) continue;
                Attachment existing = project.findAttachment(attachmentName);
                if (existing == null) {
                    Attachment attachment = readAttachment(attachmentName, slotName, attachmentMap, colorIndex++);
                    if (attachment != null) project.attachments.add(attachment);
                } else if (!slotName.equals(existing.slot)) {
                    // duplicate name in different slot: keep first, ignore
                }
            }
            if (applyToSlots && (slot.attachment == null || slot.attachment.isEmpty())) {
                Iterator<String> keys = slotAttachments.keys();
                if (keys.hasNext()) slot.attachment = keys.next();
            }
            String active = slot.attachment;
            if (active != null && slotAttachments.has(active)) {
                skinModel.attachments.put(slotName, active);
            } else if (applyToSlots && slot.attachment != null) {
                skinModel.attachments.put(slotName, slot.attachment);
            }
        }
        return colorIndex;
    }

    private static Attachment readAttachment(String name, String slotName, JSONObject map, int colorIndex) {
        String type = map.optString("type", "region");
        if (!"region".equals(type) && !"mesh".equals(type) && !"linkedmesh".equals(type)
                && !"point".equals(type)) {
            // still try to import as region-ish if has size
        }
        Attachment attachment = new Attachment(name, slotName);
        attachment.type = Attachment.TYPE_REGION;
        attachment.x = (float) map.optDouble("x", 0);
        attachment.y = (float) map.optDouble("y", 0);
        attachment.rotation = (float) map.optDouble("rotation", 0);
        attachment.width = (float) map.optDouble("width", 50);
        attachment.height = (float) map.optDouble("height", 40);
        attachment.shape = map.optString("shape", Attachment.SHAPE_RECT);
        String color = optStringOrNull(map, "color");
        attachment.path = optStringOrNull(map, "path");
        String region = optStringOrNull(map, "path");
        if (region != null && !region.isEmpty()) attachment.region = region;
        else attachment.region = attachment.name;
        attachment.color = parseColor(color, COLOR_PALETTE[colorIndex % COLOR_PALETTE.length]);
        return attachment;
    }

    private static void readRotateTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            if (map.has("value") && !map.isNull("value")) key.rotation = (float) map.optDouble("value", setup.rotation);
            else key.rotation = (float) map.optDouble("angle", setup.rotation);
        }
    }

    private static void readTranslateTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.x = (float) map.optDouble("x", setup.x);
            key.y = (float) map.optDouble("y", setup.y);
        }
    }

    private static void readTranslateXTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.x = (float) map.optDouble("value", setup.x);
        }
    }

    private static void readTranslateYTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.y = (float) map.optDouble("value", setup.y);
        }
    }

    private static void readScaleTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.scaleX = (float) map.optDouble("x", setup.scaleX);
            key.scaleY = (float) map.optDouble("y", setup.scaleY);
        }
    }

    private static void readScaleXTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.scaleX = (float) map.optDouble("value", setup.scaleX);
        }
    }

    private static void readScaleYTimeline(JSONArray array, Bone setup, TreeMap<Float, Animation.BoneKey> merged) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject map = array.optJSONObject(i);
            if (map == null) continue;
            float time = (float) map.optDouble("time", 0);
            Animation.BoneKey key = keyFor(merged, time, setup);
            key.scaleY = (float) map.optDouble("value", setup.scaleY);
        }
    }

    private static Animation.BoneKey keyFor(TreeMap<Float, Animation.BoneKey> merged, float time, Bone setup) {
        Animation.BoneKey key = merged.get(time);
        if (key == null) {
            key = new Animation.BoneKey(time, setup.x, setup.y, setup.rotation, setup.scaleX, setup.scaleY);
            merged.put(time, key);
        }
        return key;
    }

    private static float scanMaxTime(Object value) {
        float max = 0f;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("time") && !object.isNull("time")) {
                max = Math.max(max, (float) object.optDouble("time", 0));
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                max = Math.max(max, scanMaxTime(object.opt(keys.next())));
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                max = Math.max(max, scanMaxTime(array.opt(i)));
            }
        }
        return max;
    }

    private static String optStringOrNull(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String color = value.trim();
        if (color.startsWith("#")) color = color.substring(1);
        try {
            if (color.length() == 6) {
                int rgb = Integer.parseInt(color, 16);
                return 0xFF000000 | rgb;
            }
            if (color.length() == 8) {
                long rgba = Long.parseLong(color, 16);
                int r = (int) ((rgba >> 24) & 0xFF);
                int g = (int) ((rgba >> 16) & 0xFF);
                int b = (int) ((rgba >> 8) & 0xFF);
                int a = (int) (rgba & 0xFF);
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static String toSpineColor(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return String.format("%02x%02x%02x%02x", r, g, b, a);
    }
}
