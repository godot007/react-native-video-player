package com.reactlibrary.video.player;

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.GSYVideoPlayer;
import com.shuyu.gsyvideoplayer.listener.LockClickListener;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;

import java.util.Map;

/**
 * Created by lzw on 2017/6/8.
 */

public class RNVideoPlayerViewMannager extends SimpleViewManager<RCTVideoPlayer> implements LifecycleEventListener {

    private static final int COMMAND_PLAY = 0;
    private static final int COMMAND_PAUSE = 1;
    private static final int COMMAND_BACK_FROM_FULL = 2;

    private final ReactApplicationContext mReactApplicationContext;
    private RCTVideoPlayer player;

    EventDispatcher eventDispatcher;

    public RNVideoPlayerViewMannager(ReactApplicationContext reactApplicationContext) {
        super();
        mReactApplicationContext = reactApplicationContext;
    }

    private String src;
    private String newSrc;
    private String title;

    private OrientationUtils orientationUtils;

    @Override
    public String getName() {
        return "RCTVideoPlayer";
    }

    @Override
    protected void addEventEmitters(ThemedReactContext reactContext, RCTVideoPlayer view) {
        super.addEventEmitters(reactContext, view);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    protected RCTVideoPlayer createViewInstance(final ThemedReactContext reactContext) {
        eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
        player = new RCTVideoPlayer(mReactApplicationContext.getCurrentActivity());

        //外部辅助的旋转，帮助全屏
        orientationUtils = new OrientationUtils(mReactApplicationContext.getCurrentActivity(), player);
        //初始化不打开外部的旋转
        orientationUtils.setEnable(false);
        //是否可以滑动界面改变进度，声音等
        //player.setIsTouchWiget(true);
        //全屏是否可以滑动界面改变进度，声音等
        //player.setIsTouchWigetFull(false);
        //关闭自动旋转
        player.setRotateViewAuto(false);
        player.setLockLand(false);
        player.setShowFullAnimation(false);
        player.setNeedLockFull(false);
        player.setDismissControlTime(3000);
        //player.setOpenPreView(false);

        player.getBackButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eventDispatcher.dispatchEvent(new VideoPlayerEvent(player.getId(), "topBack"));
            }
        });

        player.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //直接横屏
                orientationUtils.resolveByClick();

                //第一个true是否需要隐藏actionbar，第二个true是否需要隐藏statusbar
                player.startWindowFullscreen(reactContext.getCurrentActivity(), true, true);
            }
        });

        player.setStandardVideoAllCallBack(new VideoListeneer(eventDispatcher, player) {
            @Override
            public void onPrepared(String url, Object... objects) {
                super.onPrepared(url, objects);
                //开始播放了才能旋转和全屏
//                orientationUtils.setEnable(true);
            }

            @Override
            public void onQuitFullscreen(String url, Object... objects) {
                super.onQuitFullscreen(url, objects);
                if (orientationUtils != null) {
                    orientationUtils.backToProtVideo();
                }
            }
        });

        player.setLockClickListener(new LockClickListener() {
            @Override
            public void onClick(View view, boolean lock) {
                if (orientationUtils != null) {
                    //配合下方的onConfigurationChanged
                    orientationUtils.setEnable(!lock);
                }
            }
        });
        return player;
    }

    @Override
    public void onHostResume() {
        GSYVideoManager.onResume();
    }

    @Override
    public void onHostPause() {
        GSYVideoManager.onPause();
    }

    @Override
    public void onHostDestroy() {
        GSYVideoPlayer.releaseAllVideos();
        if (orientationUtils != null)
            orientationUtils.releaseListener();
        this.src = null;
    }

    @Override
    public void onDropViewInstance(RCTVideoPlayer view) {
        super.onDropViewInstance(view);
        GSYVideoPlayer.releaseAllVideos();
        if (orientationUtils != null)
            orientationUtils.releaseListener();
        this.src = null;
    }

    // props
    @ReactProp(name = "src")
    public void setSrc(final RCTVideoPlayer player, @Nullable String src) {
        Log.i("RCTVideoPlayer", "set src " + src);
        this.newSrc = src;
    }

    @ReactProp(name = "title")
    public void setTitle(RCTVideoPlayer player, String title) {
        Log.i("RCTVideoPlayer", "set title "+ title);
        this.title = title;
    }

    @ReactProp(name = "placeholderImage")
    public void setPlaceholderImage(RCTVideoPlayer player, String placeholderImage) {
        Log.i("RCTVideoPlayer", "set placeholderImage " + placeholderImage);
        if(placeholderImage != null && !placeholderImage.equals("")) {
            Uri uri = Uri.parse(placeholderImage);
            SimpleDraweeView draweeView = new SimpleDraweeView(mReactApplicationContext.getCurrentActivity());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER;
            draweeView.setLayoutParams(params);
            draweeView.setImageURI(uri);
            draweeView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            player.setThumbImageView(draweeView);
        }
    }


    @Override
    protected void onAfterUpdateTransaction(RCTVideoPlayer view) {
        super.onAfterUpdateTransaction(view);

        if(this.newSrc != null && !this.newSrc.equals(this.src)) {
            player.setUp(this.newSrc, false, this.title == null ? "" : this.title);

            // 如果切换了播放的文件路径，触发开始播放
            if(this.src != null){
                Log.i("RCTVideoPlayer", "diff src play");
                player.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        player.startPlayLogic();
                    }
                }, 750);
            }
            this.src = this.newSrc;
            this.newSrc = null;
        }
        player.getTitleTextView().setVisibility(View.GONE);
        player.getBackButton().setVisibility(View.VISIBLE);
    }

    @Override
    public @Nullable
    Map getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.of(
                "topBack", MapBuilder.of("registrationName", "onBack"),
                "topPrepared", MapBuilder.of("registrationName", "onPrepared"),
                "topPlay", MapBuilder.of("registrationName", "onPlay"),
                "topPause", MapBuilder.of("registrationName", "onPause"),
                "topEnd", MapBuilder.of("registrationName", "onEnd"),
                "topError", MapBuilder.of("registrationName", "onError"),
                "topFullscreen", MapBuilder.of("registrationName", "onFullscreen")
                );
    }

    @javax.annotation.Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of(
                "play", COMMAND_PLAY,
                "pause", COMMAND_PAUSE,
                "backFromFull", COMMAND_BACK_FROM_FULL);
    }

    @Override
    public void receiveCommand(RCTVideoPlayer root, int commandId, @javax.annotation.Nullable ReadableArray args) {
        super.receiveCommand(root, commandId, args);
        switch (commandId){
            case COMMAND_PLAY: {
                player.startPlayLogic();
                player.release();
                player.setUp(args.getString(0), args.getBoolean(1), null, args.getString(2));
                player.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        player.startPlayLogic();
                    }
                }, 1000);
                break;
            }
            case COMMAND_BACK_FROM_FULL: {
                if (orientationUtils != null) {
                    orientationUtils.backToProtVideo();
                }
                player.backFromWindowFull(mReactApplicationContext.getCurrentActivity());
                break;
            }
            case COMMAND_PAUSE: {
                GSYVideoManager.onPause();
                break;
            }
        }
    }
}
