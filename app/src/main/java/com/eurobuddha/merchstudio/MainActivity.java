package com.eurobuddha.merchstudio;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.goterl.lazysodium.LazySodium;

import org.json.JSONArray;
import org.json.JSONObject;
import com.eurobuddha.comms.CommsIdentity;
import com.eurobuddha.comms.CommsTransport;
import com.eurobuddha.comms.Hex;
import com.eurobuddha.comms.Images;
import com.eurobuddha.comms.NodeApi;
import com.eurobuddha.comms.Sodium;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** miniMall Studio — build a shop on your phone and export a portable .shop bundle. */
public class MainActivity extends AppCompatActivity {

    private LazySodium ls;
    private NodeApi node;
    private boolean paired = false;
    private String vendorPublicId = "", vendorAddress = "";

    private String shopName = "";
    private String currency = "Minima", tokenid = CommsTransport.MINIMA;
    private final List<Catalog.Shipping> shipping = new ArrayList<>();
    private final List<Catalog.Product> products = new ArrayList<>();
    private Catalog.Product pickingFor;   // product whose photo is being chosen

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> imagePicker;

    private LinearLayout root, form;
    private TextView cardStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ls = Sodium.get();
        seedDefaults();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.BG);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(0, bars.top, 0, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        new WindowInsetsControllerCompat(getWindow(), root).setAppearanceLightStatusBars(false);

        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && pickingFor != null) attachPhoto(pickingFor, uri);
        });

        buildScreen();
        node = new NodeApi(this, this::onPaired);
    }

    @Override protected void onDestroy() { super.onDestroy(); if (node != null) node.onDestroy(); io.shutdownNow(); }

    private void seedDefaults() {
        if (!shipping.isEmpty()) return;
        for (String[] s : new String[][]{{"uk", "UK shipping"}, {"intl", "International"}, {"digital", "Digital / collect"}}) {
            Catalog.Shipping x = new Catalog.Shipping(); x.id = s[0]; x.label = s[1]; x.fee = "0"; shipping.add(x);
        }
        if (products.isEmpty()) products.add(new Catalog.Product());
    }

    // ---- identity (vendor card) ----
    private void onPaired(boolean enabled) {
        paired = enabled;
        if (enabled && vendorPublicId.isEmpty()) {
            node.cmd("vault action:seed", new NodeApi.Cb() {
                @Override public void onResult(JSONObject j) {
                    JSONObject r = j.optJSONObject("response");
                    String ikm = r == null ? "" : r.optString("seed", r.optString("phrase", ""));
                    if (!ikm.isEmpty()) deriveCard(ikm);
                }
                @Override public void onError(String m) {}
            });
            node.cmd("getaddress", new NodeApi.Cb() {
                @Override public void onResult(JSONObject j) {
                    JSONObject r = j.optJSONObject("response");
                    if (r != null) { vendorAddress = r.optString("miniaddress", r.optString("address", "")); refreshCardStatus(); }
                }
                @Override public void onError(String m) {}
            });
        }
        refreshCardStatus();
    }

    private void deriveCard(final String ikm) {
        io.execute(() -> {
            try {
                byte[] seed = ikm.startsWith("0x") ? Hex.from(ikm) : ikm.getBytes(StandardCharsets.UTF_8);
                String pid = CommsIdentity.fromSeed(ls, seed).publicId();
                ui.post(() -> { vendorPublicId = pid; refreshCardStatus(); });
            } catch (Exception e) { ui.post(() -> toast("Identity error: " + e.getMessage())); }
        });
    }

    private boolean cardReady() {
        return CommsIdentity.isValidPublicId(vendorPublicId) && vendorAddress != null && !vendorAddress.isEmpty();
    }

    private void refreshCardStatus() {
        if (cardStatus == null) return;
        if (cardReady()) { cardStatus.setText("✓ Shop key ready (from this node's seed)"); cardStatus.setTextColor(Design.IN); }
        else if (!paired) { cardStatus.setText("Enable miniMall Studio in Minima Core → Apps."); cardStatus.setTextColor(Design.ACCENT); }
        else { cardStatus.setText("Connecting to your node…"); cardStatus.setTextColor(Design.DIM); }
    }

    // ---- the editor form ----
    private void buildScreen() {
        root.removeAllViews();
        LinearLayout head = header("miniMall Studio");
        root.addView(head);
        ScrollView sv = new ScrollView(this);
        form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(16), dp(8), dp(16), dp(28));
        sv.addView(form);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        rebuildForm();
    }

    private void rebuildForm() {
        form.removeAllViews();

        cardStatus = new TextView(this); cardStatus.setTextSize(12f); cardStatus.setPadding(0, 0, 0, dp(10));
        form.addView(cardStatus); refreshCardStatus();

        form.addView(sectionLabel("Shop name"));
        form.addView(field(shopName, "e.g. Bean & Brew", s -> shopName = s));

        form.addView(sectionLabel("Currency"));
        LinearLayout cur = new LinearLayout(this); cur.setOrientation(LinearLayout.HORIZONTAL);
        cur.addView(seg("Minima", currency.equals("Minima"), () -> { currency = "Minima"; tokenid = CommsTransport.MINIMA; rebuildForm(); }));
        cur.addView(seg("mxUSDT", currency.equals("USDT"), () -> { currency = "USDT"; tokenid = CommsTransport.USDT; rebuildForm(); }));
        form.addView(cur);

        form.addView(sectionLabel("Shipping (label + fee)"));
        for (final Catalog.Shipping s : shipping) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            EditText label = field(s.label, "Label", v -> s.label = v); label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            EditText fee = field(s.fee, "0", v -> s.fee = v); fee.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT); fl.leftMargin = dp(8); fee.setLayoutParams(fl);
            row.addView(label); row.addView(fee); form.addView(row);
        }

        form.addView(sectionLabel("Products  (" + products.size() + "/40)"));
        for (int i = 0; i < products.size(); i++) form.addView(productCard(products.get(i)));

        TextView addP = button("＋ Add product", false);
        addP.setOnClickListener(v -> { if (products.size() < 40) { products.add(new Catalog.Product()); rebuildForm(); } });
        form.addView(spacer(8)); form.addView(addP);

        TextView export = button("Export shop (.shop)", true);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        el.topMargin = dp(22); export.setLayoutParams(el); export.setPadding(dp(16), dp(14), dp(16), dp(14));
        export.setOnClickListener(v -> exportShop());
        form.addView(export);
    }

    private View productCard(final Catalog.Product p) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.roundBg(this, Design.SURFACE, 12)); card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8); card.setLayoutParams(clp);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        EditText name = field(p.name, "Product name", v -> p.name = v); name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rm = new TextView(this); rm.setText("✕"); rm.setTextColor(Design.RED); rm.setTextSize(18f); rm.setPadding(dp(12), 0, dp(4), 0);
        rm.setOnClickListener(v -> { products.remove(p); rebuildForm(); });
        top.addView(name); top.addView(rm); card.addView(top);

        EditText desc = field(p.description, "Description (optional)", v -> p.description = v); card.addView(desc);

        LinearLayout pr = new LinearLayout(this); pr.setOrientation(LinearLayout.HORIZONTAL); pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.addView(tag("Price")); EditText price = field(p.price, "0", v -> p.price = v);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setLayoutParams(smLp()); pr.addView(price);
        pr.addView(tag("  Max")); EditText max = field(String.valueOf(p.maxUnits), "10", v -> { try { p.maxUnits = Integer.parseInt(v); } catch (Exception e) { p.maxUnits = 10; } });
        max.setInputType(InputType.TYPE_CLASS_NUMBER); max.setLayoutParams(smLp()); pr.addView(max);
        card.addView(pr);

        LinearLayout ph = new LinearLayout(this); ph.setOrientation(LinearLayout.HORIZONTAL); ph.setGravity(Gravity.CENTER_VERTICAL); ph.setPadding(0, dp(8), 0, 0);
        ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(Design.roundBg(this, Design.SURFACE2, 8)); thumb.setClipToOutline(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(54), dp(54)); tl.rightMargin = dp(10); thumb.setLayoutParams(tl);
        if (!p.image.isEmpty()) { Bitmap bmp = decode(p.image); if (bmp != null) thumb.setImageBitmap(bmp); }
        TextView pick = button(p.image.isEmpty() ? "+ Photo" : "Change photo", false);
        pick.setOnClickListener(v -> { pickingFor = p; imagePicker.launch("image/*"); });
        ph.addView(thumb); ph.addView(pick); card.addView(ph);
        return card;
    }

    private void attachPhoto(final Catalog.Product p, final Uri uri) {
        io.execute(() -> {
            byte[] jpeg = Images.compressToFit(MainActivity.this, uri, 90000);   // ~90KB, plenty for a bundle
            if (jpeg == null) { ui.post(() -> toast("Couldn't read that image.")); return; }
            p.image = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
            ui.post(this::rebuildForm);
        });
    }

    // ---- export ----
    private void exportShop() {
        if (shopName.trim().isEmpty()) { toast("Give your shop a name."); return; }
        if (!cardReady()) { toast("Connect your node so the shop key can be set."); return; }
        List<Catalog.Product> valid = new ArrayList<>();
        for (Catalog.Product p : products) if (p.name != null && !p.name.trim().isEmpty()) valid.add(p);
        if (valid.isEmpty()) { toast("Add at least one product."); return; }
        io.execute(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("shopName", shopName.trim()); o.put("shopId", slug(shopName));
                o.put("vendorPublicId", vendorPublicId); o.put("vendorAddress", vendorAddress);
                o.put("currency", currency); o.put("tokenid", tokenid);
                JSONArray sh = new JSONArray();
                for (Catalog.Shipping s : shipping) { JSONObject so = new JSONObject(); so.put("id", s.id); so.put("label", s.label); so.put("fee", s.fee == null || s.fee.isEmpty() ? "0" : s.fee); sh.put(so); }
                o.put("shipping", sh);
                JSONArray ps = new JSONArray();
                int i = 0;
                for (Catalog.Product p : valid) {
                    JSONObject po = new JSONObject();
                    po.put("id", "p" + (i++)); po.put("name", p.name.trim()); po.put("description", p.description == null ? "" : p.description.trim());
                    po.put("mode", "units"); po.put("price", p.price == null || p.price.isEmpty() ? "0" : p.price);
                    po.put("maxUnits", p.maxUnits <= 0 ? 10 : p.maxUnits); po.put("image", p.image == null ? "" : p.image);
                    ps.put(po);
                }
                o.put("products", ps);

                File dir = new File(getCacheDir(), "shops"); dir.mkdirs();
                File f = new File(dir, slug(shopName) + ".shop");
                try (FileWriter w = new FileWriter(f)) { w.write(o.toString()); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                ui.post(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/octet-stream");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_SUBJECT, shopName.trim() + " — miniMall shop");
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share your shop"));
                });
            } catch (Exception e) { ui.post(() -> toast("Export failed: " + e.getMessage())); }
        });
    }

    // ---- helpers ----
    private static String slug(String s) {
        String r = (s == null ? "shop" : s).replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        return r.isEmpty() ? "shop" : r;
    }
    private Bitmap decode(String b64) {
        try { byte[] b = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP); return BitmapFactory.decodeByteArray(b, 0, b.length); }
        catch (Exception e) { return null; }
    }
    private int dp(int v) { return Design.dp(this, v); }
    private LinearLayout header(String title) {
        LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(Design.SURFACE); h.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView t = new TextView(this); t.setText(title); t.setTextColor(Design.TEXT); t.setTextSize(18f); t.setTypeface(null, Typeface.BOLD);
        h.addView(t); return h;
    }
    private TextView sectionLabel(String s) {
        TextView t = new TextView(this); t.setText(s.toUpperCase()); t.setTextColor(Design.DIM2); t.setTextSize(11f);
        t.setTypeface(null, Typeface.BOLD); t.setPadding(0, dp(16), 0, dp(6)); return t;
    }
    private TextView tag(String s) { TextView t = new TextView(this); t.setText(s); t.setTextColor(Design.DIM); t.setTextSize(13f); return t; }
    private EditText field(String value, String hint, java.util.function.Consumer<String> onChange) {
        EditText e = new EditText(this); e.setText(value == null ? "" : value); e.setHint(hint); e.setHintTextColor(Design.DIM2);
        e.setTextColor(Design.TEXT); e.setTextSize(14f); e.setBackground(Design.roundBg(this, Design.SURFACE2, 10));
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(4); e.setLayoutParams(lp);
        e.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { onChange.accept(s.toString()); }
        });
        return e;
    }
    private LinearLayout.LayoutParams smLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT); lp.leftMargin = dp(6); lp.rightMargin = dp(6); return lp; }
    private TextView seg(String text, boolean on, Runnable onClick) {
        TextView b = new TextView(this); b.setText(text); b.setGravity(Gravity.CENTER); b.setTextSize(14f);
        b.setTextColor(on ? Design.ON_ACCENT : Design.DIM); b.setBackground(Design.roundBg(this, on ? Design.ACCENT : Design.SURFACE2, 10));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.rightMargin = dp(8); b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run()); return b;
    }
    private TextView button(String text, boolean active) {
        TextView b = new TextView(this); b.setText(text); b.setTextSize(14f); b.setGravity(Gravity.CENTER);
        b.setTextColor(active ? Design.ON_ACCENT : Design.TEXT); b.setBackground(Design.roundBg(this, active ? Design.ACCENT : Design.SURFACE2, 10));
        b.setPadding(dp(16), dp(11), dp(16), dp(11)); return b;
    }
    private View spacer(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
