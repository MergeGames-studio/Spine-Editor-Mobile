package com.bandjou_mergegames.spineeditormobile.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Pose {
    public static class Local {
        public float x;
        public float y;
        public float rotation;
        public float scaleX = 1f;
        public float scaleY = 1f;
    }

    public static class World {
        public float x;
        public float y;
        public float rotation;
        public float scaleX = 1f;
        public float scaleY = 1f;
    }

    public final Map<String, Local> locals = new HashMap<>();
    public final Map<String, World> worlds = new HashMap<>();

    public Local local(String boneName) {
        return locals.get(boneName);
    }

    public World world(String boneName) {
        return worlds.get(boneName);
    }

    public static Pose build(Project project, Animation animation, float time) {
        Pose pose = new Pose();
        Map<String, Bone> bones = new HashMap<>();
        for (Bone bone : project.bones) {
            bones.put(bone.name, bone);
            Local local = new Local();
            local.x = bone.x;
            local.y = bone.y;
            local.rotation = bone.rotation;
            local.scaleX = bone.scaleX;
            local.scaleY = bone.scaleY;
            if (animation != null) {
                List<Animation.BoneKey> keys = animation.keys(bone.name);
                if (keys != null && !keys.isEmpty()) {
                    sampleInto(local, keys, time);
                }
            }
            pose.locals.put(bone.name, local);
        }
        Set<String> visiting = new HashSet<>();
        for (Bone bone : project.bones) {
            pose.computeWorld(bone, bones, visiting);
        }
        return pose;
    }

    private static void sampleInto(Local local, List<Animation.BoneKey> keys, float time) {
        Animation.BoneKey first = keys.get(0);
        if (time <= first.time || keys.size() == 1) {
            apply(local, first);
            return;
        }
        Animation.BoneKey last = keys.get(keys.size() - 1);
        if (time >= last.time) {
            apply(local, last);
            return;
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            Animation.BoneKey a = keys.get(i);
            Animation.BoneKey b = keys.get(i + 1);
            if (time >= a.time && time <= b.time) {
                float span = b.time - a.time;
                float t = span <= 0.00001f ? 0f : (time - a.time) / span;
                local.x = a.x + (b.x - a.x) * t;
                local.y = a.y + (b.y - a.y) * t;
                local.rotation = a.rotation + (b.rotation - a.rotation) * t;
                local.scaleX = a.scaleX + (b.scaleX - a.scaleX) * t;
                local.scaleY = a.scaleY + (b.scaleY - a.scaleY) * t;
                return;
            }
        }
        apply(local, last);
    }

    private static void apply(Local local, Animation.BoneKey key) {
        local.x = key.x;
        local.y = key.y;
        local.rotation = key.rotation;
        local.scaleX = key.scaleX;
        local.scaleY = key.scaleY;
    }

    private World computeWorld(Bone bone, Map<String, Bone> bones, Set<String> visiting) {
        World existing = worlds.get(bone.name);
        if (existing != null) {
            return existing;
        }
        Local local = locals.get(bone.name);
        if (local == null) {
            local = new Local();
            locals.put(bone.name, local);
        }
        World world = new World();
        World parentWorld = null;
        if (bone.parent != null && bones.containsKey(bone.parent) && visiting.add(bone.name)) {
            parentWorld = computeWorld(bones.get(bone.parent), bones, visiting);
            visiting.remove(bone.name);
        }
        if (parentWorld == null) {
            world.x = local.x;
            world.y = local.y;
            world.rotation = local.rotation;
            world.scaleX = local.scaleX;
            world.scaleY = local.scaleY;
        } else {
            double radians = Math.toRadians(parentWorld.rotation);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            world.x = parentWorld.x + local.x * parentWorld.scaleX * cos - local.y * parentWorld.scaleY * sin;
            world.y = parentWorld.y + local.x * parentWorld.scaleX * sin + local.y * parentWorld.scaleY * cos;
            world.rotation = parentWorld.rotation + local.rotation;
            world.scaleX = parentWorld.scaleX * local.scaleX;
            world.scaleY = parentWorld.scaleY * local.scaleY;
        }
        worlds.put(bone.name, world);
        return world;
    }
}
