package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.support.annotation.NonNull;

import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;
import org.thoughtcrime.securesms.geolocation.DcLocationManager;
import com.mapbox.mapboxsdk.Mapbox;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.crypto.PRNGFixes;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.persistence.JavaJobSerializer;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.ScreenLockUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.SignalProtocolLoggerProvider;
import org.thoughtcrime.securesms.util.AndroidSignalProtocolLogger;

public class ApplicationContext extends Application implements DefaultLifecycleObserver {

  public ApplicationDcContext   dcContext;
  public DcLocationManager      dcLocationManager;
  private JobManager            jobManager;
  private volatile boolean      isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();

    System.loadLibrary("native-utils");
    dcContext = new ApplicationDcContext(this);

    initializeRandomNumberFix();
    initializeLogging();
    initializeJobManager();
    initializeIncomingMessageNotifier();
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    Mapbox.getInstance(getApplicationContext(), BuildConfig.MAP_ACCESS_TOKEN);
    dcLocationManager = new DcLocationManager(this);
    try {
      DynamicLanguage.setContextLocale(this, DynamicLanguage.getSelectedLocale(this));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    ScreenLockUtil.setShouldLockApp(true);
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeIncomingMessageNotifier() {

    DcEventCenter dcEventCenter = dcContext.eventCenter;
    dcEventCenter.addObserver(DcContext.DC_EVENT_INCOMING_MSG, new DcEventCenter.DcEventDelegate() {
      @Override
      public void handleEvent(int eventId, Object data1, Object data2) {
        MessageNotifier.updateNotification(dcContext.context, ((Long) data1).intValue());
      }

      @Override
      public boolean runOnMain() {
        return false;
      }
    });

    // in five seconds, the system should be up and ready so we can start issuing notifications.

    Util.runOnBackgroundDelayed(() -> {
      MessageNotifier.updateNotification(dcContext.context);
      }, 5000);
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("TextSecureJobs")
                                .withJobSerializer(new JavaJobSerializer())
                                .withConsumerThreads(5)
                                .build();
  }
}
