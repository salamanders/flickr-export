package flickrget;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

public class BackupUtils {

  private static final int BUF_SIZE = 1024 * 1024 * 1; // 1MB buffers

  /**
   * Downloads to a temporary location and renames on finish so no partials
   *
   * @param is
   * @param destinationPath
   * @return
   * @throws IOException
   */
  public static long copyStream(final InputStream is, final File destinationFile) throws IOException {

    final File tmpFile = File.createTempFile("flickr_download", null);
    try (final BufferedInputStream bis = new BufferedInputStream(is, BUF_SIZE);
        final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tmpFile), BUF_SIZE);) {
      final long total = ByteStreams.copy(bis, bos);
      tmpFile.renameTo(destinationFile);
      return total;
    }

  }

  /**
   * Simple "pick oldest non-null date"
   * 
   * @param ds
   * @return
   */
  public static Date oldest(final Date... ds) {
    Date result = null;
    for (final Date d : ds) {
      if (d == null) {
        continue;
      }
      if (result == null) {
        result = d;
        continue;
      }
      if (d.before(result)) {
        result = d;
        continue;
      }
    }
    return result;
  }

  public static String makeSafeFilename(final String input) {
    Preconditions.checkNotNull(input);
    return input.replaceAll("[^a-zA-Z0-9\\._]+", "_");
  }
}
