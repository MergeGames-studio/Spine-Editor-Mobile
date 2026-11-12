package com.bandjou_mergegames.spineeditormobile.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Animation {
    public static class DrawOrderOffset {
        public String slot;
        public int offset;

        public DrawOrderOffset() {
        }

        public DrawOrderOffset(String slot, int offset) {
            this.slot = slot;
            this.offset = offset;
        }
    }

    public static class DrawOrderKey {
        public float time;
        public ArrayList<DrawOrderOffset> offsets = new ArrayList<>();

        public DrawOrderKey() {
        }

        public DrawOrderKey(float time) {
            this.time = time;
        }
    }

    public static class EventData {
        public String name;
        public String stringValue;
        public int intValue;
        public float floatValue;

        public EventData() {
        }

        public EventData(String name) {
            this.name = name;
        }
    }

    public static class EventKey {
        public float time;
        public ArrayList<EventData> events = new ArrayList<>();

        public EventKey() {
        }

        public EventKey(float time) {
            this.time = time;
        }
    }

    public static class SkinKey {
        public float time;
        public String skin;

        public SkinKey() {
        }

        public SkinKey(float time, String skin) {
            this.time = time;
            this.skin = skin;
        }
    }

    public String name;
    public float duration = 1f;
    public boolean loop = true;
    public final Map<String, ArrayList<BoneKey>> bones = new LinkedHashMap<>();
    public final ArrayList<DrawOrderKey> drawOrder = new ArrayList<>();
    public final ArrayList<EventKey> events = new ArrayList<>();
    public final ArrayList<SkinKey> skinKeys = new ArrayList<>();

    public Animation() {
    }

    public Animation(String name, float duration) {
        this.name = name;
        this.duration = duration;
    }

    public List<BoneKey> keys(String boneName) {
        return bones.get(boneName);
    }

    public ArrayList<BoneKey> keysForEdit(String boneName) {
        ArrayList<BoneKey> keys = bones.get(boneName);
        if (keys == null) {
            keys = new ArrayList<>();
            bones.put(boneName, keys);
        }
        return keys;
    }

    public BoneKey keyAt(String boneName, float time) {
        List<BoneKey> keys = bones.get(boneName);
        if (keys == null) {
            return null;
        }
        for (BoneKey key : keys) {
            if (Math.abs(key.time - time) < 0.001f) {
                return key;
            }
        }
        return null;
    }

    public void setKey(String boneName, BoneKey key) {
        ArrayList<BoneKey> keys = keysForEdit(boneName);
        for (int i = 0; i < keys.size(); i++) {
            if (Math.abs(keys.get(i).time - key.time) < 0.001f) {
                keys.set(i, key);
                return;
            }
        }
        keys.add(key);
        keys.sort((a, b) -> Float.compare(a.time, b.time));
    }

    public boolean removeKey(String boneName, float time) {
        List<BoneKey> keys = bones.get(boneName);
        if (keys == null) {
            return false;
        }
        for (int i = 0; i < keys.size(); i++) {
            if (Math.abs(keys.get(i).time - time) < 0.01f) {
                keys.remove(i);
                if (keys.isEmpty()) {
                    bones.remove(boneName);
                }
                return true;
            }
        }
        return false;
    }

    public DrawOrderKey drawOrderKeyAt(float time) {
        for (DrawOrderKey key : drawOrder) {
            if (key != null && Math.abs(key.time - time) < 0.001f) {
                return key;
            }
        }
        return null;
    }

    public void setDrawOrderKey(DrawOrderKey key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < drawOrder.size(); i++) {
            DrawOrderKey existing = drawOrder.get(i);
            if (existing != null && Math.abs(existing.time - key.time) < 0.001f) {
                drawOrder.set(i, key);
                drawOrder.sort((a, b) -> Float.compare(a.time, b.time));
                return;
            }
        }
        drawOrder.add(key);
        drawOrder.sort((a, b) -> Float.compare(a.time, b.time));
    }

    public EventKey eventKeyAt(float time) {
        for (EventKey key : events) {
            if (key != null && Math.abs(key.time - time) < 0.001f) {
                return key;
            }
        }
        return null;
    }

    public void setEventKey(EventKey key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            EventKey existing = events.get(i);
            if (existing != null && Math.abs(existing.time - key.time) < 0.001f) {
                events.set(i, key);
                events.sort((a, b) -> Float.compare(a.time, b.time));
                return;
            }
        }
        events.add(key);
        events.sort((a, b) -> Float.compare(a.time, b.time));
    }

    public SkinKey skinKeyAt(float time) {
        for (SkinKey key : skinKeys) {
            if (key != null && Math.abs(key.time - time) < 0.001f) {
                return key;
            }
        }
        return null;
    }

    public void setSkinKey(SkinKey key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < skinKeys.size(); i++) {
            SkinKey existing = skinKeys.get(i);
            if (existing != null && Math.abs(existing.time - key.time) < 0.001f) {
                skinKeys.set(i, key);
                skinKeys.sort((a, b) -> Float.compare(a.time, b.time));
                return;
            }
        }
        skinKeys.add(key);
        skinKeys.sort((a, b) -> Float.compare(a.time, b.time));
    }

    public boolean removeSkinKey(float time) {
        for (int i = 0; i < skinKeys.size(); i++) {
            SkinKey key = skinKeys.get(i);
            if (key != null && Math.abs(key.time - time) < 0.01f) {
                skinKeys.remove(i);
                return true;
            }
        }
        return false;
    }

    public String skinAt(float time) {
        if (skinKeys.isEmpty()) {
            return null;
        }
        SkinKey best = null;
        for (SkinKey key : skinKeys) {
            if (key == null) {
                continue;
            }
            if (key.time <= time + 0.0001f) {
                best = key;
            } else {
                break;
            }
        }
        if (best == null) {
            best = skinKeys.get(0);
        }
        return best == null ? null : best.skin;
    }

    public float lastKeyTime() {
        float max = 0f;
        for (List<BoneKey> keys : bones.values()) {
            if (keys == null) {
                continue;
            }
            for (BoneKey key : keys) {
                if (key.time > max) {
                    max = key.time;
                }
            }
        }
        for (DrawOrderKey key : drawOrder) {
            if (key != null && key.time > max) {
                max = key.time;
            }
        }
        for (EventKey key : events) {
            if (key != null && key.time > max) {
                max = key.time;
            }
        }
        for (SkinKey key : skinKeys) {
            if (key != null && key.time > max) {
                max = key.time;
            }
        }
        return max;
    }

    public int keyCount() {
        int count = 0;
        for (List<BoneKey> keys : bones.values()) {
            if (keys == null) {
                continue;
            }
            count += keys.size();
        }
        return count;
    }

    public static class BoneKey {
        public float time;
        public float x;
        public float y;
        public float rotation;
        public float scaleX = 1f;
        public float scaleY = 1f;

        public BoneKey() {
        }

        public BoneKey(float time, float x, float y, float rotation, float scaleX, float scaleY) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        public BoneKey copy() {
            return new BoneKey(time, x, y, rotation, scaleX, scaleY);
        }
    }
}
