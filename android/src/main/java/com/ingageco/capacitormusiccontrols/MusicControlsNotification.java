package com.ingageco.capacitormusiccontrols;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.app.Activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MusicControlsNotification {
	private static final String TAG = "CMCNotification";

	private Activity cordovaActivity;
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notificationBuilder;
	private int notificationID;
	protected MusicControlsInfos infos;
	private Bitmap bitmapCover;
	private String CHANNEL_ID;
	private MediaSessionCompat mediaSession;
	private MediaSessionManager mediaSessionManager;

	// Public Constructor
	public MusicControlsNotification(Activity cordovaActivity, int id, MediaSession.Token token) {
		this.CHANNEL_ID = "capacitor-music-channel-id";
		this.notificationID = id;
		this.cordovaActivity = cordovaActivity;
		Context context = cordovaActivity.getApplicationContext();
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
		mediaSession = new MediaSessionCompat(context, "MusicControls");
		mediaSession.setActive(true);

		// Use channelid for Oreo and higher
		if (Build.VERSION.SDK_INT >= 26) {
			// The user-visible name of the channel.
			CharSequence name = "Audio Controls";
			// The user-visible description of the channel.
			String description = "Control Playing Audio";

			int importance = NotificationManager.IMPORTANCE_LOW;

			NotificationChannel mChannel = new NotificationChannel(this.CHANNEL_ID, name, importance);

			// Configure the notification channel.
			mChannel.setDescription(description);

			this.notificationManager.createNotificationChannel(mChannel);
		}
	}

	// Show or update notification
	public void updateNotification(MusicControlsInfos newInfos) {
		Log.i(TAG, "updateNotification: infos: " + newInfos.toString());
		// Check if the cover has changed
		if (!newInfos.cover.isEmpty() && (this.infos == null || !newInfos.cover.equals(this.infos.cover))) {
			this.getBitmapCover(newInfos.cover);
		}
		this.infos = newInfos;
		this.createBuilder();
		this.createNotification();
	}

	private void createNotification() {
		final Notification noti = this.notificationBuilder.build();
		this.notificationManager.notify(this.notificationID, noti);
		this.onNotificationUpdated(noti);
	}

	// Toggle the play/pause button
	public void updateIsPlaying(boolean isPlaying) {
		Log.i(TAG, "updateIsPlaying: isPlaying: " + isPlaying);
		Log.i(TAG, "updateIsPlaying: pre:this.infos.isPlaying: " + this.infos.isPlaying);
		this.infos.isPlaying = isPlaying;
		Log.i(TAG, "updateIsPlaying: post:this.infos.isPlaying: " + this.infos.isPlaying);
		this.createBuilder();
		this.createNotification();
	}

	// Toggle the dismissable status
	public void updateDismissable(boolean dismissable) {
		this.infos.dismissable = dismissable;
		this.createBuilder();
		this.createNotification();
	}

	// Get image from url
	private void getBitmapCover(String coverURL) {
		try {
			if (coverURL.matches("^(https?|ftp)://.*$"))
				// Remote image
				this.bitmapCover = getBitmapFromURL(coverURL);
			else {
				// Local image
				this.bitmapCover = getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
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
				InputStream fileStream = cordovaActivity.getAssets().open("public/" + localURL);
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

	private void createBuilder() {
		Context context = cordovaActivity.getApplicationContext();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, this.CHANNEL_ID);
	
		// Configure builder
		if (this.infos.track != null) {
			builder.setContentTitle(this.infos.track);
		}
		if (this.infos.artist != null) {
			builder.setContentText(this.infos.artist);
		}
		builder.setWhen(0);
	
		// Set if the notification can be destroyed by swiping
		if (this.infos.dismissable) {
			builder.setOngoing(false);
			Intent dismissIntent = new Intent("music-controls-destroy");
			PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context, 1, dismissIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.setDeleteIntent(dismissPendingIntent);
		} else {
			builder.setOngoing(true);
		}
	
		if (this.infos.ticker != null && !this.infos.ticker.isEmpty()) {
			builder.setTicker(this.infos.ticker);
		}
	
		builder.setPriority(NotificationCompat.PRIORITY_MAX);
		builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
	
		// Set SmallIcon
		boolean usePlayingIcon = this.infos.notificationIcon == null || this.infos.notificationIcon.isEmpty();
		if (!usePlayingIcon) {
			int resId = this.getResourceId(this.infos.notificationIcon, 0);
			usePlayingIcon = resId == 0;
			if (!usePlayingIcon) {
				builder.setSmallIcon(resId);
			}
		}
	
		if (usePlayingIcon) {
			if (this.infos.isPlaying) {
				builder.setSmallIcon(this.getResourceId(this.infos.playIcon, android.R.drawable.ic_media_play));
			} else {
				builder.setSmallIcon(this.getResourceId(this.infos.pauseIcon, android.R.drawable.ic_media_pause));
			}
		}
	
		// Set LargeIcon
		if (this.infos.cover != null && !this.infos.cover.isEmpty() && this.bitmapCover != null) {
			builder.setLargeIcon(this.bitmapCover);
		}
	
		// Open app if tapped
		Intent resultIntent = new Intent(context, cordovaActivity.getClass());
		resultIntent.setAction(Intent.ACTION_MAIN);
		resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE);
		builder.setContentIntent(resultPendingIntent);
	
		// Controls
		int nbControls = 0;
		/* Previous */
		if (this.infos.hasPrev) {
			nbControls++;
			Intent previousIntent = new Intent("music-controls-previous");
			PendingIntent previousPendingIntent = PendingIntent.getBroadcast(context, 1, previousIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.addAction(this.getResourceId(this.infos.prevIcon, android.R.drawable.ic_media_previous), "", previousPendingIntent);
		}
		if (this.infos.isPlaying) {
			/* Pause */
			nbControls++;
			Intent pauseIntent = new Intent("music-controls-pause");
			PendingIntent pausePendingIntent = PendingIntent.getBroadcast(context, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.addAction(this.getResourceId(this.infos.pauseIcon, android.R.drawable.ic_media_pause), "", pausePendingIntent);
		} else {
			/* Play */
			nbControls++;
			Intent playIntent = new Intent("music-controls-play");
			PendingIntent playPendingIntent = PendingIntent.getBroadcast(context, 1, playIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.addAction(this.getResourceId(this.infos.playIcon, android.R.drawable.ic_media_play), "", playPendingIntent);
		}
		/* Next */
		if (this.infos.hasNext) {
			nbControls++;
			Intent nextIntent = new Intent("music-controls-next");
			PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 1, nextIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.addAction(this.getResourceId(this.infos.nextIcon, android.R.drawable.ic_media_next), "", nextPendingIntent);
		}
		/* Close */
		if (this.infos.hasClose) {
			nbControls++;
			Intent destroyIntent = new Intent("music-controls-destroy");
			PendingIntent destroyPendingIntent = PendingIntent.getBroadcast(context, 1, destroyIntent, PendingIntent.FLAG_IMMUTABLE);
			builder.addAction(this.getResourceId(this.infos.closeIcon, android.R.drawable.ic_menu_close_clear_cancel), "", destroyPendingIntent);
		}
	
		int[] args = new int[nbControls];
		for (int i = 0; i < nbControls; ++i) {
			args[i] = i;
		}
		builder.setStyle(new MediaStyle().setShowActionsInCompactView(args).setMediaSession(mediaSession.getSessionToken()));
	
		this.notificationBuilder = builder;
	}	

	private int getResourceId(String name, int fallback) {
		try {
			if (name.isEmpty()) {
				return fallback;
			}

			int resId = this.cordovaActivity.getResources().getIdentifier(name, "drawable",
					this.cordovaActivity.getPackageName());
			return resId == 0 ? fallback : resId;
		} catch (Exception ex) {
			return fallback;
		}
	}

	public void destroy() {
		Log.i(TAG, "Destroying notification");
		this.notificationManager.cancel(this.notificationID);
		this.onNotificationDestroyed();
		Log.i(TAG, "Notification destroyed");
	}

	protected void onNotificationUpdated(Notification notification) {
	}

	protected void onNotificationDestroyed() {
	}
}
