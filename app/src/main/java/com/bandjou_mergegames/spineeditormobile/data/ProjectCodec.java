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
import java.util.Map;

public final class ProjectCodec {
    private ProjectCodec() {
    }

    public static String toJson(Project project) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", Project.FORMAT);
            root.put("version", Project.FORMAT_VERSION);
            root.put("name", project.name);
            root.put("width", project.width);
            root.put("height", project.height);
            root.put("fps", project.fps);
            if (project.activeAnimation != null) {
                root.put("activeAnimation", project.activeAnimation);
            }
            if (project.activeSkin != null) {
                root.put("activeSkin", project.activeSkin);
            }

            JSONArray bones = new JSONArray();
            for (Bone bone : project.bones) {
                JSONObject map = new JSONObject();
                map.put("name", bone.name);
                if (bone.parent != null) {
                    map.put("parent", bone.parent);
                }
                map.put("x", bone.x);
                map.put("y", bone.y);
                map.put("rotation", bone.rotation);
                map.put("scaleX", bone.scaleX);
                map.put("scaleY", bone.scaleY);
                map.put("length", bone.length);
                map.put("color", bone.color);
                map.put("visible", bone.visible);
                bones.put(map);
            }
            root.put("bones", bones);

            JSONArray slots = new JSONArray();
            for (Slot slot : project.slots) {
                JSONObject map = new JSONObject();
                map.put("name", slot.name);
                map.put("bone", slot.bone);
                if (slot.attachment != null) {
                    map.put("attachment", slot.attachment);
                }
                map.put("color", slot.color);
                map.put("visible", slot.visible);
                slots.put(map);
            }
            root.put("slots", slots);

            JSONArray attachments = new JSONArray();
            for (Attachment attachment : project.attachments) {
                JSONObject map = new JSONObject();
                map.put("name", attachment.name);
                map.put("slot", attachment.slot);
                map.put("type", attachment.type);
                map.put("shape", attachment.shape);
                map.put("x", attachment.x);
                map.put("y", attachment.y);
                map.put("rotation", attachment.rotation);
                map.put("width", attachment.width);
                map.put("height", attachment.height);
                map.put("color", attachment.color);
                map.put("visible", attachment.visible);
                if (attachment.path != null) {
                    map.put("path", attachment.path);
                }
                attachments.put(map);
            }
            root.put("attachments", attachments);

            project.syncCurrentSlotsToActiveSkin();
            JSONArray skins = new JSONArray();
            for (Skin skin : project.skins) {
                if (skin == null || skin.name == null) {
                    continue;
                }
                JSONObject map = new JSONObject();
                map.put("name", skin.name);
                JSONObject attachMap = new JSONObject();
                for (Map.Entry<String, String> entry : skin.attachments.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    attachMap.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
                }
                map.put("attachments", attachMap);
                skins.put(map);
            }
            if (skins.length() > 0) {
                root.put("skins", skins);
            }

            JSONArray animations = new JSONArray();
            for (Animation animation : project.animations) {
                JSONObject map = new JSONObject();
                map.put("name", animation.name);
                map.put("duration", animation.duration);
                map.put("loop", animation.loop);
                JSONObject boneMaps = new JSONObject();
                for (Map.Entry<String, ArrayList<Animation.BoneKey>> entry : animation.bones.entrySet()) {
                    ArrayList<Animation.BoneKey> vals = entry.getValue();
                    if (vals == null || vals.isEmpty()) {
                        continue;
                    }
                    JSONArray keys = new JSONArray();
                    for (Animation.BoneKey key : vals) {
                        JSONObject keyMap = new JSONObject();
                        keyMap.put("time", key.time);
                        keyMap.put("x", key.x);
                        keyMap.put("y", key.y);
                        keyMap.put("rotation", key.rotation);
                        keyMap.put("scaleX", key.scaleX);
                        keyMap.put("scaleY", key.scaleY);
                        keys.put(keyMap);
                    }
                    boneMaps.put(entry.getKey(), keys);
                }
                if (boneMaps.length() > 0) {
                    map.put("bones", boneMaps);
                }
                if (animation.drawOrder != null && !animation.drawOrder.isEmpty()) {
                    JSONArray drawOrderArr = new JSONArray();
                    for (Animation.DrawOrderKey key : animation.drawOrder) {
                        if (key == null) {
                            continue;
                        }
                        JSONObject keyJson = new JSONObject();
                        keyJson.put("time", key.time);
                        if (key.offsets != null && !key.offsets.isEmpty()) {
                            JSONArray offsets = new JSONArray();
                            for (Animation.DrawOrderOffset off : key.offsets) {
                                if (off == null) {
                                    continue;
                                }
                                JSONObject offJson = new JSONObject();
                                if (off.slot != null) {
                                    offJson.put("slot", off.slot);
                                }
                                offJson.put("offset", off.offset);
                                offsets.put(offJson);
                            }
                            if (offsets.length() > 0) {
                                keyJson.put("offsets", offsets);
                            }
                        }
                        drawOrderArr.put(keyJson);
                    }
                    if (drawOrderArr.length() > 0) {
                        map.put("drawOrder", drawOrderArr);
                    }
                }
                if (animation.events != null && !animation.events.isEmpty()) {
                    JSONArray eventsArr = new JSONArray();
                    for (Animation.EventKey key : animation.events) {
                        if (key == null) {
                            continue;
                        }
                        JSONObject keyJson = new JSONObject();
                        keyJson.put("time", key.time);
                        if (key.events != null && !key.events.isEmpty()) {
                            JSONArray list = new JSONArray();
                            for (Animation.EventData ev : key.events) {
                                if (ev == null) {
                                    continue;
                                }
                                JSONObject evJson = new JSONObject();
                                if (ev.name != null) {
                                    evJson.put("name", ev.name);
                                }
                                if (ev.stringValue != null) {
                                    evJson.put("string", ev.stringValue);
                                }
                                evJson.put("int", ev.intValue);
                                evJson.put("float", ev.floatValue);
                                list.put(evJson);
                            }
                            if (list.length() > 0) {
                                keyJson.put("events", list);
                            }
                        }
                        eventsArr.put(keyJson);
                    }
                    if (eventsArr.length() > 0) {
                        map.put("events", eventsArr);
                    }
                }
                if (animation.skinKeys != null && !animation.skinKeys.isEmpty()) {
                    JSONArray skinKeysArr = new JSONArray();
                    for (Animation.SkinKey key : animation.skinKeys) {
                        if (key == null || key.skin == null) {
                            continue;
                        }
                        JSONObject keyJson = new JSONObject();
                        keyJson.put("time", key.time);
                        keyJson.put("skin", key.skin);
                        skinKeysArr.put(keyJson);
                    }
                    if (skinKeysArr.length() > 0) {
                        map.put("skinKeys", skinKeysArr);
                    }
                }
                animations.put(map);
            }
            root.put("animations", animations);
            return root.toString(2);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to serialize project", exception);
        }
    }

    public static Project fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        Project project = new Project();
        project.name = root.optString("name", "Untitled");
        project.width = (float) root.optDouble("width", 800);
        project.height = (float) root.optDouble("height", 600);
        project.fps = Math.max(1, root.optInt("fps", 30));

        JSONArray bones = root.optJSONArray("bones");
        if (bones != null) {
            for (int i = 0; i < bones.length(); i++) {
                JSONObject map = bones.optJSONObject(i);
                if (map == null) {
                    continue;
                }
                String name = map.optString("name", null);
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                Bone bone = new Bone(name, optStringOrNull(map, "parent"));
                bone.x = (float) map.optDouble("x", 0);
                bone.y = (float) map.optDouble("y", 0);
                bone.rotation = (float) map.optDouble("rotation", 0);
                bone.scaleX = (float) map.optDouble("scaleX", 1);
                bone.scaleY = (float) map.optDouble("scaleY", 1);
                bone.length = (float) map.optDouble("length", 50);
                bone.color = map.optInt("color", 0xFF5C7080);
                bone.visible = map.optBoolean("visible", true);
                project.bones.add(bone);
            }
        }

        JSONArray slots = root.optJSONArray("slots");
        if (slots != null) {
            for (int i = 0; i < slots.length(); i++) {
                JSONObject map = slots.optJSONObject(i);
                if (map == null) {
                    continue;
                }
                String name = map.optString("name", null);
                String bone = map.optString("bone", null);
                if (name == null || name.trim().isEmpty() || bone == null) {
                    continue;
                }
                Slot slot = new Slot(name, bone);
                slot.attachment = optStringOrNull(map, "attachment");
                slot.color = map.optInt("color", 0xFFFFFFFF);
                slot.visible = map.optBoolean("visible", true);
                project.slots.add(slot);
            }
        }

        JSONArray attachments = root.optJSONArray("attachments");
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject map = attachments.optJSONObject(i);
                if (map == null) {
                    continue;
                }
                String name = map.optString("name", null);
                String slot = map.optString("slot", null);
                if (name == null || name.trim().isEmpty() || slot == null) {
                    continue;
                }
                Attachment attachment = new Attachment(name, slot);
                attachment.type = map.optString("type", Attachment.TYPE_REGION);
                attachment.shape = map.optString("shape", Attachment.SHAPE_RECT);
                attachment.x = (float) map.optDouble("x", 0);
                attachment.y = (float) map.optDouble("y", 0);
                attachment.rotation = (float) map.optDouble("rotation", 0);
                attachment.width = (float) map.optDouble("width", 50);
                attachment.height = (float) map.optDouble("height", 40);
                attachment.color = map.optInt("color", 0xFF35D0BA);
                attachment.visible = map.optBoolean("visible", true);
                attachment.path = optStringOrNull(map, "path");
                attachment.region = optStringOrNull(map, "region");
                project.attachments.add(attachment);
            }
        }

        JSONArray animations = root.optJSONArray("animations");
        if (animations != null) {
            for (int i = 0; i < animations.length(); i++) {
                JSONObject map = animations.optJSONObject(i);
                if (map == null) {
                    continue;
                }
                Animation animation = new Animation();
                animation.name = map.optString("name", "animation");
                animation.duration = (float) map.optDouble("duration", 1);
                animation.loop = map.optBoolean("loop", true);
                JSONObject boneMaps = map.optJSONObject("bones");
                if (boneMaps != null) {
                    JSONArray names = boneMaps.names();
                    if (names != null) {
                        for (int n = 0; n < names.length(); n++) {
                            String boneName = names.optString(n);
                            JSONArray keys = boneMaps.optJSONArray(boneName);
                            if (keys == null) {
                                continue;
                            }
                            ArrayList<Animation.BoneKey> boneKeys = new ArrayList<>();
                            for (int k = 0; k < keys.length(); k++) {
                                JSONObject keyMap = keys.optJSONObject(k);
                                if (keyMap == null) {
                                    continue;
                                }
                                Animation.BoneKey key = new Animation.BoneKey();
                                key.time = (float) keyMap.optDouble("time", 0);
                                key.x = (float) keyMap.optDouble("x", 0);
                                key.y = (float) keyMap.optDouble("y", 0);
                                key.rotation = (float) keyMap.optDouble("rotation", 0);
                                key.scaleX = (float) keyMap.optDouble("scaleX", 1);
                                key.scaleY = (float) keyMap.optDouble("scaleY", 1);
                                boneKeys.add(key);
                            }
                            boneKeys.sort((a, b) -> Float.compare(a.time, b.time));
                            animation.bones.put(boneName, boneKeys);
                        }
                    }
                }
                JSONArray drawOrderArr = map.optJSONArray("drawOrder");
                if (drawOrderArr != null) {
                    for (int d = 0; d < drawOrderArr.length(); d++) {
                        JSONObject dk = drawOrderArr.optJSONObject(d);
                        if (dk == null) {
                            continue;
                        }
                        Animation.DrawOrderKey key = new Animation.DrawOrderKey((float) dk.optDouble("time", 0));
                        JSONArray offsets = dk.optJSONArray("offsets");
                        if (offsets != null) {
                            for (int o = 0; o < offsets.length(); o++) {
                                JSONObject of = offsets.optJSONObject(o);
                                if (of == null) {
                                    continue;
                                }
                                String slot = of.optString("slot", null);
                                if (slot == null) {
                                    continue;
                                }
                                key.offsets.add(new Animation.DrawOrderOffset(slot, of.optInt("offset", 0)));
                            }
                        }
                        animation.drawOrder.add(key);
                    }
                    animation.drawOrder.sort((a, b) -> Float.compare(a.time, b.time));
                }
                JSONArray eventsArr = map.optJSONArray("events");
                if (eventsArr != null) {
                    for (int e = 0; e < eventsArr.length(); e++) {
                        JSONObject ek = eventsArr.optJSONObject(e);
                        if (ek == null) {
                            continue;
                        }
                        Animation.EventKey key = new Animation.EventKey((float) ek.optDouble("time", 0));
                        JSONArray list = ek.optJSONArray("events");
                        if (list != null) {
                            for (int l = 0; l < list.length(); l++) {
                                JSONObject ev = list.optJSONObject(l);
                                if (ev == null) {
                                    continue;
                                }
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
                        if (!key.events.isEmpty()) {
                            animation.events.add(key);
                        }
                    }
                    animation.events.sort((a, b) -> Float.compare(a.time, b.time));
                }
                JSONArray skinKeysArr = map.optJSONArray("skinKeys");
                if (skinKeysArr != null) {
                    for (int s = 0; s < skinKeysArr.length(); s++) {
                        JSONObject sk = skinKeysArr.optJSONObject(s);
                        if (sk == null) {
                            continue;
                        }
                        String skin = optStringOrNull(sk, "skin");
                        if (skin == null) {
                            continue;
                        }
                        animation.skinKeys.add(new Animation.SkinKey(
                                (float) sk.optDouble("time", 0), skin));
                    }
                    animation.skinKeys.sort((a, b) -> Float.compare(a.time, b.time));
                }
                animation.duration = Math.max(0.1f, animation.duration);
                project.animations.add(animation);
            }
        }

        JSONArray skins = root.optJSONArray("skins");
        if (skins != null) {
            for (int i = 0; i < skins.length(); i++) {
                JSONObject map = skins.optJSONObject(i);
                if (map == null) {
                    continue;
                }
                String name = map.optString("name", null);
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                Skin skin = new Skin(name);
                JSONObject attachMap = map.optJSONObject("attachments");
                if (attachMap != null) {
                    JSONArray names = attachMap.names();
                    if (names != null) {
                        for (int n = 0; n < names.length(); n++) {
                            String slotName = names.optString(n);
                            if (slotName == null) {
                                continue;
                            }
                            Object val = attachMap.opt(slotName);
                            if (val == null || val == JSONObject.NULL) {
                                skin.attachments.put(slotName, "");
                            } else {
                                String s = String.valueOf(val);
                                skin.attachments.put(slotName, s);
                            }
                        }
                    }
                }
                project.skins.add(skin);
            }
        }
        project.activeSkin = optStringOrNull(root, "activeSkin");
        project.activeAnimation = optStringOrNull(root, "activeAnimation");
        project.prune();
        project.ensureRoot();
        return project;
    }

    private static String optStringOrNull(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }
}
