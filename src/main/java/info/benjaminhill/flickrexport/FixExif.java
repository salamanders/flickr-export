package info.benjaminhill.flickrexport;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

public class FixExif {

  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

  /**
   * Various places Date Time can be hiding
   */
  private static final TagInfo[] DATE_TAGS = { TiffTagConstants.TIFF_TAG_DATE_TIME,
      ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED };

  public static LocalDateTime hasDateTime(final File jpegImageFile) throws ImageReadException, IOException {
    final ImageMetadata metadata = Imaging.getMetadata(jpegImageFile);
    final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
    if (null != jpegMetadata) {
      for (final TagInfo ti : DATE_TAGS) {
        final TiffField dateTimeField = jpegMetadata.findEXIFValueWithExactMatch(ti);
        if (null != dateTimeField) {
          return LocalDateTime.parse(dateTimeField.getStringValue(), formatter);
        }
      }
    }
    return null;
  }

  /**
   * This example illustrates how to add/update EXIF metadata in a JPEG file.
   * 
   * @param jpegImageFile
   *          A source image file.
   * @param dst
   *          The output file.
   * @throws IOException
   * @throws ImageReadException
   * @throws ImageWriteException
   */
  public static void setExifMetadataDate(final File jpegImageFile, final LocalDateTime ldt) throws ImageWriteException, ImageReadException, IOException {

      TiffOutputSet outputSet = null;

      final ImageMetadata metadata = Imaging.getMetadata(jpegImageFile);
      final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
      if (null != jpegMetadata) {
        final TiffImageMetadata exif = jpegMetadata.getExif();
        if (null != exif) {
          outputSet = exif.getOutputSet();
        }
      }
      if (outputSet == null) {
        outputSet = new TiffOutputSet();
      }
      final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();

      exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
      exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, ldt.format(formatter));
      
      final File tmpExifFile = File.createTempFile("flickr_exif", ".jpg");
      try (final OutputStream os = new BufferedOutputStream(new FileOutputStream(tmpExifFile));) {
        new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os, outputSet);
      }
      java.nio.file.Files.move(tmpExifFile.toPath(), jpegImageFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }
/*
  private static void printJpegTagValue(final JpegImageMetadata jpegMetadata, final TagInfo tagInfo) {
    final TiffField field = jpegMetadata.findEXIFValueWithExactMatch(tagInfo);
    if (field == null) {
      System.out.println(tagInfo.name + ": " + "Not Found.");
    } else {
      System.out.println(tagInfo.name + ": " + field.getValueDescription());
    }
  }
*/
}
