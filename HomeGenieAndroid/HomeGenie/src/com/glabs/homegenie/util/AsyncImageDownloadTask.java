package com.glabs.homegenie.util;

// SOURCE: http://www.java2s.com/Code/Android/2D-Graphics/downloadingabitmapimagefromhttpandsettingittogivenimageviewasynchronously.htm

/*
 Copyright (c) 2010, Sungjin Han <meinside@gmail.com>
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
  * Neither the name of meinside nor the names of its contributors may be
    used to endorse or promote products derived from this software without
    specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.
 */


import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.glabs.homegenie.client.Control;
import com.glabs.homegenie.client.httprequest.HttpRequest;
import com.glabs.homegenie.client.httprequest.HttpRequest.HttpRequestException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * helper task for downloading a bitmap image from http and setting it to given image view asynchronously
 * <br>
 * <br>
 * <b>referenced</b>: http://android-developers.blogspot.com/2010/07/multithreading-for-performance.html
 *
 * @author meinside@gmail.com
 * @since 10.11.12.
 * <p/>
 * last update: 10.11.12.
 */
public class AsyncImageDownloadTask extends AsyncTask<String, Void, Bitmap> {
    private String url;
    protected final WeakReference<ImageView> imageViewReference;
    protected WeakReference<Bitmap> imageBitmap = new WeakReference<Bitmap>(null);
    private static HashMap<String, Bitmap> imageCache = new HashMap<String, Bitmap>();
    private boolean cacheEnabled = true;
    private ImageDownloadListener listener;
    private boolean animate = false;

    /**
     * @param imageView
     */
    public AsyncImageDownloadTask(ImageView imageView, boolean doanim, ImageDownloadListener listener) {
        animate = doanim;
        imageView.buildDrawingCache(false);
        Bitmap bmref = imageView.getDrawingCache(false);
        if (bmref != null) {
            imageBitmap = new WeakReference<Bitmap>(Bitmap.createBitmap(bmref));
        }
        imageViewReference = new WeakReference<ImageView>(imageView);
        this.listener = listener;
    }

    public boolean getCacheEnabled()
    {
        return cacheEnabled;
    }
    public void setCacheEnabled(boolean enabled)
    {
        cacheEnabled = enabled;
    }

    /**
     * @param url
     * @param imageView
     */
    public void download(String url, ImageView imageView) {
        if (cacheEnabled && imageCache.containsKey(url)) {
            Bitmap bitmap = imageCache.get(url);
            setImage(imageView, bitmap);
            if (listener != null)
                listener.imageDownloaded(url, bitmap);  //call back
        } else if (cancelPotentialDownload(url, imageView)) {
            AsyncImageDownloadTask task = new AsyncImageDownloadTask(imageView, animate, listener);
            task.setCacheEnabled(cacheEnabled);
            DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task);
            imageView.setImageDrawable(downloadedDrawable);
            task.execute(url);
        }
    }

    @Override
    protected Bitmap doInBackground(String... params) {
        this.url = params[0];
        return downloadBitmap(this.url);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            //bitmap = null;
            //return;
        }

        if (imageViewReference != null) {
            ImageView imageView = imageViewReference.get();
            AsyncImageDownloadTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);
            // Change bitmap only if this process is still associated with it
            if (this == bitmapDownloaderTask) {
                // get old drawable
                Drawable drawable = imageView.getDrawable();
                // set new drawable
                setImage(imageView, bitmap);
                //
                if (cacheEnabled)
                {
                    imageCache.put(url, bitmap);
                }
                else
                {
                    if (drawable instanceof BitmapDrawable) {
                        BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                        Bitmap oldBitmap = bitmapDrawable.getBitmap();
                        if (oldBitmap != null) oldBitmap.recycle();
                    }
                }
            }
        }
    }

    private void setImage(ImageView imageView, Bitmap bitmap) {
        if (animate) {
            Resources resources = imageView.getResources();
            BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
            Drawable currentDrawable = imageView.getDrawable();
            if (currentDrawable != null) {
                Drawable[] arrayDrawable = new Drawable[2];
                arrayDrawable[0] = currentDrawable;
                arrayDrawable[1] = drawable;
                final TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
                transitionDrawable.setCrossFadeEnabled(true);
                imageView.setImageDrawable(transitionDrawable);
                transitionDrawable.startTransition(150);
            } else {
                imageView.setImageDrawable(drawable);
            }
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }

    /**
     * @param url
     * @param imageView
     * @return false if the same url is already being downloaded
     */
    private boolean cancelPotentialDownload(String url, ImageView imageView) {
        AsyncImageDownloadTask bitmapDownloaderTask = getBitmapDownloaderTask(imageView);

        if (bitmapDownloaderTask != null) {
            String bitmapUrl = bitmapDownloaderTask.url;
            if (bitmapUrl == null || !bitmapUrl.equals(url)) {
                bitmapDownloaderTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * @param url
     * @return
     */
    private Bitmap downloadBitmap(String url) {
        try {
            HttpRequest request = Control.getHttpGetRequest(url);
            int statusCode = request.code();
            if (statusCode >= 200 && statusCode < 300) {
                ByteArrayInputStream inputStream = null;
                ByteArrayOutputStream byteOutPutStream = new ByteArrayOutputStream();
                try {
                    request.receive(byteOutPutStream);
                    byte[] b = byteOutPutStream.toByteArray();
                    inputStream = new ByteArrayInputStream(b);
                    final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (listener != null)
                        listener.imageDownloaded(url, bitmap);  //call back

                    return bitmap;
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch(HttpRequestException e) {
            e.printStackTrace();
        }

        if (listener != null)
            listener.imageDownloadFailed(url);  //call back

        return null;
    }

    /**
     * @param imageView
     * @return
     */
    private AsyncImageDownloadTask getBitmapDownloaderTask(ImageView imageView) {
        if (imageView != null) {
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof DownloadedDrawable) {
                DownloadedDrawable downloadedDrawable = (DownloadedDrawable) drawable;
                return downloadedDrawable.getBitmapDownloaderTask();
            }
        }
        return null;
    }

    /**
     * <b>referenced</b>: http://android-developers.blogspot.com/2010/07/multithreading-for-performance.html
     */
    public class DownloadedDrawable extends BitmapDrawable {
        private final WeakReference<AsyncImageDownloadTask> bitmapDownloaderTaskReference;

        public DownloadedDrawable(AsyncImageDownloadTask bitmapDownloaderTask) {
            super(bitmapDownloaderTask.imageBitmap.get());
            bitmapDownloaderTaskReference = new WeakReference<AsyncImageDownloadTask>(bitmapDownloaderTask);
        }

        public AsyncImageDownloadTask getBitmapDownloaderTask() {
            return bitmapDownloaderTaskReference.get();
        }
    }

    /**
     * for calling back download results
     *
     * @author meinside@gmail.com
     */
    public interface ImageDownloadListener {
        /**
         * called when the download failed
         *
         * @param imageUrl
         */
        public void imageDownloadFailed(String imageUrl);

        /**
         * called when the download finished successfully
         *
         * @param imageUrl
         * @param downloadedImage
         */
        public void imageDownloaded(String imageUrl, Bitmap downloadedImage);
    }
}
