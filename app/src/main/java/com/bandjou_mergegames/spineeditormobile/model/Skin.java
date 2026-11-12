package com.bandjou_mergegames.spineeditormobile.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Skin {
    public String name;
    public final Map<String, String> attachments = new LinkedHashMap<>();

    public Skin() {
    }

    public Skin(String name) {
        this.name = name;
    }

    public Skin copy() {
        Skin skin = new Skin(name);
        skin.attachments.putAll(attachments);
        return skin;
    }
}