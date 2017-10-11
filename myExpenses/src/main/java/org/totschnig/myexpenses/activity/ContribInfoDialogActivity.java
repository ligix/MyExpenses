package org.totschnig.myexpenses.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ContribDialogFragment;
import org.totschnig.myexpenses.dialog.DonateDialogFragment;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.util.AcraHelper;
import org.totschnig.myexpenses.util.ShortcutHelper;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.licence.Package;
import org.totschnig.myexpenses.util.tracking.Tracker;

import java.io.Serializable;
import java.util.Locale;


/**
 * Manages the dialog shown to user when they request usage of a premium functionality or click on
 * the dedicated entry on the preferences screen. If called from an activity extending
 * {@link ProtectedFragmentActivity}, {@link ContribIFace#contribFeatureCalled(ContribFeature, Serializable)}
 * or {@link ContribIFace#contribFeatureNotCalled(ContribFeature)} will be triggered on it, depending on
 * if user canceled or has usages left. If called from shortcut, this activity will launch the intent
 * for the premium feature directly
 */
public class ContribInfoDialogActivity extends ProtectedFragmentActivity
    implements MessageDialogListener {
  public final static String KEY_FEATURE = "feature";
  private final static String KEY_PACKAGE = "package";
  public static final String KEY_TAG = "tag";
  private static final String KEY_SHOULD_REPLACE_EXISTING = "shouldReplaceExisting";


  public static Intent getIntentFor(Context context, @Nullable ContribFeature feature) {
    Intent intent = new Intent(context, ContribInfoDialogActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    if (feature != null) {
      intent.putExtra(KEY_FEATURE, feature.name());
    }
    return intent;
  }

  public static Intent getIntentFor(Context context, @NonNull Package aPackage, boolean shouldReplaceExisting) {
    Intent intent = new Intent(context, ContribInfoDialogActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    intent.putExtra(KEY_PACKAGE, aPackage.name());
    intent.putExtra(KEY_SHOULD_REPLACE_EXISTING, shouldReplaceExisting);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeIdTranslucent());
    super.onCreate(savedInstanceState);
    String packageFromExtra = getIntent().getStringExtra(KEY_PACKAGE);

    if (savedInstanceState == null) {
      if (packageFromExtra == null) {
        ContribDialogFragment.newInstance(getIntent().getStringExtra(KEY_FEATURE),
            getIntent().getSerializableExtra(KEY_TAG))
            .show(getSupportFragmentManager(), "CONTRIB");
      } else {
        contribBuyDo(Package.valueOf(packageFromExtra));
      }
    }
  }

  public void contribBuyDo(Package aPackage) {
    Bundle bundle = new Bundle(1);
    bundle.putString(Tracker.EVENT_PARAM_PACKAGE, aPackage.name());
    logEvent(Tracker.EVENT_CONTRIB_DIALOG_BUY, bundle);
    Integer[] paymentOptions = aPackage.getPaymentOptions();
    if (paymentOptions.length > 1) {
      DonateDialogFragment.newInstance(aPackage).show(getSupportFragmentManager(), "CONTRIB");
    } else {
      startPayment(paymentOptions[0], aPackage);
    }
  }

  public void startPayment(int paymentOption, Package aPackage) {
    Intent intent;
    switch (paymentOption) {
      case R.string.donate_button_paypal: {
        String host = BuildConfig.DEBUG ? "www.sandbox.paypal.com" : "www.paypal.com" ;
        String paypalButtonId = BuildConfig.DEBUG? "TURRUESSCUG8N" : "LBUDF8DSWJAZ8";
        String uri = String.format(Locale.US,
            "https://%s/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=%s&on0=%s&os0=%s&lc=%s",
            host, paypalButtonId, "Licence", aPackage.name(), getPaypalLocale());
        String licenceEmail = PrefKey.LICENCE_EMAIL.getString(null);
        if (licenceEmail != null) {
          uri += "&custom=" + Uri.encode(licenceEmail);
        }

        intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(uri));
        startActivityForResult(intent, 0);
        break;
      }
      case R.string.donate_button_invoice: {
        intent = new Intent(Intent.ACTION_SEND);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{MyApplication.INVOICES_EMAIL});
        String packageLabel = aPackage.getButtonLabel(this);
        intent.putExtra(Intent.EXTRA_SUBJECT,
            "[" + getString(R.string.app_name) + "] " + getString(R.string.donate_button_invoice));
        String userCountry = Utils.getCountryFromTelephonyManager();
        String messageBody = String.format(
            "Please send an invoice for %s to:\nName: (optional)\nCountry: %s (required)",
            packageLabel, userCountry != null ? userCountry : "");
        intent.putExtra(Intent.EXTRA_TEXT, messageBody);
        if (!Utils.isIntentAvailable(this, intent)) {
          Toast.makeText(this, R.string.no_app_handling_email_available, Toast.LENGTH_LONG).show();
        } else {
          startActivityForResult(intent, 0);
        }
      }
    }
  }

  private String getPaypalLocale() {
    return Locale.getDefault().toString();
  }

  @Override
  public void onMessageDialogDismissOrCancel() {
    finish(true);
  }

  public void finish(boolean canceled) {
    String featureStringFromExtra = getIntent().getStringExtra(KEY_FEATURE);
    if (featureStringFromExtra != null) {
      ContribFeature feature = ContribFeature.valueOf(featureStringFromExtra);
      int usagesLeft = feature.usagesLeft();
      boolean shouldCallFeature = feature.hasAccess() || (!canceled && usagesLeft > 0);
      if (callerIsContribIface()) {
        Intent i = new Intent();
        i.putExtra(KEY_FEATURE, featureStringFromExtra);
        i.putExtra(KEY_TAG, getIntent().getSerializableExtra(KEY_TAG));
        if (shouldCallFeature) {
          setResult(RESULT_OK, i);
        } else {
          setResult(RESULT_CANCELED, i);
        }
      } else if (shouldCallFeature) {
        callFeature(feature);
      }
    }
    super.finish();
  }

  private void callFeature(ContribFeature feature) {
    switch (feature) {
      case SPLIT_TRANSACTION:
        startActivity(ShortcutHelper.createIntentForNewSplit(this));
        break;
      default:
        //should not happen
        AcraHelper.report(new IllegalStateException(
            String.format("Unhandlable request for feature %s (caller = %s)", feature,
                getCallingActivity() != null ? getCallingActivity().getClassName() : "null")));
    }
  }

  private boolean callerIsContribIface() {
    boolean result = false;
    ComponentName callingActivity = getCallingActivity();
    if (callingActivity != null) {
      try {
        Class<?> caller = Class.forName(callingActivity.getClassName());
        result = ContribIFace.class.isAssignableFrom(caller);
      } catch (ClassNotFoundException ignored) {
      }
    }
    return result;
  }

  @Override
  protected void onActivityResult(int arg0, int arg1, Intent arg2) {
    finish(false);
  }
}
