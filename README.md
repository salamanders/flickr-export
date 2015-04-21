# flickr-export
Downloads all images and videos of your flickr account

`java -jar flickr-export.jar YOUR_USERNAME_1 [OTHER_USERNAME_2...]`

1. Run this app: `java -jar flickr-export.jar YOUR_ID_1 [OTHER_ID_2...]`
2. If it is your first time running you will have to authorize the app


## Features

- [x] Can be run over and over, and will skip attempting previously downloaded files
- [x] Only saves successfully finished files, no partial downloads
- [x] Handles both photos and videos, even videos with strange URLs
- [ ] Restores timestamp EXIF data to photos that had theirs stripped by flickr

