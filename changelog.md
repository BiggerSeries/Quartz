# 1.18.2-0.0.0-beta.1.4
 - backport fixes from 1.19
   - fix SSBO checking on some GPUs
   - check phos version before attempting to use any faetures of phos

# 1.18.2-0.0.0-beta.1.3
- fix gpu memory alignment waste calculation, again
  - im bad at math apparently, the new one had a different bug from what i was trying to fix last time 

# 1.18.2-0.0.0-beta.1.2
 - fix gpu memory alignment waste calculation
 - gracefully handle Phosphophyllyte not being installed

# 1.18.2-0.0.0-beta.1.1
 - fix shader compilation on nvidia cards

# 1.18.2-0.0.0-beta.1
 - 1.18.2
 - use SSBOs when available
 - correct check for dynamic matrix parents
 - allow existing custom size draw batchers to be used for fully contained smaller AABBs

# 1.18.1-0.0.0-beta.0.2
 - fix rendering when draw indirect is available but base instance is not (Mac)

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