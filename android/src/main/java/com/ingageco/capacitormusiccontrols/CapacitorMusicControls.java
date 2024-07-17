package com.ingageco.capacitormusiccontrols;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import android.media.session.MediaSession.Token;

import android.util.Log;
import android.app.Activity;
import android.app.Notification;

import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.app.Service;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Build;
import android.R;
import android.content.BroadcastReceiver;
import android.media.AudioManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "CapacitorMusicControls")
public class CapacitorMusicControls extends Plugin {

	private static final String TAG = "CapacitorMusicControls";

	private MusicControlsBroadcastReceiver mMessageReceiver;
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private final int notificationID = 7824;
	private AudioManager mAudioManager;
	private PendingIntent mediaButtonPendingIntent;
	private boolean mediaButtonAccess = true;
	private android.media.session.MediaSession.Token token;
	private MusicControlsServiceConnection mConnection;

	private MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback(this);

	@PluginMethod()
	public void create(PluginCall call) {
		JSObject options = call.getData();
		Context context = getActivity().getApplicationContext();
		Activity activity = getActivity();

		initialize();

		try {
			MusicControlsInfos infos = new MusicControlsInfos(options);
			MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

			notification.updateNotification(infos);

			// Track title
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track);
			// Artists
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist);
			// Album
			metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);

			Bitmap art = getBitmapCover(infos.cover);
			if (art != null) {
				metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
				metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
			}

			mediaSessionCompat.setMetadata(metadataBuilder.build());

			if (infos.isPlaying)
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
			else
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

			call.resolve();
		} catch (JSONException e) {
			call.reject("error in initializing MusicControlsInfos " + e.toString());
		}
	}

	private void registerBroadcaster(MusicControlsBroadcastReceiver mMessageReceiver) {
		Context context = getActivity().getApplicationContext();
		IntentFilter filter = new IntentFilter();
		filter.addAction("music-controls-previous");
		filter.addAction("music-controls-pause");
		filter.addAction("music-controls-play");
		filter.addAction("music-controls-next");
		filter.addAction("music-controls-media-button");
		filter.addAction("music-controls-destroy");
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);

		context.registerReceiver(mMessageReceiver, filter);
	}

	// Register pendingIntent for broacast
	public void registerMediaButtonEvent() {
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(this.mediaButtonPendingIntent);
		}
	}

	public void unregisterMediaButtonEvent() {
		if (this.mediaSessionCompat != null) {
			this.mediaSessionCompat.setMediaButtonReceiver(null);
			this.mediaSessionCompat.release();
		}
	}

	public void destroyPlayerNotification() {
		if (this.notification != null) {
			try {
				this.notification.destroy();
				this.notification = null;
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}

	public void initialize() {
		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		// Avoid spawning multiple receivers
		if (this.mMessageReceiver != null) {
			try {
				context.unregisterReceiver(this.mMessageReceiver);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
			unregisterMediaButtonEvent();
		}

		this.mMessageReceiver = new MusicControlsBroadcastReceiver(this);
		this.registerBroadcaster(this.mMessageReceiver);

		this.mediaSessionCompat = new MediaSessionCompat(context, "capacitor-music-controls-media-session", null,
				this.mediaButtonPendingIntent);
		this.mediaSessionCompat.setFlags(
				MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

		MediaSessionCompat.Token _token = this.mediaSessionCompat.getSessionToken();
		this.token = (android.media.session.MediaSession.Token) _token.getToken();

		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

		this.mediaSessionCompat.setActive(true);
		this.mediaSessionCompat.setCallback(this.mMediaSessionCallback);

		this.notification = new MusicControlsNotification(activity, this.notificationID, this.token) {
			@Override
			protected void onNotificationUpdated(Notification notification) {
				mConnection.setNotification(notification, this.infos.isPlaying);
			}

			@Override
			protected void onNotificationDestroyed() {
				mConnection.setNotification(null, false);
			}
		};

		// Register media (headset) button event receiver
		try {
			this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			Intent headsetIntent = new Intent("music-controls-media-button");
			this.mediaButtonPendingIntent = PendingIntent.getBroadcast(
					context, 0, headsetIntent,
					Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
							? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
							: PendingIntent.FLAG_UPDATE_CURRENT);
			this.registerMediaButtonEvent();
		} catch (Exception e) {
			this.mediaButtonAccess = false;
			e.printStackTrace();
		}

		Intent startServiceIntent = new Intent(activity, MusicControlsNotificationKiller.class);
		startServiceIntent.putExtra("notificationID", this.notificationID);
		activity.bindService(startServiceIntent, this.mConnection, Context.BIND_AUTO_CREATE);
	}

	@PluginMethod()
	public void destroy(PluginCall call) {

		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		this.stopMessageReceiver(context);
		this.unregisterMediaButtonEvent();
		this.stopServiceConnection(activity);

		call.resolve();
	}

	protected void handleOnDestroy() {

		final Activity activity = getActivity();
		final Context context = activity.getApplicationContext();

		this.destroyPlayerNotification();
		this.stopMessageReceiver(context);
		this.unregisterMediaButtonEvent();
		this.stopServiceConnection(activity);

	}

	public void stopMessageReceiver(Context context) {
		if (this.mMessageReceiver != null) {
			this.mMessageReceiver.stopListening();
			try {
				context.unregisterReceiver(this.mMessageReceiver);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
	}

	public void stopServiceConnection(Activity activity) {
		if (this.mConnection != null) {
			Intent stopServiceIntent = new Intent(activity, MusicControlsNotificationKiller.class);
			activity.unbindService(this.mConnection);
			activity.stopService(stopServiceIntent);
			this.mConnection = null;
		}
	}

	@PluginMethod()
	public void updateIsPlaying(PluginCall call) {
		JSObject params = call.getData();

		if (this.notification == null) {
			call.resolve();
			return;
		}

		try {
			final boolean isPlaying = params.getBoolean("isPlaying");
			this.notification.updateIsPlaying(isPlaying);

			if (isPlaying)
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
			else
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

			call.resolve();
		} catch (JSONException e) {
			call.reject("error updateIsPlaying: " + e.toString());
		}

	}

	@PluginMethod()
	public void updateElapsed(PluginCall call) {
		JSObject params = call.getData();

		// final JSONObject params = args.getJSONObject(0);
		try {
			final boolean isPlaying = params.getBoolean("isPlaying");
			this.notification.updateIsPlaying(isPlaying);

			if (isPlaying)
				setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
			else
				setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

			call.resolve();
		} catch (JSONException e) {
			call.reject("error updateElapsed: " + e.toString());
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

	}

	@PluginMethod()
	public void updateDismissable(PluginCall call) {
		JSObject params = call.getData();
		// final JSONObject params = args.getJSONObject(0);
		try {
			final boolean dismissable = params.getBoolean("dismissable");
			this.notification.updateDismissable(dismissable);
			call.resolve();
		} catch (JSONException e) {
			call.reject("error updateDismissable: " + e.toString());
		}

	}

	public void controlsNotification(JSObject ret) {

		Log.i(TAG, "controlsNotification fired " + ret.getString("message"));
		// notifyListeners("controlsNotification", ret);
		this.bridge.triggerJSEvent("controlsNotification", "document", ret.toString());

	}

	private void setMediaPlaybackState(int state) {
		PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
		if (state == PlaybackStateCompat.STATE_PLAYING) {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE
					| PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
					PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
					PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
		} else {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY
					| PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
					PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
					PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0);
		}
		this.mediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
	}

	// Get image from url
	private Bitmap getBitmapCover(String coverURL) {
		try {
			if (coverURL.matches("^(https?|ftp)://.*$"))
				// Remote image
				return this.getBitmapFromURL(coverURL);
			else {
				// Local image
				return this.getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	// get Local image
	private Bitmap getBitmapFromLocal(String localURL) {
		try {
			Uri uri = Uri.parse(localURL);
			File file = new File(uri.getPath());
			FileInputStream fileStream = new FileInputStream(file);
			BufferedInputStream buf = new BufferedInputStream(fileStream);
			Bitmap myBitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			return myBitmap;
		} catch (Exception ex) {
			try {
				InputStream fileStream = getActivity().getAssets().open("public/" + localURL);
				BufferedInputStream buf = new BufferedInputStream(fileStream);
				Bitmap myBitmap = BitmapFactory.decodeStream(buf);
				buf.close();
				return myBitmap;
			} catch (Exception ex2) {
				ex.printStackTrace();
				ex2.printStackTrace();
				return null;
			}
		}
	}

	// get Remote image
	private Bitmap getBitmapFromURL(String strURL) {
		try {
			URL url = new URL(strURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
