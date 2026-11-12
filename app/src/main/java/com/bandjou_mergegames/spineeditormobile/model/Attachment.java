package com.bandjou_mergegames.spineeditormobile.model;

public class Attachment {
    public static final String TYPE_REGION = "region";

    public static final String SHAPE_RECT = "rect";
    public static final String SHAPE_ELLIPSE = "ellipse";
    public static final String SHAPE_TRIANGLE = "triangle";

    public String name;
    public String slot;
    public String type = TYPE_REGION;
    public String shape = SHAPE_RECT;
    public float x;
    public float y;
    public float rotation;
    public float width = 50f;
    public float height = 40f;
    public int color = 0xFF35D0BA;
    public boolean visible = true;
    public String path;
    public String region;

    public Attachment() {
    }

    public Attachment(String name, String slot) {
        this.name = name;
        this.slot = slot;
    }

    public Attachment copy() {
        Attachment attachment = new Attachment(name, slot);
        attachment.type = type;
        attachment.shape = shape;
        attachment.x = x;
        attachment.y = y;
        attachment.rotation = rotation;
        attachment.width = width;
        attachment.height = height;
        attachment.color = color;
        attachment.visible = visible;
        attachment.path = path;
        attachment.region = region;
        return attachment;
    }
}
