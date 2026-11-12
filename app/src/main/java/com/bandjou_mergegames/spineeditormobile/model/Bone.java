package com.bandjou_mergegames.spineeditormobile.model;

public class Bone {
    public String name;
    public String parent;
    public float x;
    public float y;
    public float rotation;
    public float scaleX = 1f;
    public float scaleY = 1f;
    public float length = 50f;
    public int color = 0xFF5C7080;
    public boolean visible = true;

    public Bone() {
    }

    public Bone(String name, String parent) {
        this.name = name;
        this.parent = parent;
    }

    public Bone copy() {
        Bone bone = new Bone(name, parent);
        bone.x = x;
        bone.y = y;
        bone.rotation = rotation;
        bone.scaleX = scaleX;
        bone.scaleY = scaleY;
        bone.length = length;
        bone.color = color;
        bone.visible = visible;
        return bone;
    }
}
