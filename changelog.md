# 1.18.1-0.0.0-beta.0.1
 - use srg names as backup for AT reflection backups

# 1.18.1-0.0.0-beta
 - First seperated release
 - Indirect drawing used if available
 - Quartz config registered earlier than mod initialization to allow reading at quartz init
 - deltanano flipped to positive
 - Resource reload no longer crashes
 - QuartzTile helper code added
 - Dont attempt resource load if init wasn't completed
 - Add reflection based AT backups,
   - AT badness happens with a mod fails to init
 - Draw batches are frustum culled
 - StaticMesh renamed to Mesh
   - Can be explicitly rebuilt


### See Phos changelog for earlier changes https://github.com/BiggerSeries/Phosphophyllite/blob/0.6.0-alpha.9.1/changelog.md