package com.bandjou_mergegames.spineeditormobile.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import com.bandjou_mergegames.spineeditormobile.model.Attachment;
import com.bandjou_mergegames.spineeditormobile.model.Project;
import com.bandjou_mergegames.spineeditormobile.model.Skin;
import com.bandjou_mergegames.spineeditormobile.model.Slot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SpineZipCodec {
    private SpineZipCodec() {
    }

    public static class Package {
        public Project project;
        public Map<String, byte[]> images = new LinkedHashMap<>();
        public String atlasName;
        public byte[] atlasPng;
        public byte[] atlasText;
    }

    public static Package fromZip(InputStream input) throws Exception {
        byte[] zipBytes = readAll(input);
        String jsonText = null;
        String atlasText = null;
        Map<String, byte[]> images = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String baseName = new File(entryName).getName();
                String lower = baseName.toLowerCase(Locale.US);
                byte[] bytes = readAll(zis);
                if (lower.endsWith(".json")) {
                    jsonText = new String(bytes, "UTF-8");
                } else if (lower.endsWith(".atlas")) {
                    atlasText = new String(bytes, "UTF-8");
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    images.put(baseName, bytes);
                }
                zis.closeEntry();
            }
        }
        if (jsonText == null) {
            throw new Exception("missing");
        }
        Project project = SpineJsonCodec.fromSpineJson(jsonText, "Spine");
        Package pkg = new Package();
        pkg.project = project;
        pkg.images = images;
        if (atlasText != null) {
            applyAtlasRegions(project, atlasText);
            pkg.atlasText = atlasText.getBytes("UTF-8");
        }
        if (!images.isEmpty()) {
            String atlasImage = atlasText == null ? null : atlasImageName(atlasText);
            if (atlasImage != null && images.containsKey(atlasImage)) {
                pkg.atlasPng = images.get(atlasImage);
                pkg.atlasName = atlasImage;
            } else {
                String firstPng = null;
                for (String name : images.keySet()) {
                    String lower = name.toLowerCase(Locale.US);
                    if (lower.endsWith(".png")) {
                        firstPng = name;
                        break;
                    }
                }
                if (firstPng != null) {
                    pkg.atlasPng = images.get(firstPng);
                    pkg.atlasName = firstPng;
                }
            }
        }
        return pkg;
    }

    public static byte[] toZip(Project project, String name) throws Exception {
        return toZip(project, name, null);
    }

    public static byte[] toZip(Project project, String name, Map<String, Bitmap> imageCache) throws Exception {
        Project copy = ProjectCodec.fromJson(ProjectCodec.toJson(project));
        copy.name = name == null || name.trim().isEmpty() ? "spine" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        copy.prune();
        copy.ensureRoot();
        if (imageCache != null) {
            for (Attachment attachment : copy.attachments) {
                if (attachment == null) {
                    continue;
                }
                String region = ensureRegionName(attachment);
                if (region != null && imageCache.get(region) != null) {
                    attachment.color = 0xFFFFFFFF;
                }
            }
        }
        String skeletonJson = SpineJsonCodec.toSpineJson(copy);
        AtlasResult atlas = buildAtlas(copy, imageCache);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("skeleton.json"));
            zos.write(skeletonJson.getBytes("UTF-8"));
            zos.closeEntry();
            if (atlas.text != null) {
                zos.putNextEntry(new ZipEntry("atlas.atlas"));
                zos.write(atlas.text.getBytes("UTF-8"));
                zos.closeEntry();
            }
            if (atlas.png != null) {
                zos.putNextEntry(new ZipEntry("atlas.png"));
                zos.write(atlas.png);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static class AtlasResult {
        byte[] png;
        String text;
    }

    private static AtlasResult buildAtlas(Project project) {
        return buildAtlas(project, null);
    }

    private static AtlasResult buildAtlas(Project project, Map<String, Bitmap> imageCache) {
        AtlasResult result = new AtlasResult();
        List<RegionItem> items = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        for (Skin skin : project.skins) {
            if (skin == null) {
                continue;
            }
            for (String attachmentName : skin.attachments.values()) {
                if (attachmentName != null && !attachmentName.isEmpty()) {
                    referenced.add(attachmentName);
                }
            }
        }
        for (Attachment attachment : project.attachments) {
            if (attachment == null || !referenced.contains(attachment.name)) {
                continue;
            }
            String region = ensureRegionName(attachment);
            if (region == null || region.isEmpty()) {
                continue;
            }
            if (used.add(region)) {
                items.add(new RegionItem(region, attachment.width, attachment.height, attachment.shape));
            }
        }
        if (items.isEmpty()) {
            return result;
        }
        int padding = 2;
        int size = 512;
        int area = totalArea(items, padding);
        while (size * size < area && size < 2048) {
            size *= 2;
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int x = padding;
        int y = padding;
        int rowH = 0;
        StringBuilder atlas = new StringBuilder();
        atlas.append("atlas.png\n");
        atlas.append("size: ").append(size).append(',').append(size).append('\n');
        atlas.append("format: RGBA8888\n");
        atlas.append("filter: Linear,Linear\n");
        atlas.append("repeat: none\n");
        for (RegionItem item : items) {
            int w = Math.max(1, Math.round(item.width));
            int h = Math.max(1, Math.round(item.height));
            if (x + w + padding > size) {
                x = padding;
                y += rowH + padding;
                rowH = 0;
            }
            if (y + h + padding > size) {
                break;
            }
            Bitmap src = imageCache == null ? null : imageCache.get(item.name);
            if (src == null || src.isRecycled()) {
                drawRegion(canvas, paint, item, x, y, w, h);
            } else {
                Rect dst = new Rect(x, y, x + w, y + h);
                Rect srect = new Rect(0, 0, src.getWidth(), src.getHeight());
                paint.setAlpha(255);
                canvas.drawBitmap(src, srect, dst, paint);
            }
            appendRegion(atlas, item.name, x, y, w, h);
            x += w + padding;
            rowH = Math.max(rowH, h);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        result.png = bos.toByteArray();
        result.text = atlas.toString();
        return result;
    }

    private static String ensureRegionName(Attachment attachment) {
        String region = attachment.region;
        if (region == null || region.trim().isEmpty()) {
            region = attachment.path;
        }
        if (region == null || region.trim().isEmpty()) {
            region = attachment.name;
        }
        if (region == null) {
            return null;
        }
        region = region.trim();
        attachment.region = region;
        attachment.path = region;
        return region;
    }

    private static void drawRegion(Canvas canvas, Paint paint, RegionItem item, int x, int y, int w, int h) {
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        RectF rect = new RectF(x, y, x + w, y + h);
        String shape = item.shape == null ? Attachment.SHAPE_RECT : item.shape;
        if (Attachment.SHAPE_ELLIPSE.equals(shape)) {
            canvas.drawOval(rect, paint);
        } else if (Attachment.SHAPE_TRIANGLE.equals(shape)) {
            Path path = new Path();
            path.moveTo(x + w / 2f, y);
            path.lineTo(x + w, y + h);
            path.lineTo(x, y + h);
            path.close();
            canvas.drawPath(path, paint);
        } else {
            canvas.drawRect(rect, paint);
        }
    }

    private static void appendRegion(StringBuilder atlas, String name, int x, int y, int w, int h) {
        atlas.append(name).append('\n');
        atlas.append("  rotate: false\n");
        atlas.append("  xy: ").append(x).append(',').append(y).append('\n');
        atlas.append("  size: ").append(w).append(',').append(h).append('\n');
        atlas.append("  orig: ").append(w).append(',').append(h).append('\n');
        atlas.append("  offset: 0,0\n");
        atlas.append("  index: -1\n");
    }

    private static int totalArea(List<RegionItem> items, int padding) {
        int area = 0;
        for (RegionItem item : items) {
            area += (Math.max(1, Math.round(item.width)) + padding)
                    * (Math.max(1, Math.round(item.height)) + padding);
        }
        return area;
    }

    private static String buildSimpleAtlas(Map<String, byte[]> images) {
        String imageName = null;
        for (String name : images.keySet()) {
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                imageName = name;
                break;
            }
        }
        if (imageName == null) {
            return null;
        }
        StringBuilder atlas = new StringBuilder();
        atlas.append(imageName).append('\n');
        atlas.append("size: 1024,1024\n");
        atlas.append("format: RGBA8888\n");
        atlas.append("filter: Linear,Linear\n");
        atlas.append("repeat: none\n");
        return atlas.toString();
    }

    private static void applyAtlasRegions(Project project, String atlasText) {
        Map<String, String> regionToPage = new LinkedHashMap<>();
        String page = null;
        String[] lines = atlasText.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (!line.startsWith(" ") && !line.startsWith("\t") && !line.contains(":")) {
                String trimmed = line.trim();
                if (!trimmed.equals("size:") && !trimmed.equals("format:") && !trimmed.equals("filter:")
                        && !trimmed.equals("repeat:") && !trimmed.equals("pma:")) {
                    page = trimmed;
                    continue;
                }
            }
            if (line.contains("xy:")) {
                String regionName = findLastRegion(lines);
                if (regionName != null && page != null) {
                    regionToPage.put(regionName, page);
                }
            }
        }
        for (Slot slot : project.slots) {
            Attachment attachment = project.attachmentForSlot(slot);
            if (attachment == null) {
                continue;
            }
            String region = attachment.region;
            if (region == null || region.trim().isEmpty()) {
                region = attachment.name;
            }
            attachment.region = region;
            attachment.path = region;
        }
    }

    private static String findLastRegion(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            if (line.contains("xy:")) {
                continue;
            }
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.contains(":")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("size")
                        && !trimmed.equals("format") && !trimmed.equals("filter")
                        && !trimmed.equals("repeat") && !trimmed.equals("pma")) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String atlasImageName(String atlasText) {
        String[] lines = atlasText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.contains(":")) {
                if (!trimmed.equals("size") && !trimmed.equals("format") && !trimmed.equals("filter")
                        && !trimmed.equals("repeat") && !trimmed.equals("pma")) {
                    return trimmed.endsWith(".png") ? trimmed : trimmed + ".png";
                }
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static class RegionItem {
        String name;
        float width;
        float height;
        String shape;

        RegionItem(String name, float width, float height, String shape) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.shape = shape;
        }
    }
}
