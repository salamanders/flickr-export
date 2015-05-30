package info.benjaminhill.flickrexport;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.photos.Extras;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.PhotosInterface;
import com.flickr4java.flickr.photos.Size;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.PhotosetsInterface;
import com.flickr4java.flickr.util.AuthStore;
import com.flickr4java.flickr.util.FileAuthStore;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.Scanner;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.scribe.model.Token;
import org.scribe.model.Verifier;

/**
 * Based on the "Backup.java" by Matthew MacKenzie v1.6 2009/01/01 TODO: Set dates on images missing date info
 * https://svn.apache.org/repos/asf/commons/proper/imaging/trunk/src/test/java/ org/apache/commons/imaging/examples/
 * WriteExifMetadataExample.java
 */
public class Backup {

  private static final ThreadLocal<DateFormat> YEAR_MONTH = new ThreadLocal<DateFormat>() {
    @Override
    protected DateFormat initialValue() {
      return new SimpleDateFormat("yyyy" + File.separatorChar + "MM" + File.separatorChar);
    }
  };

  /**
   * https://www.flickr.com/services/api/ https://www.flickr.com/services/api/flickr.photos.search.html
   * https://www.flickr.com/services/api/explore/?method=flickr.people.getInfo
   *
   * @param args
   * @throws Exception
   */
  public static void main(final String... args) throws Exception {
    final String apiKey = "385e87974a9b8ea369d02a50d7aba701";
    final String sharedSecret = "36e07d0c8c5c7144";

    if (args.length == 0) {
      // System.out.println("Look up your user ID at
      // https://www.flickr.com/services/api/explore/flickr.photos.search");
      System.out.println("Run with Java 8: java -jar flickr-export.jar YOUR_USERNAME_1 [OTHER_USERNAME_2...]");
    }

    for (final String userName : args) {
      final Backup bf = new Backup(apiKey, sharedSecret, userName);
      bf.doBackup();
    }
  }

  /**
   * Checks if the file has an EXIF date, if not, tries to set it.
   *
   * @param p
   * @param newFile
   * @throws IOException
   */
  private static void verifyExif(final Photo p, final File newFile) throws IOException {
    if (newFile.toString().toLowerCase().endsWith("jpg")) {
      try {
        LocalDateTime ldt = FixExif.hasDateTime(newFile);
        if (ldt == null) {
          final Date oldest = BackupUtils.oldest(p.getDateAdded(), p.getDatePosted(), p.getDateTaken());
          final LocalDateTime ldtReal = LocalDateTime.ofInstant(oldest.toInstant(), ZoneId.systemDefault());
          FixExif.setExifMetadataDate(newFile, ldtReal);
          System.out.println("# updated exif on " + newFile.toString());
        }
      } catch (final ClassCastException | ImageReadException | ImageWriteException e) {
        System.err.println("EXIF update error:" + e.toString() + " on " + newFile.toString());
      }
    }
  }

  private final Flickr flickr;

  private final String targetNsid, apiUsername;
  final File backupDir;

  public Backup(final String apiKey, final String sharedSecret, final String targetName)
          throws FlickrException, IOException {
    Preconditions.checkNotNull(apiKey);
    Preconditions.checkNotNull(sharedSecret);

    RequestContext.getRequestContext()
            .setExtras(Lists.newArrayList("date_upload", "date_taken", "original_format", "o_dims", "media", "url_o"));
    flickr = new Flickr(apiKey, sharedSecret, new REST());
    targetNsid = flickr.getPeopleInterface().findByUsername(targetName).getId();
    authorize();
    apiUsername = RequestContext.getRequestContext().getAuth().getUser().getUsername().toLowerCase();
    assert apiUsername != null;
    assert apiUsername.equalsIgnoreCase(targetName);

    backupDir = new File(System.getProperty("user.home") + File.separatorChar + "Pictures" + File.separatorChar
            + "flickr_" + BackupUtils.makeSafeFilename(apiUsername));
  }

  /**
   * Get and store an oAuth token
   *
   * @throws IOException
   * @throws FlickrException
   */
  private void authorize() throws IOException, FlickrException {
    Preconditions.checkNotNull(targetNsid);
    final AuthStore authStore = new FileAuthStore(
            new File(System.getProperty("user.home") + File.separatorChar + ".flickrAuth"));

    if (authStore.retrieve(targetNsid) == null) {
      final AuthInterface authInterface = flickr.getAuthInterface();
      final Token accessToken = authInterface.getRequestToken();

      final String url = authInterface.getAuthorizationUrl(accessToken, Permission.READ);
      System.out.println("Follow this URL to authorise yourself (" + targetNsid + ") on Flickr");
      System.out.println(url);
      System.out.println("Paste in the token it gives you:");
      System.out.print(">>");

      @SuppressWarnings("resource")
      final String tokenKey = new Scanner(System.in).nextLine();
      final Token requestToken = authInterface.getAccessToken(accessToken, new Verifier(tokenKey));

      final Auth auth = authInterface.checkToken(requestToken);
      authStore.store(auth);
      System.out.println("Thanks.  You probably will not have to do this every time.  Now starting backup.");
    }
    RequestContext.getRequestContext().setAuth(authStore.retrieve(targetNsid));
  }

  public void doBackup() throws Exception {
    Preconditions.checkNotNull(targetNsid);
    Preconditions.checkNotNull(backupDir);

    System.out.println("# Starting backup for " + targetNsid + " (" + apiUsername + ") to " + backupDir.toString());

    final SetMultimap<String, Photo> photosBySet = getPhotosBySet();
    System.out.println("# Total photos: " + photosBySet.size());

    photosBySet.entries().stream().parallel().forEach(ent -> {
      backupEntry(ent.getKey(), ent.getValue());
    });
  }

  private void backupEntry(final String setName, final Photo p) {
    try {
      final Date oldest = BackupUtils.oldest(p.getDateAdded(), p.getDatePosted(), p.getDateTaken());
      if (oldest == null) {
        System.err.println("Missing date for " + p.getUrl());
        return;
      }

      final File setDateDir = new File(backupDir, YEAR_MONTH.get().format(oldest) + BackupUtils.makeSafeFilename(setName));
      setDateDir.mkdirs();

      String result;
      switch (p.getMedia()) {
        case "video":
          result = downloadVideo(p, setDateDir);
          break;
        case "photo":
          result = downloadPhoto(p, setDateDir);
          break;
        default:
          throw new RuntimeException("FATAL Unknown Media:" + p.getMedia());
      }
      if (!result.contains("photo_exists") && !result.contains("video_id_exists")) {
        System.out.println(result);
      }
    } catch (final FlickrException | IOException | IllegalStateException e) {
      System.err.println(p.getUrl() + " caused " + e.getMessage());
    }
  }

  /**
   * Specific to photos only
   *
   * @param p
   * @param destDir
   * @return
   * @throws FlickrException
   * @throws FileNotFoundException
   * @throws IOException
   */
  private String downloadPhoto(final Photo p, final File destDir)
          throws FlickrException, FileNotFoundException, IOException {
    Preconditions.checkNotNull(p);
    Preconditions.checkNotNull(destDir);

    final String url = p.getOriginalUrl();
    final String filename = BackupUtils
            .makeSafeFilename(p.getTitle() + "_" + url.substring(url.lastIndexOf("/") + 1, url.length()));

    final File newFile = new File(destDir, filename);
    if (newFile.exists()) {
      verifyExif(p, newFile);
      return "# photo_exists:" + newFile.toString();
    }

    BackupUtils.copyStream(flickr.getPhotosInterface().getImageAsStream(p, Size.ORIGINAL), newFile);
    verifyExif(p, newFile);
    return "photo_dl:" + newFile.toString();
  }

  /**
   * Two ways to look for original URLs
   *
   * @param p
   * @return
   * @throws FlickrException
   */
  private String getVideoURL(final Photo p) throws FlickrException {
    Preconditions.checkNotNull(p);

    if (p.getOriginalSecret() != null && p.getOriginalSecret().length() > 3) {
      return String.format("https://www.flickr.com/photos/%s/%s/play/orig/%s/", apiUsername, p.getId(),
              p.getOriginalSecret());
    }
    for (final Size size : flickr.getPhotosInterface().getSizes(p.getId(), true)) {
      if (size.getSource().contains("/play/orig/")) {
        return size.getSource();
      }
    }
    for (final Size size : flickr.getPhotosInterface().getSizes(p.getId(), true)) {
      if (size.getSource().contains("/play/hd")) {
        return size.getSource();
      }
    }
    for (final Size size : flickr.getPhotosInterface().getSizes(p.getId(), true)) {
      if (size.getSource().contains("/play/site")) {
        return size.getSource();
      }
    }

    throw new IllegalStateException("Unable to locate any video URL for: " + p.getUrl());
  }

  /**
   * Extra work specific to videos
   *
   * @param p
   * @param destDir
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   * @throws FlickrException
   */
  private String downloadVideo(final Photo p, final File destDir) throws FileNotFoundException, IOException, FlickrException {
    // http://code.flickr.net/2009/03/02/videos-in-the-flickr-api-part-deux/
    // http://www.flickr.com/photos/{user-id|custom-url}/{photo-id}/play/{site|mobile|hd|orig}/{secret|originalsecret}/

    Preconditions.checkNotNull(p);
    Preconditions.checkNotNull(destDir);

    // TODO: Early check for duplicate file without network
    if (destDir.exists()) {
      for (final File already : destDir.listFiles()) {
        if (already.getName().contains(p.getId())) {
          return "# video_id_exists:" + p.getId() + " " + already.getName();
        }
      }
    }

    final String fileURL = getVideoURL(p);
    String fileName = BackupUtils.makeSafeFilename(p.getTitle());

    final URL url = new URL(fileURL);
    final HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
    final int responseCode = httpConn.getResponseCode();

    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new IllegalStateException(
              "No file to download. Server replied HTTP code: " + responseCode + " for " + fileURL);
    }

    final String disposition = httpConn.getHeaderField("Content-Disposition");
    final String contentType = httpConn.getContentType();

    if (disposition != null && disposition.contains("=")) {
      // extracts file name from header field
      final String fileNameFromDisp = BackupUtils.makeSafeFilename(disposition.split("=")[1].trim());
      fileName = fileName + "_" + fileNameFromDisp;
    } else {
      // extracts file name from URL
      fileName = fileName + "_"
              + BackupUtils.makeSafeFilename(fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length()));
    }

    if (!fileName.toLowerCase().endsWith(".mov") && !fileName.toLowerCase().endsWith(".mp4")
            && !fileName.toLowerCase().endsWith(".avi")) {
      fileName = fileName + "." + contentType.split("/")[1];
    }

    fileName = fileName.replaceAll("\\.+", "."); // Bizarre trailing dots
    fileName = fileName.replaceAll("_+", "_"); // Double underscores

    final File saveFilePath = new File(destDir, fileName);

    if (saveFilePath.exists()) {
      return "# video_exists:" + saveFilePath.toString();
    }

    // opens input stream from the HTTP connection
    BackupUtils.copyStream(httpConn.getInputStream(), saveFilePath);
    httpConn.disconnect();
    return "video_dl:" + saveFilePath.toString() + " " + saveFilePath.length();

  }

  private SetMultimap<String, Photo> getPhotosBySet()
          throws FlickrException {
    Preconditions.checkNotNull(targetNsid);

    final PhotosetsInterface pi = flickr.getPhotosetsInterface();
    final PhotosInterface photoInt = flickr.getPhotosInterface();

    final SetMultimap<String, Photo> photosBySet = HashMultimap.create();

    // Get sets slowly (serial) or they error
    for (final Photoset set : pi.getList(targetNsid).getPhotosets()) {
      int pageNumber = 1;
      while (true) {
        final PhotoList<Photo> photosTemp = pi.getPhotos(set.getId(), Extras.ALL_EXTRAS, Flickr.PRIVACY_LEVEL_NO_FILTER,
                500, pageNumber);
        photosBySet.putAll(set.getTitle(), photosTemp);
        System.out.println("# Set:" + set.getTitle() + " - " + pageNumber + " " + photosTemp.size());
        if (photosTemp.size() < 500) {
          break;
        }
        pageNumber++;
      }
    }

    // Non-set
    int pageNumber = 1;
    while (true) {
      System.out.println("# Set (non-set) - " + pageNumber);
      final Collection<Photo> nis = photoInt.getNotInSet(500, pageNumber);
      photosBySet.putAll("", nis);
      if (nis.size() < 500) {
        break;
      }
      pageNumber++;
    }

    return photosBySet;
  }

}
