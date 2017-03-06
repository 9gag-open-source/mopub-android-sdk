package com.mopub.nativeads;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.datasource.DataSource;
import com.facebook.datasource.DataSubscriber;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.AbstractDraweeControllerBuilder;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.mopub.common.Preconditions;
import com.mopub.common.logging.MoPubLog;

/**
 * Created by raymond on 13/2/2017.
 */
public class FrescoImageHelper {

    private static ResizeOptions sResizeOptions = new ResizeOptions(640, 480);


    public static void loadImageView(@Nullable final String url, @Nullable final SimpleDraweeView imageView) {
        if (!Preconditions.NoThrow.checkNotNull(imageView, "Cannot load image into null SimpleDraweeView")) {
            return;
        }

        if (!Preconditions.NoThrow.checkNotNull(url, "Cannot load image with null url")) {
            imageView.setImageDrawable(null);
            return;
        }

        ImageRequestBuilder imageRequestBuilder = ImageRequestBuilder.newBuilderWithSource(Uri.parse(url)).setResizeOptions(sResizeOptions);

        AbstractDraweeControllerBuilder draweeControllerBuilder = Fresco.newDraweeControllerBuilder().setOldController(imageView.getController()).setImageRequest(imageRequestBuilder.build());

        imageView.setController(draweeControllerBuilder.build());
    }

    /**
     * @param imageUrls
     * @param imageListener
     */
    public static void preCacheImages(@NonNull final List<String> imageUrls,
                                      @NonNull final NativeImageHelper.ImageListener imageListener) {

        final AtomicInteger imageCounter = new AtomicInteger(imageUrls.size());
        final AtomicBoolean anyFailures = new AtomicBoolean(false);

        DataSubscriber DS = new DataSubscriber() {
            @Override
            public void onNewResult(DataSource dataSource) {

            }

            @Override
            public void onFailure(DataSource dataSource) {
                MoPubLog.d("Failed to download a native ads image:", dataSource.getFailureCause());
                boolean anyPreviousErrors = anyFailures.getAndSet(true);
                imageCounter.decrementAndGet();
                if (!anyPreviousErrors) {
                    imageListener.onImagesFailedToCache(NativeErrorCode.IMAGE_DOWNLOAD_FAILURE);
                }
            }

            @Override
            public void onCancellation(DataSource dataSource) {

            }

            @Override
            public void onProgressUpdate(DataSource dataSource) {
                if (dataSource.getProgress() >= 1) {
                    final int count = imageCounter.decrementAndGet();
                    if (count == 0 && !anyFailures.get()) {
                        imageListener.onImagesCached();
                    }
                }
            }
        };

        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (TextUtils.isEmpty(url)) {
                anyFailures.set(true);
                imageListener.onImagesFailedToCache(NativeErrorCode.IMAGE_DOWNLOAD_FAILURE);
                return;
            }
            ImageRequestBuilder builder = ImageRequestBuilder.newBuilderWithSource(Uri.parse(url))
                .setResizeOptions(sResizeOptions)
                ;
            Fresco.getImagePipeline().prefetchToDiskCache(builder.build(), null)
                .subscribe(DS, UiThreadImmediateExecutorService.getInstance());
        }
    }
}
