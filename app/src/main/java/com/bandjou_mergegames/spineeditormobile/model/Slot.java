package com.bandjou_mergegames.spineeditormobile.model;

public class Slot {
    public String name;
    public String bone;
    public String attachment;
    public int color = 0xFFFFFFFF;
    public boolean visible = true;

    public Slot() {
    }

    public Slot(String name, String bone) {
        this.name = name;
        this.bone = bone;
    }

    public Slot copy() {
        Slot slot = new Slot(name, bone);
        slot.attachment = attachment;
        slot.color = color;
        slot.visible = visible;
        return slot;
    }
}
