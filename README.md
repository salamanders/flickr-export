# flickr-export
Downloads all images and videos of your flickr account


1. Look up your user ID at https://www.flickr.com/services/api/explore/flickr.photos.search
2. Then run this app: `java -jar flickr-export.jar YOUR_ID_1 [OTHER_ID_2...]`
3. If it is your first time running you will have to authorize the app
4. Run it a few times until it isn't downloading any new photos.

## Features

- [x] Can be run over and over, and will skip attempting previously downloaded files
- [x] Only saves successfully finished files, no partial downloads
- [x] Handles both photos and videos, even videos with strange URLs
- [ ] Restores timestamp EXIF data to photos that had theirs stripped by flickr

