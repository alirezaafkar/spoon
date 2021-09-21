package com.squareup.spoon;

import static android.graphics.Bitmap.CompressFormat.PNG;
import static com.squareup.spoon.Chmod.chmodPlusR;
import static com.squareup.spoon.Chmod.chmodPlusRWX;
import static com.squareup.spoon.internal.Constants.NAME_SEPARATOR;
import static com.squareup.spoon.internal.Constants.SPOON_FILES;
import static com.squareup.spoon.internal.Constants.SPOON_SCREENSHOTS;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

/**
 * A test rule which captures screenshots and associates them with the test class and test method
 * for Spoon.
 *
 * <pre><code>
 * &#064;Rule public final SpoonRule spoon = new SpoonRule();
 * &#064;Rule public final ActivityTestRule&lt;MyActivity> activityRule = // ...
 *
 * &#064;Test public void methodUnderTest() {
 *   MyActivity activity = activityRule.getActivity();
 *   spoon.screenshot(activity, "start");
 *   // ...
 * }
 * </code></pre>
 */
public final class SpoonRule implements TestRule {
  private static final String EXTENSION = ".png";
  private static final String TAG = "Spoon";
  private static final Pattern TAG_VALIDATION = Pattern.compile("[a-zA-Z0-9_-]+");

  private String className;
  private String methodName;

  @Override public Statement apply(Statement base, Description description) {
    className = description.getClassName();
    methodName = description.getMethodName();
    return base; // Pass-through. We're just here to capture the description information.
  }

  public File screenshot(Activity activity, String tag) {
    if (!TAG_VALIDATION.matcher(tag).matches()) {
      throw new IllegalArgumentException("Tag must match " + TAG_VALIDATION.pattern() + ".");
    }
    File screenshotDirectory =
        obtainDirectory(activity.getApplicationContext(), className, methodName, SPOON_SCREENSHOTS);
    String screenshotName = System.currentTimeMillis() + NAME_SEPARATOR + tag + EXTENSION;
    File screenshotFile = new File(screenshotDirectory, screenshotName);
    Bitmap bitmap = Screenshot.capture(tag, activity);
    writeBitmapToFile(bitmap, screenshotFile);
    Log.d(TAG, "Captured screenshot '" + tag + "'.");
    return screenshotFile;
  }

  private static void writeBitmapToFile(Bitmap bitmap, File file) {
    OutputStream fos = null;
    try {
      fos = new BufferedOutputStream(new FileOutputStream(file));
      bitmap.compress(PNG, 100 /* quality */, fos);

      chmodPlusR(file);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Cannot write screenshot to " + file, e);
    } finally {
      bitmap.recycle();
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  public static File obtainDirectory(Context context, String testClassName,
    String testMethodName, String directoryName) {
    File directory = new File(context.getExternalFilesDir(null), "app_" + directoryName);
    File dirClass = new File(directory, testClassName);
    File dirMethod = new File(dirClass, testMethodName);
    createDir(dirMethod);
    return dirMethod;
  }


  public File save(final Context context, final File file) {
    if (!file.exists()) {
      throw new RuntimeException("Can't find any file at: " + file);
    }

    File filesDirectory = null;
    try {
      filesDirectory = obtainDirectory(context, className, methodName, SPOON_FILES);
      File target = new File(filesDirectory, file.getName());
      copyFile(file, target);
      Log.d(TAG, "Saved " + file);
      return target;
    } catch (IOException e) {
      throw new RuntimeException("Couldn't copy file " + file + " to " + filesDirectory, e);
    }
  }

  private static void copyFile(File source, File target) throws IOException {
    Log.d(TAG, "Will copy " + source + " to " + target);

    if (!target.createNewFile()) {
      throw new RuntimeException("Couldn't create file " + target);
    }
    chmodPlusR(target);

    BufferedInputStream is = null;
    BufferedOutputStream os = null;
    try {
      is = new BufferedInputStream(new FileInputStream(source));
      os = new BufferedOutputStream(new FileOutputStream(target));
      byte[] buffer = new byte[4096];
      while (true) {
        int read = is.read(buffer);
        if (read == -1) {
          break;
        }
        os.write(buffer, 0, read);
      }
    } finally {
      try {
        is.close();
      } catch (IOException ie) {}
      try {
        os.close();
      } catch (IOException ie) {}
    }
  }

  private static void createDir(File dir) {
    File parent = dir.getParentFile();
    if (!parent.exists()) {
      createDir(parent);
    }
    if (!dir.exists() && !dir.mkdirs()) {
      throw new RuntimeException("Unable to create output dir: " + dir.getAbsolutePath());
    }
    chmodPlusRWX(dir);
  }
}
