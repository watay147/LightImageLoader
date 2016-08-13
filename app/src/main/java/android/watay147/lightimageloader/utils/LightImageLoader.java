package android.watay147.lightimageloader.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by wwz on 2016/8/13.
 */
public class LightImageLoader {
    private static LightImageLoader mLightImageLoader;
    private Context mContext;
    private Handler mUiHandler;
    private ThreadPoolExecutor mThreadExecutor;
    private LinkedBlockingQueue<Runnable> mTaskQueue;
    private LruCache<String,Bitmap> mMemoryCache;


    private static int NUMBER_OF_CORES =
            Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    private static final int CACHE_SIZE=(int)(Runtime.getRuntime().maxMemory()
            /8/1024); //In KB unit


    final static int  TASK_COMPLETE=0;

    static class ImageLoadTask{
        ImageView imageView;
        Bitmap bitmap;
        String uri;
        volatile boolean canceled;//readed can use



        public ImageLoadTask(ImageView imageView,String uri){
            this.imageView=imageView;
            this.uri=uri;
            canceled=false;

        }


        public void setBitmap(Bitmap bitmap){
            this.bitmap=bitmap;
        }



        public synchronized void cancel(boolean canceled){
            this.canceled=canceled;
        }

        boolean isCancel(){
            return canceled;
        }
    }

    /**
     * A drawable bound to the ImageView which containing the reference to the ImageLoadTask.
     * In concurrent scenario, we need to make sure the target ImageView is
     * still need the bitmap loaded by the task.
     */
    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<ImageLoadTask> imageLoadTaskReference;

        public AsyncDrawable(ImageLoadTask bitmapWorkerTask){
            super();
            imageLoadTaskReference=new WeakReference<>(bitmapWorkerTask);

        }
        public AsyncDrawable(ImageLoadTask bitmapWorkerTask,Bitmap bitmap){
            super(bitmap);
            imageLoadTaskReference=new WeakReference<>(bitmapWorkerTask);

        }

        public ImageLoadTask getImageLoadTask() {
            return imageLoadTaskReference.get();
        }
    }

    static class DownLoadImageRunnable implements Runnable{

        private ImageLoadTask mImageLoadTask;
        public DownLoadImageRunnable(ImageLoadTask imageLoadTask){
            mImageLoadTask=imageLoadTask;

        }
        @Override
        public void run(){
            if(mImageLoadTask.isCancel()){
                return;
            }
            Bitmap bitmap = null;
            try {
                URL url = new URL(mImageLoadTask.uri);
                HttpURLConnection connection = (HttpURLConnection)url
                        .openConnection();
                connection.setRequestMethod("GET");
                InputStream inputStream = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if(bitmap!=null){
                LightImageLoader lightImageLoader=LightImageLoader
                        .getInstance();
                lightImageLoader.addBitmapToMemoryCache(mImageLoadTask.uri,bitmap);
                mImageLoadTask.setBitmap(bitmap);
                mLightImageLoader.deliverMessage(mImageLoadTask,
                        LightImageLoader.TASK_COMPLETE);

            }

        }
    }

    private LightImageLoader(){

        mUiHandler=new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message inputMessage) {
                ImageLoadTask task = (ImageLoadTask) inputMessage.obj;
                switch (inputMessage.what){
                    case TASK_COMPLETE:
                        if(!task.isCancel()){
                            task.imageView.setImageBitmap(task.bitmap);
                        }
                        break;
                }

            }
        };
        mTaskQueue=new LinkedBlockingQueue<>();
        mThreadExecutor=new ThreadPoolExecutor(NUMBER_OF_CORES,
                NUMBER_OF_CORES,KEEP_ALIVE_TIME,KEEP_ALIVE_TIME_UNIT,
                mTaskQueue);

        mMemoryCache =new LruCache<String, Bitmap>(CACHE_SIZE){
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };


    }

    public  void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        //The LruCache is thread safe therefore no need for synchronizing.
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    static public LightImageLoader getInstance(){
        if(mLightImageLoader==null){
            synchronized (LightImageLoader.class){
                if(mLightImageLoader==null){
                    mLightImageLoader=new LightImageLoader();
                }
            }
        }
        return  mLightImageLoader;
    }


    private void deliverMessage(ImageLoadTask imageLoadTask,int state){
        switch (state){
            case TASK_COMPLETE:
                Message message=mUiHandler.obtainMessage(state,imageLoadTask);
                message.sendToTarget();
                break;

        }

    }

    public void loadImage(ImageView imageView,String uri){
        final String imageKey=uri;
        final Bitmap bitmap = getBitmapFromMemCache(imageKey);
        if(bitmap!=null){
            imageView.setImageBitmap(bitmap);
        }
        else {
            if (cancelPotentialTask(imageView, uri)) {
                final ImageLoadTask imageLoadTask = new ImageLoadTask(imageView,

                        uri);
                AsyncDrawable asyncDrawable = new AsyncDrawable(imageLoadTask);
                imageView.setImageDrawable(asyncDrawable);
                final DownLoadImageRunnable downLoadImageRunnable = new DownLoadImageRunnable(imageLoadTask);
                mThreadExecutor.execute(downLoadImageRunnable);

            }
        }


    }

    public static boolean cancelPotentialTask(ImageView imageView,String uri){
        final ImageLoadTask imageLoadTask=getImageLoadTask(imageView);
        if(imageLoadTask!=null){
            final String oldUri=imageLoadTask.uri;
            if(oldUri!=null&&oldUri.equals(uri)){
                //The same with the uri newer, the new task shouldn't be
                // executed.
                return false;
            }
            else {
                //Different to the uri newer, should be canceled.
                imageLoadTask.cancel(true);
            }

        }
        return true;
    }

    public static ImageLoadTask getImageLoadTask(ImageView imageView){
        if(imageView!=null) {
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable=(AsyncDrawable) drawable;
                return asyncDrawable.getImageLoadTask();
            }
        }
        return null;
    }
}
