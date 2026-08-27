package net.sourceforge.opencamera;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import net.sourceforge.opencamera.cameracontroller.CameraController;

import java.util.Locale;

/** Minimal, responsive "Simplest Camera" UI overlay for elders. Drives Open Camera's
 *  existing pipeline (takePicture / switch camera / switch video / gallery) from a
 *  stripped-down set of controls that adapt to any screen ratio. iPhone-style: controls
 *  float over the preview; the recording border hugs the real preview rectangle. */
public class SimpleCameraUI {

    enum Screen { CAMERA, TUCK, RECORDING }

    private final MainActivity main;
    private Screen screen = Screen.CAMERA;

    private View root, preview, cameraControls, recordingControls;
    private TextView tabPhoto, tabVideo, shutter, timerText, galleryLabel, flipBtn;
    private ImageView galleryThumb;
    private View gallery, stopBtn, flash, tuck, recCapsule, recBorder;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerTick;
    private int secs;
    private ObjectAnimator recBorderAnim;
    private boolean starting, stopping;

    public SimpleCameraUI(MainActivity main) {
        this.main = main;
        setup();
    }

    private int dp(float v) {
        return Math.round(v * main.getResources().getDisplayMetrics().density);
    }

    private void setup() {
        root = main.findViewById(R.id.sc_root);
        preview = main.findViewById(R.id.preview);
        cameraControls = main.findViewById(R.id.sc_camera_controls);
        recordingControls = main.findViewById(R.id.sc_recording_controls);
        tabPhoto = main.findViewById(R.id.sc_tab_photo);
        tabVideo = main.findViewById(R.id.sc_tab_video);
        shutter = main.findViewById(R.id.sc_shutter);
        timerText = main.findViewById(R.id.sc_timer);
        gallery = main.findViewById(R.id.sc_gallery);
        galleryThumb = main.findViewById(R.id.sc_gallery_thumb);
        galleryLabel = main.findViewById(R.id.sc_gallery_label);
        flipBtn = main.findViewById(R.id.sc_flip);
        stopBtn = main.findViewById(R.id.sc_stop);
        flash = main.findViewById(R.id.sc_flash);
        tuck = main.findViewById(R.id.sc_tuck);
        recCapsule = main.findViewById(R.id.sc_rec_capsule);
        recBorder = main.findViewById(R.id.sc_rec_border);

        // physical-feeling shutter: circular shadow + press translate
        ViewOutlineProvider oval = new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        };
        shutter.setOutlineProvider(oval);
        shutter.setElevation(dp(8));
        shutter.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().translationY(dp(6)).setDuration(40).start();
                v.setElevation(dp(2));
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().translationY(0).setDuration(80).start();
                v.setElevation(dp(8));
            }
            return false;
        });

        // rounded thumbnail
        galleryThumb.setClipToOutline(true);
        galleryThumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(13));
            }
        });

        hideNativeControls();

        shutter.setOnClickListener(v -> onShutter());
        stopBtn.setOnClickListener(v -> onStop());
        gallery.setOnClickListener(v -> main.clickedGallery(v));
        flipBtn.setOnClickListener(v -> { main.clickedSwitchCamera(v); handler.postDelayed(this::refreshFlipLabel, 400); });
        tabPhoto.setOnClickListener(v -> selectMode(false));
        tabVideo.setOnClickListener(v -> selectMode(true));

        // keep the recording border + timer capsule glued to the real preview rectangle,
        // so they hug the actual image on any screen ratio (U11, A56, ...).
        if (preview != null) {
            preview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncToPreview());
            preview.post(this::syncToPreview);
        }

        refreshMode();
        refreshThumbnail();
    }

    /** Open Camera leaves its buttons VISIBLE by XML default and re-shows some (e.g. the
     *  exposure button in cameraSetup), so hide the always-on native controls up front. */
    private void hideNativeControls() {
        int[] ids = {
            R.id.exposure, R.id.settings, R.id.popup,
            R.id.switch_camera, R.id.switch_video, R.id.switch_multi_camera,
            R.id.take_photo, R.id.gallery, R.id.take_photo_when_video_recording,
            R.id.pause_video,
        };
        for (int id : ids) {
            View v = main.findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        }
    }

    /** Position the recording border and timer capsule to match the live preview rect. */
    private void syncToPreview() {
        if (root == null || preview == null || recBorder == null) return;
        int pw = preview.getWidth(), ph = preview.getHeight();
        if (pw <= 0 || ph <= 0) return;
        int[] rootLoc = new int[2];
        int[] pvLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        preview.getLocationOnScreen(pvLoc);
        int left = pvLoc[0] - rootLoc[0];
        int top = pvLoc[1] - rootLoc[1];

        FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) recBorder.getLayoutParams();
        blp.width = pw;
        blp.height = ph;
        blp.leftMargin = left;
        blp.topMargin = top;
        blp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        recBorder.setLayoutParams(blp);

        FrameLayout.LayoutParams clp = (FrameLayout.LayoutParams) recCapsule.getLayoutParams();
        clp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        clp.topMargin = top + dp(16);
        recCapsule.setLayoutParams(clp);
    }

    private boolean isVideoMode() {
        return main.getPreview() != null && main.getPreview().isVideo();
    }

    /** Sync tab + shutter appearance to the current photo/video mode. */
    public void refreshMode() {
        boolean video = isVideoMode();
        if (video) {
            tabVideo.setBackgroundResource(R.drawable.sc_tab_selected);
            tabVideo.setTextColor(0xFF14150F);
            tabPhoto.setBackground(null);
            tabPhoto.setTextColor(0x99FFFFFF);
            shutter.setBackgroundResource(R.drawable.sc_shutter_video);
            shutter.setText("錄影");
            shutter.setTextColor(0xFFFFFFFF);
        } else {
            tabPhoto.setBackgroundResource(R.drawable.sc_tab_selected);
            tabPhoto.setTextColor(0xFF14150F);
            tabVideo.setBackground(null);
            tabVideo.setTextColor(0x99FFFFFF);
            shutter.setBackgroundResource(R.drawable.sc_shutter_photo);
            shutter.setText("拍照");
            shutter.setTextColor(0xFF14150F);
        }
        refreshFlipLabel();
    }

    /** Show the camera the button will switch TO ("前" when on back, "後" when on front). */
    public void refreshFlipLabel() {
        if (flipBtn == null) return;
        try {
            CameraController cc = main.getPreview() != null ? main.getPreview().getCameraController() : null;
            if (cc != null)
                flipBtn.setText(cc.getFacing() == CameraController.Facing.FACING_FRONT ? "後" : "前");
        } catch (Exception ignored) {}
    }

    private void selectMode(boolean wantVideo) {
        if (screen == Screen.RECORDING || starting) return;
        if (isVideoMode() != wantVideo) {
            main.clickedSwitchVideo(shutter);
            refreshMode();
            handler.postDelayed(this::refreshMode, 300);
        }
    }

    private void onShutter() {
        if (screen != Screen.CAMERA) return;
        if (isVideoMode()) {
            if (starting) return;
            starting = true;
            main.takePicture(false); // start recording; onRecordingStarted() confirms
            handler.postDelayed(() -> starting = false, 3000); // safety reset if start fails
        } else {
            playFlashThenTuck();
            main.takePicture(false);
        }
    }

    private void onStop() {
        if (screen != Screen.RECORDING || stopping) return;
        stopping = true;
        main.takePicture(false); // stop; onRecordingStopped() confirms
        handler.postDelayed(() -> stopping = false, 3000);
    }

    // ---- callbacks driven by MyApplicationInterface (always on UI thread) ----

    public void onRecordingStarted() {
        starting = false;
        screen = Screen.RECORDING;
        cameraControls.setVisibility(View.GONE);
        recordingControls.setVisibility(View.VISIBLE);
        recCapsule.setVisibility(View.VISIBLE);
        startRecBorder();
        secs = 0;
        updateTimer();
        timerTick = new Runnable() {
            @Override public void run() {
                secs++;
                updateTimer();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(timerTick, 1000);
    }

    public void onRecordingStopped() {
        stopping = false;
        if (timerTick != null) {
            handler.removeCallbacks(timerTick);
            timerTick = null;
        }
        recCapsule.setVisibility(View.GONE);
        recordingControls.setVisibility(View.GONE);
        cameraControls.setVisibility(View.VISIBLE);
        stopRecBorder();
        screen = Screen.CAMERA;
        playTuck();
        handler.postDelayed(this::refreshThumbnail, 800);
    }

    private void updateTimer() {
        timerText.setText(String.format(Locale.US, "%d:%02d", secs / 60, secs % 60));
    }

    /** Gentle red border pulse on the viewfinder while recording (comfortable, ~1.8s cycle). */
    private void startRecBorder() {
        if (recBorder == null) return;
        syncToPreview();
        recBorder.setVisibility(View.VISIBLE);
        recBorder.setAlpha(0.15f);
        recBorderAnim = ObjectAnimator.ofFloat(recBorder, "alpha", 0.15f, 1f);
        recBorderAnim.setDuration(900);
        recBorderAnim.setRepeatMode(ValueAnimator.REVERSE);
        recBorderAnim.setRepeatCount(ValueAnimator.INFINITE);
        recBorderAnim.start();
    }

    private void stopRecBorder() {
        if (recBorderAnim != null) {
            recBorderAnim.cancel();
            recBorderAnim = null;
        }
        if (recBorder != null) {
            recBorder.setAlpha(0f);
            recBorder.setVisibility(View.GONE);
        }
    }

    // ---- animations ----

    private void playFlashThenTuck() {
        screen = Screen.TUCK;
        flash.setAlpha(1f);
        flash.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            flash.animate().alpha(0f).setDuration(80).withEndAction(() -> {
                flash.setVisibility(View.GONE);
                flash.setAlpha(0f);
            }).start();
            playTuck();
        }, 170);
        handler.postDelayed(() -> { if (screen == Screen.TUCK) screen = Screen.CAMERA; }, 170 + 600);
        handler.postDelayed(this::refreshThumbnail, 900);
    }

    /** Shrink a preview-sized square into the gallery button. Positions computed dynamically. */
    private void playTuck() {
        if (root == null || preview == null || gallery == null) return;
        int[] rootLoc = new int[2];
        int[] pvLoc = new int[2];
        int[] galLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        preview.getLocationOnScreen(pvLoc);
        gallery.getLocationOnScreen(galLoc);

        int L = pvLoc[0] - rootLoc[0];
        int T = pvLoc[1] - rootLoc[1];
        int W = preview.getWidth();
        int H = preview.getHeight();
        if (W <= 0 || H <= 0) return;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tuck.getLayoutParams();
        lp.width = W;
        lp.height = H;
        lp.leftMargin = L;
        lp.topMargin = T;
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        tuck.setLayoutParams(lp);

        float gx = (galLoc[0] - rootLoc[0]) + gallery.getWidth() / 2f;
        float gy = (galLoc[1] - rootLoc[1]) + gallery.getHeight() / 2f;

        final float s = 0.13f;
        tuck.setPivotX(0f);
        tuck.setPivotY(H);
        tuck.setScaleX(1f);
        tuck.setScaleY(1f);
        tuck.setTranslationX(0f);
        tuck.setTranslationY(0f);
        tuck.setAlpha(0.96f);
        tuck.setVisibility(View.VISIBLE);

        float dx = gx - L - (W * s) / 2f;
        float dy = gy - (T + H) + (H * s) / 2f;

        tuck.animate()
            .scaleX(s).scaleY(s)
            .translationX(dx).translationY(dy)
            .alpha(0f)
            .setDuration(580)
            .setInterpolator(new PathInterpolator(0.5f, 0f, 0.75f, 0.9f))
            .withEndAction(() -> {
                tuck.setVisibility(View.GONE);
                tuck.setScaleX(1f);
                tuck.setScaleY(1f);
                tuck.setTranslationX(0f);
                tuck.setTranslationY(0f);
                tuck.setAlpha(0.96f);
            })
            .start();
    }

    // ---- gallery thumbnail (last shot) ----

    public void refreshThumbnail() {
        new Thread(() -> {
            final Bitmap bmp = loadLatestThumb();
            main.runOnUiThread(() -> {
                if (bmp != null) {
                    galleryThumb.setImageBitmap(bmp);
                    galleryThumb.setVisibility(View.VISIBLE);
                    galleryLabel.setVisibility(View.GONE);
                } else {
                    galleryThumb.setVisibility(View.GONE);
                    galleryLabel.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private Bitmap loadLatestThumb() {
        Cursor c = null;
        try {
            Uri files = MediaStore.Files.getContentUri("external");
            String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?,?)";
            String[] args = {
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            };
            String[] proj = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED
            };
            c = main.getContentResolver().query(files, proj, sel, args,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                int type = c.getInt(1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri item = ContentUris.withAppendedId(files, id);
                    return main.getContentResolver().loadThumbnail(item, new Size(dp(120), dp(120)), null);
                } else if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    return MediaStore.Video.Thumbnails.getThumbnail(
                        main.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                } else {
                    return MediaStore.Images.Thumbnails.getThumbnail(
                        main.getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public void onDestroy() {
        if (timerTick != null) {
            handler.removeCallbacks(timerTick);
            timerTick = null;
        }
        stopRecBorder();
        handler.removeCallbacksAndMessages(null);
    }
}
