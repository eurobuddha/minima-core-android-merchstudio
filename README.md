# miniMerch Studio (native Android)

The **authoring tool** for miniMerch: build your shop and vendor identity, then export a portable **`.shop`** catalog
your customers open in the [miniMerch Shop](https://github.com/eurobuddha/minima-core-android-merchshop) app. Part of
the miniMerch family (Shop · Inbox · Studio). Package `com.eurobuddha.merchstudio`.

## How it works

- **Vendor identity** is derived automatically from **your node's own seed** (X25519 + Ed25519 keys under the
  `minimerch-*` HKDF domain) — no paste, no Maxima. Your vendor card carries your `publicId` + receive address so
  customers can order and pay you.
- **Author a shop** — add products (name, price, token, image, shipping options); the studio assembles them into a
  **`.shop`** file.
- **Export / share** — the `.shop` is a portable catalog (schema shared with the desktop pocketShop studio and the
  web miniMerch family), so it's importable by any miniMerch storefront. Orders placed against it arrive in the
  [miniMerch Inbox](https://github.com/eurobuddha/minima-core-android-merchinbox).

No server: the shop is a file, the vendor identity is on-chain/seed-derived, and orders flow over the shared
**MINIMERCH** sentinel `0x4D494E494D45524348` with sealed-box crypto — interoperable with the whole miniMerch/miniMall
family.

## Build

Requires a **JDK 17/21** (the Android Studio JBR works):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease
```

Install, then enable **miniMerch Studio** in Minima Core → Apps (needed to derive your vendor identity from the seed).

Current: **v0.2.0** · package `com.eurobuddha.merchstudio`.
