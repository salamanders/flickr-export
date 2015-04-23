# flickr-export
Downloads all images and videos of your flickr account

## How to run
1. Download flickr-export.jar to anywhere, it will write to your Photos folder.
1. Run it from the command line: `java -jar flickr-export.jar YOUR_USERNAME [ OTHER_USERNAMES...]`
2. If it is your first time running you will have to authorize the app, follow the directions.

## Features

- [x] Downloads run in parallel - as many as your computer's cores can handle
- [x] Can be run over and over, will skip attempting previously downloaded files
- [x] Only saves successfully finished files - no partial downloads!
- [x] Picks the original photos/videos or highest resolution available
- [x] Handles both photos and videos, even videos with strange URLs
- [x] Restores timestamp EXIF data to jpg photos that had theirs stripped by flickr
- [x] Automatically saves organized into Pictures/flickr_yourid/YEAR/MONTH/SET/photo_name

