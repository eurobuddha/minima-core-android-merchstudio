package com.eurobuddha.merchstudio;

import org.json.JSONObject;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A literal schema-v3 snapshot. Background work never owns editable Activity models. */
final class ShopExport {
    final String name, slug;
    private final String json;
    private final List<Image> images;

    static final class Image {
        final String array, field, url, identity;
        final int index, decodeSize, fallbackSize, budget;
        Image(String array, int index, String field, String url, String identity,
                int decodeSize, int fallbackSize, int budget) {
            this.array=array;this.index=index;this.field=field;this.url=url;this.identity=identity;
            this.decodeSize=decodeSize;this.fallbackSize=fallbackSize;this.budget=budget;
        }
    }
    interface Resolver { String resolve(Image image) throws Exception; }

    ShopExport(JSONObject shop, String slug, List<Image> images) throws Exception {
        this.name=shop.getString("shopName");
        if (slug == null || !slug.matches("[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*"))
            throw new IllegalArgumentException("Invalid shop filename");
        this.slug=slug;
        this.json=shop.toString();
        this.images=Collections.unmodifiableList(new ArrayList<>(images));
    }

    JSONObject render(Resolver resolver) throws Exception {
        JSONObject result=new JSONObject(json);
        for (Image image:images) {
            String encoded=resolver.resolve(image);
            if (encoded != null && !encoded.isEmpty())
                result.getJSONArray(image.array).getJSONObject(image.index).put(image.field,encoded);
        }
        return result;
    }

    /** A count of 0 or less: the listing carries no usable edition/stock size (not an overflow). */
    static final class NonPositiveCountException extends IllegalArgumentException {
        NonPositiveCountException(String message) { super(message); }
    }

    /** miniMall's v3 reader and quantity controls use positive signed 32-bit counts. */
    static int count(int n) {
        if (n<=0) throw new NonPositiveCountException("This listing has no edition size — refresh the gallery.");
        return n;
    }
    /** Same contract for arbitrary-precision amounts; overflow stays an ArithmeticException. */
    static int count(BigInteger value) {
        return count(new java.math.BigDecimal(value).intValueExact());
    }

    /** Export dirs are never reused (each share keeps its own); drop the ones older than a day. */
    static void pruneStale(File cache) {
        File[] dirs=new File(cache,"shops").listFiles((d,name)->name.startsWith("export-"));
        long cutoff=System.currentTimeMillis()-24L*60*60*1000;
        if (dirs!=null) for (File dir:dirs) {
            if (dir.lastModified()>=cutoff) continue;
            File[] files=dir.listFiles();
            if (files!=null) for (File f:files) f.delete();
            dir.delete();
        }
    }

    /** Reuse NodeFiles.replaceFrom's temporary-write, sync and atomic-rename ordering.
     * Each export has its own directory, so an already shared URI never changes contents. */
    File write(File cache, Resolver resolver) throws Exception {
        byte[] bytes=render(resolver).toString().getBytes(StandardCharsets.UTF_8);
        File root=new File(cache,"shops");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create export folder");
        File dir=Files.createTempDirectory(root.toPath(),"export-").toFile();
        File part=new File(dir,"export.part"), destination=new File(dir,slug+".shop");
        boolean complete=false;
        try {
            try (FileOutputStream output=new FileOutputStream(part)) {
                output.write(bytes);
                output.getFD().sync();
            }
            Files.move(part.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
            complete=true;
            return destination;
        } finally {
            if (!complete) { part.delete(); destination.delete(); dir.delete(); }
        }
    }
}
