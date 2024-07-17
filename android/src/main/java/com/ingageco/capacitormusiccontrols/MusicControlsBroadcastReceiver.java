package com.ingageco.capacitormusiccontrols;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getcapacitor.JSObject;

public class MusicControlsBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "CMCBroadRcvr";
    private CapacitorMusicControls musicControls;

    public MusicControlsBroadcastReceiver(CapacitorMusicControls musicControls) {
        this.musicControls = musicControls;
    }

    public void stopListening() {
        JSObject ret = new JSObject();
        ret.put("message", "music-controls-stop-listening");
        this.musicControls.controlsNotification(ret);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getAction();
        JSObject ret = new JSObject();

        Log.i(TAG, "onReceive fired " + message);

        if (message.equals(Intent.ACTION_HEADSET_PLUG)) {
            // Handle headphone plug/unplug
            int state = intent.getIntExtra("state", -1);
            switch (state) {
                case 0:
                    ret.put("message", "music-controls-headset-unplugged");
                    this.musicControls.controlsNotification(ret);
                    this.musicControls.unregisterMediaButtonEvent();
                    break;
                case 1:
                    ret.put("message", "music-controls-headset-plugged");
                    this.musicControls.registerMediaButtonEvent();
                    break;
                default:
                    break;
            }
        } else if (message.equals("music-controls-media-button")) {
            // Handle media button
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                switch (keyCode) {
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        ret.put("message", "music-controls-next");
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        ret.put("message", "music-controls-pause");
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        ret.put("message", "music-controls-play");
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        ret.put("message", "music-controls-toggle-play-pause");
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        ret.put("message", "music-controls-previous");
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        ret.put("message", "music-controls-stop");
                        break;
                    default:
                        ret.put("message", message);
                        break;
                }
                this.musicControls.controlsNotification(ret);
            }
        } else if (message.equals("music-controls-destroy")) {
            // Handle close button
            ret.put("message", "music-controls-destroy");
            this.musicControls.controlsNotification(ret);
            this.musicControls.destroyPlayerNotification();
        } else {
            ret.put("message", message);
            this.musicControls.controlsNotification(ret);
        }
    }
}
