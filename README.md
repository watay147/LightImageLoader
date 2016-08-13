# LightImageLoader
A light, async-network-support image loader with memory and disk cache for android.

## Usage
- Copy the LightImageLoader.java under ```java/android/watay147/lightimageloader/utils``` and codes under libcore.io package (which contains the DiskLruCache.java) to your project. Then codes like the below are enough for loading an image for an ImageView by an uri.
```java
ImageView imageView=(ImageView)findViewById(R.id.image);
        LightImageLoader.getInstance().loadImage(imageView,tarUriStr);
```
- Or you can take the code in MainActivity.java as an example for help.