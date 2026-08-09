# miniMall Studio (native Android)

The **authoring tool** for miniMall: build your shop on your phone and export a portable **`.shop`**
catalog your customers open in the [miniMall](https://github.com/eurobuddha/minima-core-android-merchshop)
app. Part of the miniMall family (miniMall · Inbox · Studio). Package `com.eurobuddha.merchstudio`.

**Two kinds of product under one roof** (v0.3.0 merged the former miniMerch NFT Studio in):

- **Products tab** — regular goods: you write the name and price, attach a photo from your
  gallery, and configure shipping options (UK / international / digital) with fees.
- **NFTs tab** — an art-first gallery of the NFTs in your node's wallet: plain 0-decimal tokens
  and **StateNFT collections** (Atelier / artBox — each piece rendered from its own on-chain
  stamped artwork, WebP/SVG/JPEG plates, beside the collection's token icon). Sell pieces
  individually or the **complete collection** as a single listing. No shipping — NFT delivery is
  on-chain and **automatic**: the buyer's wallet address rides in the order, and
  [miniMall Inbox](https://github.com/eurobuddha/minima-core-android-merchinbox) transfers the
  NFT (with full state replay for StateNFTs) once payment confirms.

Both kinds can live in one `.shop`. miniMall renders NFT-only shops as an art-grid collection
page and mixed/regular shops as a product list; checkout asks for a postal address only for the
physical items.

- **Vendor identity** is derived automatically from **your node's own seed** (X25519 + Ed25519
  keys under the `minimerch-*` HKDF domain) — the same identity across the whole family, so all
  orders land in the same miniMall Inbox.
- Exports **`.shop` schema v3** (additive; older miniMall builds ignore the new fields).

Note: this app holds the `INTERNET` permission — NFT images referenced by http(s)/ipfs URL in
token metadata are fetched by the app (SSRF-guarded, size-capped). All node communication remains
local broadcast IPC.

## Build

Requires a **JDK 17/21** (the Android Studio JBR works):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
```

Install, then enable **miniMall Studio** in Minima Core → Apps (needed to derive your vendor
identity and read your wallet). Freshly minted NFTs appear once their mint is confirmed.

Current: **v0.3.0** · package `com.eurobuddha.merchstudio`.
